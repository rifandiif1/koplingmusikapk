package com.example.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Google Play Policy compliant mediaPlayback Foreground Service using native Android MediaSession
 * + Notification.MediaStyle.
 *
 * Key background-stability guarantees:
 * 1. Observes KoplingfyMediaBridge.snapshot directly in-process via StateFlow (no repeated background
 *    startService() calls that fail on Android 12+).
 * 2. Keeps foreground status & a bounded CPU PARTIAL_WAKE_LOCK continuously active across track-end
 *    transitions (STATE_ENDED -> async stream resolution -> next track start) so auto-next never stalls.
 * 3. Uses a 20-second grace delay before detaching foreground on genuine pause and immediately re-posts
 *    the notification so the mini-player never vanishes from the notification shade.
 * 4. Diffs & throttles notification updates (especially live lyric lines) so Android's notification
 *    rate-limiter never mutes or drops the media notification.
 */
class KoplingfyPlaybackService : Service() {

    private lateinit var mediaSession: MediaSession
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var snapshotCollectJob: Job? = null
    private var artworkLoadJob: Job? = null
    private var detachForegroundJob: Job? = null
    private var lyricThrottleJob: Job? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var lastWakeLockRefreshElapsedMs: Long = 0L

    private var cachedArtUrl: String? = null
    private var cachedBitmap: Bitmap? = null
    private var isForegroundStarted: Boolean = false

    // Diffing caches to avoid redundant Binder / NotificationManager spam on 200ms progress ticks
    private var lastPlaybackStateCode: Int = PlaybackState.STATE_NONE
    private var lastPlaybackStateTrackId: String? = null
    private var lastPlaybackStatePosMs: Long = -1L
    private var lastPlaybackStateTimeElapsedMs: Long = 0L

    private var lastMetadataKey: String? = null
    private var lastNotificationKey: String? = null
    private var lastLyricNotificationElapsedMs: Long = 0L
    private var pendingThrottledSnapshot: MediaBridgeSnapshot? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initWakeLock()

        mediaSession = MediaSession(this, "KoplingfyMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    KoplingfyMediaBridge.dispatchPlay()
                }

                override fun onPause() {
                    KoplingfyMediaBridge.dispatchPause()
                }

                override fun onSkipToNext() {
                    KoplingfyMediaBridge.dispatchNext()
                }

                override fun onSkipToPrevious() {
                    KoplingfyMediaBridge.dispatchPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    KoplingfyMediaBridge.dispatchSeekTo(pos)
                }

