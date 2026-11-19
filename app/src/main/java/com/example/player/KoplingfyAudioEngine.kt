package com.example.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.Equalizer
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

class KoplingfyAudioEngine(
    private val context: Context,
    private val onTrackCompleted: () -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaPlayer: MediaPlayer? = null
    private var equalizer: Equalizer? = null
    private var progressJob: Job? = null

    private var currentSpeed: Float = 1.0f
    private var currentPitch: Float = 1.0f
    private var currentBandsDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f)

    private val _state = MutableStateFlow(AudioEngineState())
    val state: StateFlow<AudioEngineState> = _state.asStateFlow()

    fun playSource(sourceUrl: String?, isLocalUri: Boolean) {
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
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                if (isLocalUri) {
                    setDataSource(context, Uri.parse(sourceUrl))
                } else {
                    setDataSource(sourceUrl)
                }
                setOnPreparedListener { player ->
                    attachEqualizer(player.audioSessionId)
                    applyPlaybackParams(player)
                    player.start()
                    _state.value = AudioEngineState(
                        isPlaying = true,
                        isBuffering = false,
                        positionMs = 0L,
                        durationMs = player.duration.toLong().coerceAtLeast(1L),
                        errorMessage = null
                    )
                    startProgressPolling()
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
                        errorMessage = "Gagal memutar audio. Periksa koneksi internet."
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

    fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
                stopProgressPolling()
                _state.value = _state.value.copy(isPlaying = false)
            } else {
                applyPlaybackParams(mp)
                mp.start()
                _state.value = _state.value.copy(isPlaying = true, errorMessage = null)
                startProgressPolling()
            }
        } catch (_: IllegalStateException) {
            // Ignored when player is still preparing
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
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
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

    private fun attachEqualizer(audioSessionId: Int) {
        releaseEqualizer()
        try {
            val eq = Equalizer(0, audioSessionId)
            eq.enabled = true
            equalizer = eq
            applyEqualizerBands()
        } catch (_: Exception) {
            equalizer = null
        }
    }

    private fun applyEqualizerBands() {
        val eq = equalizer ?: return
        try {
            val numBands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val minLevel = range[0].toInt()
            val maxLevel = range[1].toInt()
            for (i in 0 until minOf(numBands, currentBandsDb.size)) {
                val millibels = (currentBandsDb[i] * 100f).toInt().coerceIn(minLevel, maxLevel)
                eq.setBandLevel(i.toShort(), millibels.toShort())
            }
        } catch (_: Exception) {
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
                            _state.value = _state.value.copy(
                                isPlaying = true,
                                positionMs = mp.currentPosition.toLong(),
                                durationMs = mp.duration.toLong().coerceAtLeast(_state.value.durationMs)
                            )
                        }
                    } catch (_: IllegalStateException) {
                    }
                }
                delay(250L)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun releaseEqualizer() {
        try {
            equalizer?.release()
        } catch (_: Exception) {
        }
        equalizer = null
    }

    private fun releasePlayerOnly() {
        stopProgressPolling()
        releaseEqualizer()
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    fun release() {
        releasePlayerOnly()
    }
}
