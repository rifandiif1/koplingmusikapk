package com.example.player

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.net.Uri
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.data.remote.InnerTubeClient
import com.example.model.StreamTechStats
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
    val errorMessage: String? = null,
    val streamTechStats: StreamTechStats = StreamTechStats()
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
    private val onTrackCompleted: () -> Boolean,
    private val onStreamExpiredError: (() -> Boolean)? = null
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var exoPlayer: ExoPlayer? = null
    private val beatAudioProcessor = BeatDetectorAudioProcessor()
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var presetReverb: PresetReverb? = null
    private var attachedAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var progressJob: Job? = null

    private var currentSpeed: Float = 1.0f
    private var roomSyncSpeedMultiplier: Float = 1.0f
    private var currentPitch: Float = 1.0f
    private var currentBandsDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f)
    private var currentBassStrength: Float = 0f
    private var normalizeAudio: Boolean = true
    private var skipSilenceEnabled: Boolean = false
    private var reverbEnabled: Boolean = false
    private var crossfadeSeconds: Int = 0
    private var sleepFadeMultiplier: Float = 1.0f
    private var abLoopStartMs: Long? = null
    private var abLoopEndMs: Long? = null
    private var pendingStartPositionMs: Long = 0L
    private var pendingAutoStart: Boolean = true
    private var activeCacheKey: String? = null
    private var attachedTextureView: TextureView? = null
    private var isHandlingTrackCompletion: Boolean = false

    /** Invoked whenever ExoPlayer finishes buffering and reaches STATE_READY (used for zero-delay Listen Together sync). */
    var onPlaybackReady: ((positionMs: Long, isPlaying: Boolean) -> Unit)? = null

    @Volatile
    var lastKnownGoodPositionMs: Long = 0L
        private set

    private val _state = MutableStateFlow(AudioEngineState())
    val state: StateFlow<AudioEngineState> = _state.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0) {
                attachAudioEffects(audioSessionId)
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            exoPlayer?.let { player ->
                _state.value = _state.value.copy(streamTechStats = extractStreamTechStats(player))
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
                    if (pos > 0L) lastKnownGoodPositionMs = pos
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
                        errorMessage = null,
                        streamTechStats = extractStreamTechStats(player)
                    )
                    onPlaybackReady?.invoke(pos, playingNow)
                }
                Player.STATE_ENDED -> {
                    stopProgressPolling()
                    val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
                        ?: _state.value.durationMs
                    isHandlingTrackCompletion = true
                    // Keep isBuffering = true across the synchronous onTrackCompleted() handoff so
                    // ForegroundService & MediaBridge never see a false "stopped" gap between tracks!
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        isBuffering = true,
                        positionMs = dur,
                        durationMs = dur
                    )
                    val transitioningToNext = runCatching { onTrackCompleted() }.getOrDefault(false)
                    isHandlingTrackCompletion = false
                    if (!transitioningToNext) {
                        _state.value = _state.value.copy(
                            isPlaying = false,
                            isBuffering = false,
                            positionMs = dur,
                            durationMs = dur
                        )
                    }
                }
                Player.STATE_IDLE -> {
                    stopProgressPolling()
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val player = exoPlayer ?: return
            val pos = player.currentPosition.coerceAtLeast(0L)
            if (pos > 0L) lastKnownGoodPositionMs = pos
            val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: _state.value.durationMs
            if (isPlaying) {
                AudioBeatMonitor.setPlaybackActive(true)
                startProgressPolling()
            } else {
                AudioBeatMonitor.setPlaybackActive(false)
                stopProgressPolling()
            }
            // Do not clear isBuffering during track-end handoff (ExoPlayer emits isPlaying=false right
            // before/alongside STATE_ENDED).
            val keepBuffering = player.playbackState == Player.STATE_BUFFERING ||
                (!isPlaying && (isHandlingTrackCompletion || player.playbackState == Player.STATE_ENDED))
            _state.value = _state.value.copy(
                isPlaying = isPlaying,
                isBuffering = keepBuffering,
                positionMs = pos,
                durationMs = dur
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            stopProgressPolling()
            val playerPos = exoPlayer?.currentPosition?.takeIf { it > 0L }
            if (playerPos != null) {
                lastKnownGoodPositionMs = playerPos
            }
            // Evict potentially corrupt/partial cached stream before attempting recovery
            activeCacheKey?.let { key ->
                runCatching { sharedStreamCache?.removeResource(key) }
            }

            val cause = error.cause
            val isRecoverableStreamError = cause is HttpDataSource.HttpDataSourceException ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT ||
                error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ||
                error.errorCode == PlaybackException.ERROR_CODE_UNSPECIFIED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED

            if (isRecoverableStreamError && onStreamExpiredError?.invoke() == true) {
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
                positionMs = lastKnownGoodPositionMs.coerceAtLeast(_state.value.positionMs),
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

        val renderersFactory = object : DefaultRenderersFactory(appContext) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(beatAudioProcessor))
                    .build()
            }
        }

        return ExoPlayer.Builder(appContext, renderersFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                addListener(playerListener)
                playbackParameters = PlaybackParameters(currentSpeed, currentPitch)
                this.skipSilenceEnabled = this@KoplingfyAudioEngine.skipSilenceEnabled
                attachedTextureView?.let { setVideoTextureView(it) }
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
        lastKnownGoodPositionMs = safeStartPos
        AudioBeatMonitor.resetForNewTrack()

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
            activeCacheKey = if (!isLocalSource) cacheKey else null

            val mediaItemBuilder = MediaItem.Builder().setUri(uri)
            if (!isLocalSource && !cacheKey.isNullOrBlank()) {
                mediaItemBuilder.setCustomCacheKey(cacheKey)
            }
            val mediaItem = mediaItemBuilder.build()

            val dataSourceFactory = if (isLocalSource) {
                DefaultDataSource.Factory(appContext)
            } else {
                buildRemoteDataSourceFactory(userAgent = userAgent, cookie = cookie, useCache = true)
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

    /**
     * Plays a Metrolist-style inline Lyric/Music Video directly inside the same ExoPlayer instance
     * (either a muxed progressive MP4 or a MergingMediaSource combining adaptive H.264 video + audio).
     */
    fun playVideoSource(
        videoUrl: String?,
        audioUrl: String? = null,
        userAgent: String? = null,
        cookie: String? = null
    ) {
        if (videoUrl.isNullOrBlank()) {
            _state.value = _state.value.copy(
                isBuffering = false,
                errorMessage = "Stream video tidak tersedia untuk pilihan ini."
            )
            return
        }

        val safeStartPos = pendingStartPositionMs.coerceAtLeast(0L)
        val shouldAutoStart = pendingAutoStart
        pendingStartPositionMs = 0L
        pendingAutoStart = true
        activeCacheKey = null

        _state.value = AudioEngineState(
            isPlaying = false,
            isBuffering = true,
            positionMs = safeStartPos,
            durationMs = _state.value.durationMs,
            errorMessage = null
        )

        try {
            val player = getOrCreatePlayer()
            val remoteFactory = buildRemoteDataSourceFactory(userAgent = userAgent, cookie = cookie, useCache = false)
            val videoSource = ProgressiveMediaSource.Factory(remoteFactory)
                .createMediaSource(MediaItem.fromUri(Uri.parse(videoUrl)))

            val finalSource = if (!audioUrl.isNullOrBlank()) {
                val audioSource = ProgressiveMediaSource.Factory(remoteFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(audioUrl)))
                MergingMediaSource(videoSource, audioSource)
            } else {
                videoSource
            }

            attachedTextureView?.let { player.setVideoTextureView(it) }
            player.volume = sleepFadeMultiplier.coerceIn(0.05f, 1.0f)
            player.playbackParameters = PlaybackParameters(currentSpeed, currentPitch)
            player.setMediaSource(finalSource, safeStartPos)
            player.playWhenReady = shouldAutoStart
            player.prepare()
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isPlaying = false,
                isBuffering = false,
                errorMessage = "Tidak dapat memutar stream video."
            )
        }
    }

    fun attachVideoTextureView(textureView: TextureView?) {
        attachedTextureView = textureView
        exoPlayer?.setVideoTextureView(textureView)
    }

    fun clearVideoTextureView(textureView: TextureView?) {
        if (textureView == null || attachedTextureView == textureView) {
            attachedTextureView = null
        }
        exoPlayer?.clearVideoTextureView(textureView)
    }

    private fun buildRemoteDataSourceFactory(
        userAgent: String?,
        cookie: String?,
        useCache: Boolean
    ): androidx.media3.datasource.DataSource.Factory {
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
        if (!useCache) return chunkedUpstream
        val cache = getOrCreateStreamCache(appContext)
        return if (cache != null) {
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
            player.playbackParameters = PlaybackParameters((currentSpeed * roomSyncSpeedMultiplier).coerceIn(0.5f, 2.0f), currentPitch)
            player.play()
            _state.value = _state.value.copy(isPlaying = true, errorMessage = null)
            startProgressPolling()
        }
    }

    fun resume() {
        val player = exoPlayer ?: return
        if (!player.isPlaying) {
            player.playbackParameters = PlaybackParameters((currentSpeed * roomSyncSpeedMultiplier).coerceIn(0.5f, 2.0f), currentPitch)
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
        exoPlayer?.playbackParameters = PlaybackParameters(
            (currentSpeed * roomSyncSpeedMultiplier).coerceIn(0.5f, 2.0f),
            currentPitch
        )
    }

    /**
     * Temporarily nudges playback speed by ±4.5% during Listen Together sessions to smoothly
     * erase tiny 180-550ms phase drifts without audible seek glitches.
     */
    fun setRoomSyncSpeedMultiplier(multiplier: Float) {
        val clamped = multiplier.coerceIn(0.92f, 1.08f)
        if (kotlin.math.abs(roomSyncSpeedMultiplier - clamped) < 0.002f) return
        roomSyncSpeedMultiplier = clamped
        exoPlayer?.playbackParameters = PlaybackParameters(
            (currentSpeed * roomSyncSpeedMultiplier).coerceIn(0.5f, 2.0f),
            currentPitch
        )
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

    fun updateSkipSilenceAndReverb(skipSilence: Boolean, reverb: Boolean) {
        skipSilenceEnabled = skipSilence
        reverbEnabled = reverb
        exoPlayer?.skipSilenceEnabled = skipSilence
        applyReverb()
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
        runCatching {
            presetReverb = PresetReverb(0, audioSessionId).apply {
                preset = PresetReverb.PRESET_LARGEHALL
                enabled = reverbEnabled
            }
        }
        applyBassAndLoudness()
        applyReverb()
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

    private fun applyReverb() {
        runCatching {
            presetReverb?.let { pr ->
                pr.preset = PresetReverb.PRESET_LARGEHALL
                pr.enabled = reverbEnabled
            }
        }
    }

    private fun extractStreamTechStats(player: ExoPlayer): StreamTechStats {
        val af = player.audioFormat
        val vf = player.videoFormat
        val rawMime = af?.sampleMimeType ?: af?.containerMimeType ?: ""
        val codecLabel = when {
            rawMime.contains("opus", ignoreCase = true) -> "Opus (WebM)"
            rawMime.contains("mp4a", ignoreCase = true) || rawMime.contains("aac", ignoreCase = true) -> "AAC-LC (MP4)"
            rawMime.contains("flac", ignoreCase = true) -> "FLAC Lossless"
            rawMime.contains("mpeg", ignoreCase = true) -> "MP3"
            af?.codecs?.isNotBlank() == true -> af.codecs.orEmpty()
            rawMime.isNotBlank() -> rawMime.substringAfter("/").uppercase()
            else -> "-"
        }
        val bitrateKbps = af?.bitrate?.takeIf { it > 0 }?.div(1000) ?: 0
        val sampleRateHz = af?.sampleRate?.takeIf { it > 0 } ?: 0
        val channelCount = af?.channelCount?.takeIf { it > 0 } ?: 0
        val videoRes = if (vf != null && vf.width > 0 && vf.height > 0) "${vf.width}×${vf.height}" else ""
        val videoCodec = vf?.sampleMimeType?.substringAfter("/")?.uppercase().orEmpty()
        val bufferedPct = player.bufferedPercentage.coerceIn(0, 100)
        val cachedNow = activeCacheKey?.let { key ->
            runCatching { sharedStreamCache?.getCachedBytes(key, 0L, 1024L) ?: 0L }.getOrDefault(0L) > 0L
        } ?: false
        return StreamTechStats(
            audioCodec = codecLabel,
            bitrateKbps = bitrateKbps,
            sampleRateHz = sampleRateHz,
            channelCount = channelCount,
            videoResolution = videoRes,
            videoCodec = videoCodec,
            bufferedPercent = bufferedPct,
            isCached = cachedNow,
            audioSessionId = player.audioSessionId
        )
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
                    if (pos > 0L) lastKnownGoodPositionMs = pos
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
        runCatching { presetReverb?.release() }
        equalizer = null
        bassBoost = null
        loudnessEnhancer = null
        presetReverb = null
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