                override fun onStop() {
                    KoplingfyMediaBridge.dispatchPause()
                    releaseWakeLock()
                    stopForegroundCompat(removeNotification = true)
                    stopSelf()
                }
            })
            isActive = true
        }

        KoplingfyMediaBridge.markServiceRunning(true)

        // Observe state updates directly in-process without needing startService() IPC from background
        snapshotCollectJob = serviceScope.launch {
            KoplingfyMediaBridge.snapshot.collect { snap ->
                handleSnapshotUpdate(snap, forcePromote = false)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAY_PAUSE -> KoplingfyMediaBridge.dispatchTogglePlayPause()
            ACTION_NEXT -> KoplingfyMediaBridge.dispatchNext()
            ACTION_PREV -> KoplingfyMediaBridge.dispatchPrevious()
            ACTION_STOP -> {
                KoplingfyMediaBridge.dispatchPause()
                releaseWakeLock()
                stopForegroundCompat(removeNotification = true)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val snap = KoplingfyMediaBridge.snapshot.value
        if (snap.track == null) {
            // Safeguard against ForegroundServiceDidNotStartInTimeException when started via startForegroundService
            if (!isForegroundStarted) {
                val placeholder = buildMediaNotification(
                    title = "Koplingfy",
                    subtitle = "Menyiapkan pemutar musik…",
                    subHeader = "Koplingfy Hi-Fi",
                    showPauseAction = false,
                    ongoing = false,
                    artwork = null
                )
                promoteToForeground(placeholder)
            }
            releaseWakeLock()
            stopForegroundCompat(removeNotification = true)
            stopSelf()
            return START_NOT_STICKY
        }

        // Ensure startForeground() is always satisfied promptly whenever the service is started via startForegroundService
        handleSnapshotUpdate(snap, forcePromote = !isForegroundStarted)
        return START_NOT_STICKY
    }

    private fun handleSnapshotUpdate(
        snap: MediaBridgeSnapshot,
        forcePromote: Boolean,
        forceVisualRefresh: Boolean = false
    ) {
        val track = snap.track ?: return

        if (track.artworkUrl != cachedArtUrl) {
            cachedArtUrl = track.artworkUrl
            cachedBitmap = null
            if (!track.artworkUrl.isNullOrBlank()) {
                loadArtworkAsync(track.artworkUrl)
            }
        }

        if (!mediaSession.isActive) {
            mediaSession.isActive = true
        }

        val keepForeground = snap.isPlaybackActive
        val pbStateCode = when {
            snap.isTransitioning || snap.isBuffering -> PlaybackState.STATE_BUFFERING
            snap.isPlaying -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }

        // 1. Update MediaSession PlaybackState only on state change, track change, or explicit seek/drift (>1.8s)
        val nowElapsed = SystemClock.elapsedRealtime()
        val expectedPosMs = if (lastPlaybackStateCode == PlaybackState.STATE_PLAYING && lastPlaybackStateTimeElapsedMs > 0L) {
            lastPlaybackStatePosMs + (nowElapsed - lastPlaybackStateTimeElapsedMs)
        } else {
            lastPlaybackStatePosMs
        }
        val positionJumped = lastPlaybackStatePosMs < 0L || abs(snap.positionMs - expectedPosMs) > 1_800L
        if (pbStateCode != lastPlaybackStateCode || track.id != lastPlaybackStateTrackId || positionJumped || forceVisualRefresh) {
            lastPlaybackStateCode = pbStateCode
            lastPlaybackStateTrackId = track.id
            lastPlaybackStatePosMs = snap.positionMs
            lastPlaybackStateTimeElapsedMs = nowElapsed

            val pbState = PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SEEK_TO or
                        PlaybackState.ACTION_STOP
                )
                .setState(
                    pbStateCode,
                    snap.positionMs,
                    if (snap.isPlaying) 1.0f else 0f,
                    nowElapsed
                )
                .build()
            mediaSession.setPlaybackState(pbState)
        }

        val liveLyric = snap.activeLyricLine?.takeIf { it.isNotBlank() && snap.isPlaying }
        val displaySubtitle = when {
            snap.isTransitioning && !snap.isPlaying -> "Memuat lagu berikutnya…"
            liveLyric != null -> "♪ $liveLyric"
            else -> track.artist
        }
        val displaySubHeader = if (liveLyric != null) track.artist else "Koplingfy Hi-Fi"
        val durationMs = snap.durationMs.coerceAtLeast(track.durationMs)
        val artIdentity = cachedBitmap?.hashCode() ?: 0

        // 2. Keep foreground & WakeLock continuous while playing, buffering, or transitioning between tracks
        if (keepForeground) {
            detachForegroundJob?.cancel()
            detachForegroundJob = null
            acquireOrRefreshWakeLock()
        } else if (isForegroundStarted && detachForegroundJob == null) {
            // Grace window: keep foreground briefly on pause so quick resume / audio-focus ducking
            // never drops FGS privileges or loses the mini-player notification.
            scheduleDelayedForegroundDetach()
        }

        // 3. Determine whether Metadata + Notification need refreshing (avoiding 200ms tick spam)
        val metaKey = "${track.id}|$displaySubtitle|$durationMs|$artIdentity"
        val notifKey = "${track.id}|$displaySubtitle|$displaySubHeader|$keepForeground|$artIdentity"
        val isCoreVisualChange = forcePromote ||
            forceVisualRefresh ||
            lastNotificationKey == null ||
            lastNotificationKey?.substringBefore("|") != track.id ||
            lastNotificationKey?.split("|")?.getOrNull(3) != keepForeground.toString() ||
            lastNotificationKey?.substringAfterLast("|") != artIdentity.toString()

        val notifChanged = notifKey != lastNotificationKey
        if (!isCoreVisualChange && !notifChanged) {
            return
        }

        // Throttle lyric-only notification updates (>= 900ms) so OEM / Android Notification Rate Limiter never drops it
        if (!isCoreVisualChange && liveLyric != null) {
            val sinceLastLyricMs = nowElapsed - lastLyricNotificationElapsedMs
            if (sinceLastLyricMs < LYRIC_NOTIF_MIN_INTERVAL_MS) {
                pendingThrottledSnapshot = snap
                if (lyricThrottleJob?.isActive != true) {
                    val waitMs = (LYRIC_NOTIF_MIN_INTERVAL_MS - sinceLastLyricMs).coerceAtLeast(150L)
                    lyricThrottleJob = serviceScope.launch {
                        delay(waitMs)
                        val latest = pendingThrottledSnapshot ?: return@launch
                        pendingThrottledSnapshot = null
                        handleSnapshotUpdate(latest, forcePromote = false, forceVisualRefresh = true)
                    }
                }
                return
            }
            lastLyricNotificationElapsedMs = nowElapsed
        } else {
            lyricThrottleJob?.cancel()
            pendingThrottledSnapshot = null
        }

        if (metaKey != lastMetadataKey || forceVisualRefresh) {
            lastMetadataKey = metaKey
            val metaBuilder = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, displaySubtitle)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album.ifBlank { track.artist })
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
            cachedBitmap?.let { metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
            mediaSession.setMetadata(metaBuilder.build())
        }

        lastNotificationKey = notifKey
        val notification = buildMediaNotification(
            title = track.title,
            subtitle = displaySubtitle,
            subHeader = displaySubHeader,
            showPauseAction = keepForeground,
            ongoing = keepForeground,
            artwork = cachedBitmap
        )

        if (forcePromote || (keepForeground && !isForegroundStarted)) {
            promoteToForeground(notification)
        } else {
            val nm = getSystemService(NotificationManager::class.java)
            runCatching { nm?.notify(NOTIFICATION_ID, notification) }
        }
    }

    private fun scheduleDelayedForegroundDetach() {
        detachForegroundJob?.cancel()
        detachForegroundJob = serviceScope.launch {
            delay(PAUSE_DETACH_GRACE_MS)
            val snap = KoplingfyMediaBridge.snapshot.value
            if (snap.isPlaybackActive) return@launch

            releaseWakeLock()
            if (isForegroundStarted) {
                runCatching {
                    stopForeground(STOP_FOREGROUND_DETACH)
                    isForegroundStarted = false
                }
            }
            // Immediately re-post the paused notification so the mini-player remains visible in the shade
            val track = snap.track ?: return@launch
            val pausedNotif = buildMediaNotification(
                title = track.title,
                subtitle = track.artist,
                subHeader = "Koplingfy Hi-Fi",
                showPauseAction = false,
                ongoing = false,
                artwork = cachedBitmap
            )
            runCatching {
                getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, pausedNotif)
            }
        }
    }

    private fun promoteToForeground(notification: Notification) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForegroundStarted = true
        }.onFailure {
            // Fallback: still post notification via NotificationManager if foreground promotion was rejected
            runCatching {
                getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun initWakeLock() {
        runCatching {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Koplingfy:PlaybackWakeLock")?.apply {
                setReferenceCounted(false)
            }
        }
    }

    private fun acquireOrRefreshWakeLock() {
        val wl = wakeLock ?: return
        val now = SystemClock.elapsedRealtime()
        if (!wl.isHeld || now - lastWakeLockRefreshElapsedMs >= WAKE_LOCK_REFRESH_INTERVAL_MS) {
            lastWakeLockRefreshElapsedMs = now
            runCatching { wl.acquire(WAKE_LOCK_TIMEOUT_MS) }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        lastWakeLockRefreshElapsedMs = 0L
    }

    private fun loadArtworkAsync(url: String) {
        artworkLoadJob?.cancel()
        artworkLoadJob = serviceScope.launch(Dispatchers.IO) {
            val bmp = runCatching {
                val loader = ImageLoader(this@KoplingfyPlaybackService)
                val req = ImageRequest.Builder(this@KoplingfyPlaybackService)
                    .data(url)
                    .size(256, 256)
                    .allowHardware(false)
                    .build()
                ((loader.execute(req) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            }.getOrNull()
            if (bmp != null && cachedArtUrl == url) {
                cachedBitmap = bmp
                launch(Dispatchers.Main.immediate) {
                    handleSnapshotUpdate(
                        snap = KoplingfyMediaBridge.snapshot.value,
                        forcePromote = false,
                        forceVisualRefresh = true
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun buildMediaNotification(
        title: String,
        subtitle: String,
        subHeader: String,
        showPauseAction: Boolean,
        ongoing: Boolean,
        artwork: Bitmap?
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevAction = Notification.Action.Builder(
            android.R.drawable.ic_media_previous,
            "Sebelumnya",
            servicePendingIntent(ACTION_PREV, 101)
        ).build()

        val playPauseAction = Notification.Action.Builder(
            if (showPauseAction) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (showPauseAction) "Jeda" else "Putar",
            servicePendingIntent(ACTION_TOGGLE_PLAY_PAUSE, 102)
        ).build()

        val nextAction = Notification.Action.Builder(
            android.R.drawable.ic_media_next,
            "Berikutnya",
            servicePendingIntent(ACTION_NEXT, 103)
        ).build()

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSubText(subHeader)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(contentPendingIntent)
            .setOngoing(ongoing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        if (artwork != null) {
            builder.setLargeIcon(artwork)
        }
        return builder.build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, KoplingfyPlaybackService::class.java).apply {
            this.action = action
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        // Use getForegroundService on API 26+ so notification controls still work reliably
        // even after the 20s paused detach grace period on Android 12+.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && action != ACTION_STOP) {
            PendingIntent.getForegroundService(this, requestCode, intent, flags)
        } else {
            PendingIntent.getService(this, requestCode, intent, flags)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pemutar Musik Koplingfy",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Kontrol pemutaran musik latar belakang & layar kunci Koplingfy"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundCompat(removeNotification: Boolean) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(removeNotification)
            }
            isForegroundStarted = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        KoplingfyMediaBridge.markServiceRunning(false)
        detachForegroundJob?.cancel()
        lyricThrottleJob?.cancel()
        artworkLoadJob?.cancel()
        snapshotCollectJob?.cancel()
        releaseWakeLock()
        serviceScope.cancel()
        runCatching {
            mediaSession.isActive = false
            mediaSession.release()
        }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "koplingfy_media_playback"
        const val NOTIFICATION_ID = 7401
        const val ACTION_UPDATE_STATE = "com.example.action.UPDATE_STATE"
        const val ACTION_UPDATE_LYRIC = "com.example.action.UPDATE_LYRIC"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.example.action.TOGGLE_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.action.NEXT"
        const val ACTION_PREV = "com.example.action.PREV"
        const val ACTION_STOP = "com.example.action.STOP"

        private const val PAUSE_DETACH_GRACE_MS = 20_000L
        private const val LYRIC_NOTIF_MIN_INTERVAL_MS = 900L
        private const val WAKE_LOCK_TIMEOUT_MS = 10L * 60L * 1000L // 10 min bounded safety timeout
        private const val WAKE_LOCK_REFRESH_INTERVAL_MS = 4L * 60L * 1000L
    }
}
