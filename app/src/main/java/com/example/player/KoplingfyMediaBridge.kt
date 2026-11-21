package com.example.player

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.model.Track
import com.example.widget.KoplingfyWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaBridgeSnapshot(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isTransitioning: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val activeLyricLine: String? = null
) {
    val isPlaybackActive: Boolean
        get() = isPlaying || isBuffering || isTransitioning
}

/**
 * Process-wide bridge connecting KoplingfyViewModel, KoplingfyPlaybackService (MediaSession + Notification),
 * and KoplingfyWidgetProvider (Home Screen Widget).
 *
 * Once KoplingfyPlaybackService is running, all state/track/lyric updates flow in-process via [snapshot]
 * StateFlow without repeatedly calling startService() from the background (preventing Android 12+
 * BackgroundServiceStartNotAllowedException / ForegroundServiceStartNotAllowedException).
 */
object KoplingfyMediaBridge {
    private val _snapshot = MutableStateFlow(MediaBridgeSnapshot())
    val snapshot: StateFlow<MediaBridgeSnapshot> = _snapshot.asStateFlow()

    @Volatile var onTogglePlayPause: (() -> Unit)? = null
    @Volatile var onPlay: (() -> Unit)? = null
    @Volatile var onPause: (() -> Unit)? = null
    @Volatile var onNext: (() -> Unit)? = null
    @Volatile var onPrevious: (() -> Unit)? = null
    @Volatile var onSeekTo: ((Long) -> Unit)? = null

    @Volatile private var serviceRunning: Boolean = false
    @Volatile private var lastStartRequestElapsedMs: Long = 0L

    private var lastWidgetTrackId: String? = null
    private var lastWidgetPlaying: Boolean? = null
    private var lastWidgetActive: Boolean? = null

    fun markServiceRunning(running: Boolean) {
        serviceRunning = running
        if (!running) {
            lastStartRequestElapsedMs = 0L
        }
    }

    fun publishState(
        context: Context,
        track: Track?,
        isPlaying: Boolean,
        isBuffering: Boolean,
        positionMs: Long,
        durationMs: Long,
        activeLyricLine: String? = null,
        isTransitioning: Boolean = false
    ) {
        val cleanedLyric = activeLyricLine?.trim()?.takeIf { it.isNotEmpty() }
        val nextSnapshot = MediaBridgeSnapshot(
            track = track,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            isTransitioning = isTransitioning,
            positionMs = positionMs,
            durationMs = durationMs,
            activeLyricLine = cleanedLyric
        )
        _snapshot.value = nextSnapshot

        val isPlaybackActive = nextSnapshot.isPlaybackActive

        // Only launch the foreground service if it isn't already running.
        // Once started, KoplingfyPlaybackService observes [snapshot] directly in-process.
        if (track != null && isPlaybackActive && !serviceRunning) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastStartRequestElapsedMs >= 1_500L) {
                lastStartRequestElapsedMs = now
                runCatching {
                    val intent = Intent(context.applicationContext, KoplingfyPlaybackService::class.java).apply {
                        action = KoplingfyPlaybackService.ACTION_UPDATE_STATE
                    }
                    ContextCompat.startForegroundService(context.applicationContext, intent)
                }
            }
        }

        // Refresh Home Screen widget only on meaningful track or play-state changes
        val widgetChanged = track != null && (
            track.id != lastWidgetTrackId ||
                isPlaying != lastWidgetPlaying ||
                isPlaybackActive != lastWidgetActive
            )
        if (widgetChanged) {
            lastWidgetTrackId = track?.id
            lastWidgetPlaying = isPlaying
            lastWidgetActive = isPlaybackActive
            KoplingfyWidgetProvider.requestWidgetUpdate(context.applicationContext)
        }
    }

    fun dispatchTogglePlayPause() {
        onTogglePlayPause?.invoke()
    }

    fun dispatchPlay() {
        onPlay?.invoke() ?: onTogglePlayPause?.invoke()
    }

    fun dispatchPause() {
        onPause?.invoke() ?: onTogglePlayPause?.invoke()
    }

    fun dispatchNext() {
        onNext?.invoke()
    }

    fun dispatchPrevious() {
        onPrevious?.invoke()
    }

    fun dispatchSeekTo(posMs: Long) {
        onSeekTo?.invoke(posMs)
    }
}
