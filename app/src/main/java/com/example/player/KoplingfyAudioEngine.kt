package com.example.player

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.data.remote.InnerTubeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class AudioEngineState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)

/**
 * Koplingfy Metrolist-Architecture Media3 ExoPlayer Audio Engine:
 * - Zero-teardown single ExoPlayer instance with aggressive fast-start DefaultLoadControl (600ms)
 * - 512 KB chunked HTTP Range streaming over pooled HTTP/2 OkHttp (bypasses YouTube CDN throttling)
 * - 128 MB LRU SimpleCache keyed by trackId+quality so replays & seeks are instant (0ms network)
 * - Native Opus (audio/webm), AAC (audio/mp4), MP3, FLAC, offline files, & SAF local URIs
 * - 5-Band Equalizer + BassBoost + LoudnessEnhancer (Audio Normalization)
 * - Playback Speed & Pitch, Crossfade, Sleep Fade-out, A-B Loop, AudioFocus & BecomingNoisy handling
 */
@OptIn(UnstableApi::class)
class KoplingfyAudioEngine(
    private val context: Context,
    private val onTrackCompleted: () -> Unit,
    private val onStreamExpiredError: (() -> Boolean)? = null
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var exoPlayer: ExoPlayer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var attachedAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var progressJob: Job? = null

    private var currentSpeed: Float = 1.0f
    private var currentPitch: Float = 1.0f
    private var currentBandsDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f)
    private var currentBassStrength: Float = 0f
    private var normalizeAudio: Boolean = true
    private var crossfadeSeconds: Int = 0
    private var sleepFadeMultiplier: Float = 1.0f
    private var abLoopStartMs: Long? = null
    private var abLoopEndMs: Long? = null
    private var pendingStartPositionMs: Long = 0L
    private var pendingAutoStart: Boolean = true

    private val _state = MutableStateFlow(AudioEngineState())
    val state: StateFlow<AudioEngineState> = _state.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0) {
                attachAudioEffects(audioSessionId)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = exoPlayer ?: return
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _state.value = _state.value.copy(
                        isBuffering = true,
                        errorMessage = null
                    )
                }
                Player.STATE_READY -> {
                    val sessionId = player.audioSessionId
                    if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId != 0 && sessionId != attachedAudioSessionId) {
                        attachAudioEffects(sessionId)
                    }
                    val totalDur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
                        ?: _state.value.durationMs
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val playingNow = player.isPlaying || player.playWhenReady
                    if (playingNow) {
                        startProgressPolling()
                    } else {
                        stopProgressPolling()
                    }
                    _state.value = AudioEngineState(
                        isPlaying = player.isPlaying,
                        isBuffering = false,
                        positionMs = pos,
                        durationMs = totalDur,
                        errorMessage = null
                    )
                }
                Player.STATE_ENDED -> {
                    stopProgressPolling()
                    val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
                        ?: _state.value.durationMs
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        isBuffering = false,
                        positionMs = dur,
                        durationMs = dur
                    )
                    onTrackCompleted()
                }
                Player.STATE_IDLE -> {
                    stopProgressPolling()
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val player = exoPlayer ?: return
            val pos = player.currentPosition.coerceAtLeast(0L)
            val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: _state.value.durationMs
            if (isPlaying) {
                startProgressPolling()
            } else {
                stopProgressPolling()
            }
            _state.value = _state.value.copy(
                isPlaying = isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                positionMs = pos,
                durationMs = dur
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            stopProgressPolling()
            val cause = error.cause
            val isExpiredHttp = (cause is HttpDataSource.InvalidResponseCodeException &&
                (cause.responseCode == 403 || cause.responseCode == 410 || cause.responseCode == 404)) ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS

            if (isExpiredHttp && onStreamExpiredError?.invoke() == true) {
                _state.value = _state.value.copy(
                    isPlaying = false,
                    isBuffering = true,
                    errorMessage = null
                )
                return
            }

            _state.value = AudioEngineState(
                isPlaying = false,
                isBuffering = false,
                positionMs = _state.value.positionMs,
                durationMs = _state.value.durationMs,
                errorMessage = "Gagal memutar stream audio. Coba putar ulang atau Login YouTube Music."
            )
        }
    }

    private fun getOrCreatePlayer(): ExoPlayer {
        exoPlayer?.let { return it }

        // Fast-start buffer profile tuned for music streaming (Metrolist-like snappy playback start)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 600,
                /* bufferForPlaybackAfterRebufferMs = */ 1_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        return ExoPlayer.Builder(appContext)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                addListener(playerListener)
                playbackParameters = PlaybackParameters(currentSpeed, currentPitch)
                exoPlayer = this
            }
    }

    fun playSource(
        sourceUrl: String?,
        isLocalUri: Boolean,
        userAgent: String? = null,
        cookie: String? = null,
        cacheKey: String? = null
    ) {
        if (sourceUrl.isNullOrBlank()) {
            stopProgressPolling()
            exoPlayer?.stop()
            _state.value = AudioEngineState(
                isPlaying = false,
                isBuffering = false,
                errorMessage = "Audio stream tidak tersedia untuk lagu ini."
            )
            return
        }

        val safeStartPos = pendingStartPositionMs.coerceAtLeast(0L)
        val shouldAutoStart = pendingAutoStart
        pendingStartPositionMs = 0L
        pendingAutoStart = true

        _state.value = AudioEngineState(
            isPlaying = false,
            isBuffering = true,
            positionMs = safeStartPos,
            durationMs = _state.value.durationMs,
            errorMessage = null
        )

        try {
            val player = getOrCreatePlayer()
            val uri = Uri.parse(sourceUrl)
            val isLocalSource = isLocalUri || uri.scheme == "file" || uri.scheme == "content"

            val mediaItemBuilder = MediaItem.Builder().setUri(uri)
            if (!isLocalSource && !cacheKey.isNullOrBlank()) {
                mediaItemBuilder.setCustomCacheKey(cacheKey)
            }
            val mediaItem = mediaItemBuilder.build()

            val dataSourceFactory = if (isLocalSource) {
                DefaultDataSource.Factory(appContext)
            } else {
                val headers = mutableMapOf<String, String>()
                if (!cookie.isNullOrBlank()) {
                    headers["Cookie"] = cookie
                }
                val okHttpFactory = OkHttpDataSource.Factory(InnerTubeClient.streamHttpClient).apply {
                    if (!userAgent.isNullOrBlank()) {
                        setUserAgent(userAgent)
                    }
                    if (headers.isNotEmpty()) {
                        setDefaultRequestProperties(headers)
                    }
                }
                val chunkedUpstream = YouTubeChunkedDataSource.Factory(
                    upstreamFactory = okHttpFactory,
                    chunkSize = YouTubeChunkedDataSource.DEFAULT_CHUNK_SIZE
                )
                val cache = getOrCreateStreamCache(appContext)
                if (cache != null) {
                    val cacheSinkFactory = CacheDataSink.Factory()
                        .setCache(cache)
                        .setFragmentSize(YouTubeChunkedDataSource.DEFAULT_CHUNK_SIZE)
                    CacheDataSource.Factory()
                        .setCache(cache)
                        .setUpstreamDataSourceFactory(chunkedUpstream)
                        .setCacheWriteDataSinkFactory(cacheSinkFactory)
                        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                } else {
                    chunkedUpstream
                }
            }

            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)

            val initVol = ((if (crossfadeSeconds > 0 && safeStartPos < 1_000L) 0.14f else 1.0f) * sleepFadeMultiplier)
                .coerceIn(0.05f, 1.0f)
            player.volume = initVol
            player.playbackParameters = PlaybackParameters(currentSpeed, currentPitch)
            player.setMediaSource(mediaSource, safeStartPos)
            player.playWhenReady = shouldAutoStart
            player.prepare()
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
        val player = exoPlayer ?: return
        if (player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)) {
            player.pause()
            stopProgressPolling()
            _state.value = _state.value.copy(isPlaying = false)
        } else {
            player.playbackParameters = PlaybackParameters(currentSpeed, currentPitch)
            player.play()
            _state.value = _state.value.copy(isPlaying = true, errorMessage = null)
            startProgressPolling()
        }
    }

    fun resume() {
        val player = exoPlayer ?: return
        if (!player.isPlaying) {
            player.playbackParameters = PlaybackParameters(currentSpeed, currentPitch)
            player.play()
            _state.value = _state.value.copy(isPlaying = true, errorMessage = null)
            startProgressPolling()
        }
    }

    fun pause() {
        val player = exoPlayer ?: return
        if (player.isPlaying || player.playWhenReady) {
            player.pause()
            stopProgressPolling()
            _state.value = _state.value.copy(isPlaying = false)
        }
    }

    fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        val maxDur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            ?: _state.value.durationMs.coerceAtLeast(1L)
        val safePos = positionMs.coerceIn(0L, maxDur)
        player.seekTo(safePos)
        _state.value = _state.value.copy(positionMs = safePos)
    }

    fun updatePlaybackParams(speed: Float, pitch: Float) {
        currentSpeed = speed.coerceIn(0.5f, 2.0f)
        currentPitch = pitch.coerceIn(0.75f, 1.5f)
        exoPlayer?.playbackParameters = PlaybackParameters(currentSpeed, currentPitch)
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
        if (crossfadeSeconds == 0) {
            exoPlayer?.volume = (1.0f * sleepFadeMultiplier).coerceIn(0.05f, 1.0f)
        }
    }

    fun setSleepFadeMultiplier(multiplier: Float) {
        sleepFadeMultiplier = multiplier.coerceIn(0.05f, 1.0f)
        exoPlayer?.volume = sleepFadeMultiplier
    }

    fun setAbLoop(startMs: Long?, endMs: Long?) {
        abLoopStartMs = startMs
        abLoopEndMs = endMs
    }

    fun clearStreamCache(): Long {
        val cache = sharedStreamCache ?: return 0L
        val before = cache.cacheSpace
        runCatching {
            cache.keys.toList().forEach { key ->
                cache.removeResource(key)
            }
        }
        return before
    }

    private fun attachAudioEffects(audioSessionId: Int) {
        if (audioSessionId == attachedAudioSessionId && equalizer != null) return
        releaseEffects()
        attachedAudioSessionId = audioSessionId
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

    private fun startProgressPolling() {
        stopProgressPolling()
        progressJob = scope.launch {
            while (isActive) {
                val player = exoPlayer
                if (player != null && player.isPlaying) {
                    var pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
                        ?: _state.value.durationMs
                    val loopA = abLoopStartMs
                    val loopB = abLoopEndMs
                    if (loopA != null && loopB != null && loopB > loopA + 500L && pos >= loopB) {
                        player.seekTo(loopA)
                        pos = loopA
                    }
                    applyCrossfadeVolume(player, pos, dur)
                    _state.value = _state.value.copy(
                        isPlaying = true,
                        isBuffering = false,
                        positionMs = pos,
                        durationMs = dur
                    )
                }
                delay(200L)
            }
        }
    }

    private fun applyCrossfadeVolume(player: ExoPlayer, positionMs: Long, durationMs: Long) {
        val maxVol = sleepFadeMultiplier
        if (crossfadeSeconds <= 0 || durationMs <= 12_000L) {
            if (player.volume != maxVol) player.volume = maxVol
            return
        }
        val fadeWindowMs = (crossfadeSeconds * 1_000L).coerceAtMost(durationMs / 3L)
        val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
        val factor = when {
            positionMs < fadeWindowMs -> (positionMs.toFloat() / fadeWindowMs.toFloat()).coerceIn(0.14f, 1.0f)
            remainingMs < fadeWindowMs -> (remainingMs.toFloat() / fadeWindowMs.toFloat()).coerceIn(0.08f, 1.0f)
            else -> 1.0f
        }
        player.volume = (maxVol * factor).coerceIn(0.04f, 1.0f)
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
        attachedAudioSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    fun release() {
        stopProgressPolling()
        releaseEffects()
        runCatching {
            exoPlayer?.removeListener(playerListener)
            exoPlayer?.release()
        }
        exoPlayer = null
        scope.cancel()
    }

    companion object {
        const val STREAM_CACHE_DIR_NAME = "koplingfy_exo_cache"
        private const val STREAM_CACHE_MAX_BYTES: Long = 128L * 1024L * 1024L

        @Volatile
        private var sharedStreamCache: SimpleCache? = null

        private fun getOrCreateStreamCache(context: Context): SimpleCache? {
            sharedStreamCache?.let { return it }
            return synchronized(this) {
                sharedStreamCache ?: runCatching {
                    val cacheDir = File(context.cacheDir, STREAM_CACHE_DIR_NAME).apply {
                        if (!exists()) mkdirs()
                    }
                    SimpleCache(
                        cacheDir,
                        LeastRecentlyUsedCacheEvictor(STREAM_CACHE_MAX_BYTES),
                        StandaloneDatabaseProvider(context)
                    ).also { sharedStreamCache = it }
                }.getOrNull()
            }
        }
    }
}
