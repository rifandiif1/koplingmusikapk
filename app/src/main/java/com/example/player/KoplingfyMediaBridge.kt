package com.example.player

import android.content.Context
import android.content.Intent
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
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

/**
 * Process-wide bridge connecting KoplingfyViewModel, KoplingfyPlaybackService (MediaSession + Notification),
 * and KoplingfyWidgetProvider (Home Screen Widget).
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

    private var lastServiceTrackId: String? = null
    private var lastServiceActive: Boolean? = null
    private var serviceRunning: Boolean = false

    fun markServiceRunning(running: Boolean) {
        serviceRunning = running
    }

    fun publishState(
        context: Context,
        track: Track?,
        isPlaying: Boolean,
        isBuffering: Boolean,
        positionMs: Long,
        durationMs: Long
    ) {
        _snapshot.value = MediaBridgeSnapshot(
            track = track,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            positionMs = positionMs,
            durationMs = durationMs
        )

        // Keep foreground service active during BOTH playback and track-transition buffering
        val isPlaybackActive = isPlaying || isBuffering
        val shouldSyncService = track != null && (
            track.id != lastServiceTrackId ||
                isPlaybackActive != lastServiceActive
            )

        if (shouldSyncService) {
            lastServiceTrackId = track?.id
            lastServiceActive = isPlaybackActive
            runCatching {
                val intent = Intent(context.applicationContext, KoplingfyPlaybackService::class.java).apply {
                    action = KoplingfyPlaybackService.ACTION_UPDATE_STATE
                }
                if (isPlaybackActive) {
                    if (!serviceRunning) {
                        ContextCompat.startForegroundService(context.applicationContext, intent)
                    } else {
                        context.applicationContext.startService(intent)
                    }
                } else if (serviceRunning) {
                    context.applicationContext.startService(intent)
                }
            }
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
