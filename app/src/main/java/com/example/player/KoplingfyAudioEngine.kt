package com.example.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AudioEngineState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)

/**
 * Koplingfy DSP Audio Engine:
 * - Full-length stream, offline file, & local SAF URI playback
 * - 5-Band Equalizer + BassBoost + LoudnessEnhancer (Audio Normalization)
 * - Playback Speed & Pitch control
 * - Android AudioFocus management + automatic pause on headset disconnect (ACTION_AUDIO_BECOMING_NOISY)
 */
class KoplingfyAudioEngine(
    private val context: Context,
    private val onTrackCompleted: () -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var progressJob: Job? = null
    private var focusRequest: AudioFocusRequest? = null
    private var noisyReceiverRegistered = false

    private var currentSpeed: Float = 1.0f
    private var currentPitch: Float = 1.0f
    private var currentBandsDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f)
    private var currentBassStrength: Float = 0f
    private var normalizeAudio: Boolean = true
    private var crossfadeSeconds: Int = 0
    private var sleepFadeMultiplier: Float = 1.0f
    private var abLoopStartMs: Long? = null
    private var abLoopEndMs: Long? = null
    private var isDucked: Boolean = false
    private var pendingStartPositionMs: Long = 0L
    private var pendingAutoStart: Boolean = true

    private val _state = MutableStateFlow(AudioEngineState())
    val state: StateFlow<AudioEngineState> = _state.asStateFlow()

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pause()
            }
        }
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                isDucked = true
                runCatching { mediaPlayer?.setVolume(0.3f, 0.3f) }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                isDucked = false
                runCatching { mediaPlayer?.setVolume(1.0f, 1.0f) }
            }
        }
    }

    fun playSource(
        sourceUrl: String?,
        isLocalUri: Boolean,
        userAgent: String? = null,
        cookie: String? = null
    ) {
        releasePlayerOnly()
        if (sourceUrl.isNullOrBlank()) {
            _state.value = AudioEngineState(
                isPlaying = false,
                isBuffering = false,
                errorMessage = "Audio stream tidak tersedia untuk lagu ini."
            )
            return
        }

        _state.value = AudioEngineState(isPlaying = false, isBuffering = true, errorMessage = null)

        try {
            requestAudioFocus()
            registerNoisyReceiver()

            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                val uri = Uri.parse(sourceUrl)
                if (isLocalUri || uri.scheme == "file" || uri.scheme == "content") {
                    setDataSource(context, uri)
                } else {
                    val headers = mutableMapOf<String, String>()
                    if (!userAgent.isNullOrBlank()) headers["User-Agent"] = userAgent
                    if (!cookie.isNullOrBlank()) headers["Cookie"] = cookie
                    if (headers.isNotEmpty()) {
                        setDataSource(context, uri, headers)
                    } else {
                        setDataSource(sourceUrl)
                    }
                }
                setOnPreparedListener { player ->
                    attachAudioEffects(player.audioSessionId)
                    applyPlaybackParams(player)
                    val totalDur = player.duration.toLong().coerceAtLeast(1L)
                    val safeStartPos = pendingStartPositionMs.coerceIn(0L, totalDur)
                    if (safeStartPos > 1_000L) {
                        runCatching { player.seekTo(safeStartPos.toInt()) }
                    }
                    val shouldStart = pendingAutoStart
                    pendingStartPositionMs = 0L
                    pendingAutoStart = true
                    if (shouldStart) {
                        val initVol = ((if (crossfadeSeconds > 0 && safeStartPos < 1_000L && !isDucked) 0.12f else if (isDucked) 0.3f else 1.0f) * sleepFadeMultiplier).coerceIn(0.05f, 1.0f)
                        runCatching { player.setVolume(initVol, initVol) }
                        player.start()
                        startProgressPolling()
                    }
                    _state.value = AudioEngineState(
                        isPlaying = shouldStart,
                        isBuffering = false,
                        positionMs = safeStartPos,
                        durationMs = totalDur,
                        errorMessage = null
                    )
                }
                setOnCompletionListener {
                    stopProgressPolling()
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        positionMs = _state.value.durationMs
                    )
                    onTrackCompleted()
                }
                setOnErrorListener { _, _, _ ->
                    stopProgressPolling()
                    _state.value = AudioEngineState(
                        isPlaying = false,
                        isBuffering = false,
                        errorMessage = "Gagal memutar stream audio. Coba putar ulang atau Login YouTube Music."
                    )
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            _state.value = AudioEngineState(
                isPlaying = false,
                isBuffering = false,
                errorMessage = "Tidak dapat membuka sumber audio."
            )
        }
    }

    fun setNextTrackStartOptions(startPositionMs: Long = 0L, autoStart: Boolean = true) {
        pendingStartPositionMs = startPositionMs.coerceAtLeast(0L)
        pendingAutoStart = autoStart
    }

    fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
                stopProgressPolling()
                _state.value = _state.value.copy(isPlaying = false)
            } else {
                requestAudioFocus()
                applyPlaybackParams(mp)
                mp.start()
                _state.value = _state.value.copy(isPlaying = true, errorMessage = null)
                startProgressPolling()
            }
        } catch (_: IllegalStateException) {
        }
    }

    fun resume() {
        val mp = mediaPlayer ?: return
        try {
            if (!mp.isPlaying) {
                requestAudioFocus()
                applyPlaybackParams(mp)
                mp.start()
                _state.value = _state.value.copy(isPlaying = true, errorMessage = null)
                startProgressPolling()
            }
        } catch (_: IllegalStateException) {
        }
    }

    fun pause() {
        val mp = mediaPlayer ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
                stopProgressPolling()
                _state.value = _state.value.copy(isPlaying = false)
            }
        } catch (_: IllegalStateException) {
        }
    }

    fun seekTo(positionMs: Long) {
        val mp = mediaPlayer ?: return
        try {
            val safePos = positionMs.coerceIn(0L, _state.value.durationMs.coerceAtLeast(1L))
            mp.seekTo(safePos.toInt())
            _state.value = _state.value.copy(positionMs = safePos)
        } catch (_: IllegalStateException) {
        }
    }

    fun updatePlaybackParams(speed: Float, pitch: Float) {
        currentSpeed = speed.coerceIn(0.5f, 2.0f)
        currentPitch = pitch.coerceIn(0.75f, 1.5f)
        mediaPlayer?.let { mp ->
            try {
                val wasPlaying = mp.isPlaying
                applyPlaybackParams(mp)
                if (!wasPlaying && mp.isPlaying) {
                    mp.pause()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun updateEqualizerBands(bandsDb: List<Float>) {
        if (bandsDb.size != 5) return
        currentBandsDb = bandsDb
        applyEqualizerBands()
    }

    fun updateBassBoostAndNormalization(bassStrength: Float, normalize: Boolean) {
        currentBassStrength = bassStrength.coerceIn(0f, 1f)
        normalizeAudio = normalize
        applyBassAndLoudness()
    }

    fun updateCrossfadeSeconds(seconds: Int) {
        crossfadeSeconds = seconds.coerceIn(0, 10)
        if (crossfadeSeconds == 0 && !isDucked) {
            val vol = (1.0f * sleepFadeMultiplier).coerceIn(0.05f, 1.0f)
            runCatching { mediaPlayer?.setVolume(vol, vol) }
        }
    }

    fun setSleepFadeMultiplier(multiplier: Float) {
        sleepFadeMultiplier = multiplier.coerceIn(0.05f, 1.0f)
        val mp = mediaPlayer ?: return
        runCatching {
            val maxVol = (if (isDucked) 0.3f else 1.0f) * sleepFadeMultiplier
            mp.setVolume(maxVol, maxVol)
        }
    }

    fun setAbLoop(startMs: Long?, endMs: Long?) {
        abLoopStartMs = startMs
        abLoopEndMs = endMs
    }

    private fun applyPlaybackParams(mp: MediaPlayer) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val params = PlaybackParams()
                    .setSpeed(currentSpeed)
                    .setPitch(currentPitch)
                mp.playbackParams = params
            } catch (_: Exception) {
            }
        }
    }

    private fun attachAudioEffects(audioSessionId: Int) {
        releaseEffects()
        runCatching {
            equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
            applyEqualizerBands()
        }
        runCatching {
            bassBoost = BassBoost(0, audioSessionId).apply { enabled = currentBassStrength > 0.02f }
        }
        runCatching {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply { enabled = normalizeAudio }
        }
        applyBassAndLoudness()
    }

    private fun applyEqualizerBands() {
        val eq = equalizer ?: return
        runCatching {
            val numBands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val minLevel = range[0].toInt()
            val maxLevel = range[1].toInt()
            for (i in 0 until minOf(numBands, currentBandsDb.size)) {
                val millibels = (currentBandsDb[i] * 100f).toInt().coerceIn(minLevel, maxLevel)
                eq.setBandLevel(i.toShort(), millibels.toShort())
            }
        }
    }

    private fun applyBassAndLoudness() {
        runCatching {
            bassBoost?.let { bb ->
                bb.enabled = currentBassStrength > 0.02f
                bb.setStrength((currentBassStrength * 1000f).toInt().coerceIn(0, 1000).toShort())
            }
        }
        runCatching {
            loudnessEnhancer?.let { le ->
                le.enabled = normalizeAudio
                le.setTargetGain(if (normalizeAudio) 320 else 0)
            }
        }
    }

    private fun requestAudioFocus() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build()
                focusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        }
    }

    private fun registerNoisyReceiver() {
        if (!noisyReceiverRegistered) {
            runCatching {
                context.registerReceiver(
                    becomingNoisyReceiver,
                    IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                )
                noisyReceiverRegistered = true
            }
        }
    }

    private fun startProgressPolling() {
        stopProgressPolling()
        progressJob = scope.launch {
            while (isActive) {
                val mp = mediaPlayer
                if (mp != null) {
                    try {
                        if (mp.isPlaying) {
                            var pos = mp.currentPosition.toLong()
                            val dur = mp.duration.toLong().coerceAtLeast(_state.value.durationMs)
                            val loopA = abLoopStartMs
                            val loopB = abLoopEndMs
                            if (loopA != null && loopB != null && loopB > loopA + 500L && pos >= loopB) {
                                runCatching { mp.seekTo(loopA.toInt()) }
                                pos = loopA
                            }
                            applyCrossfadeVolume(mp, pos, dur)
                            _state.value = _state.value.copy(
                                isPlaying = true,
                                positionMs = pos,
                                durationMs = dur
                            )
                        }
                    } catch (_: IllegalStateException) {
                    }
                }
                delay(200L)
            }
        }
    }

    private fun applyCrossfadeVolume(mp: MediaPlayer, positionMs: Long, durationMs: Long) {
        val maxVol = (if (isDucked) 0.3f else 1.0f) * sleepFadeMultiplier
        if (crossfadeSeconds <= 0 || durationMs <= 12_000L) {
            runCatching { mp.setVolume(maxVol, maxVol) }
            return
        }
        val fadeWindowMs = (crossfadeSeconds * 1_000L).coerceAtMost(durationMs / 3L)
        val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
        val factor = when {
            positionMs < fadeWindowMs -> (positionMs.toFloat() / fadeWindowMs.toFloat()).coerceIn(0.12f, 1.0f)
            remainingMs < fadeWindowMs -> (remainingMs.toFloat() / fadeWindowMs.toFloat()).coerceIn(0.08f, 1.0f)
            else -> 1.0f
        }
        val vol = (maxVol * factor).coerceIn(0.04f, 1.0f)
        runCatching { mp.setVolume(vol, vol) }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        bassBoost = null
        loudnessEnhancer = null
    }

    private fun releasePlayerOnly() {
        stopProgressPolling()
        releaseEffects()
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
    }

    fun release() {
        releasePlayerOnly()
        if (noisyReceiverRegistered) {
            runCatching { context.unregisterReceiver(becomingNoisyReceiver) }
            noisyReceiverRegistered = false
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            }
        }
    }
}
