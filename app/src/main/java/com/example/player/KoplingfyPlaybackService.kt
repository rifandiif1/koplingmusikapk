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
import kotlinx.coroutines.launch

/**
 * Google Play Policy compliant mediaPlayback Foreground Service using native Android MediaSession
 * + Notification.MediaStyle so Koplingfy plays smoothly in the background with Lock Screen,
 * Status Bar, and Bluetooth headset media controls.
 */
class KoplingfyPlaybackService : Service() {

    private lateinit var mediaSession: MediaSession
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var artworkLoadJob: Job? = null
    private var cachedArtUrl: String? = null
    private var cachedBitmap: Bitmap? = null
    private var isForegroundStarted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

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
                    stopForegroundCompat()
                    stopSelf()
                }
            })
            isActive = true
        }
        KoplingfyMediaBridge.markServiceRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAY_PAUSE -> KoplingfyMediaBridge.dispatchTogglePlayPause()
            ACTION_NEXT -> KoplingfyMediaBridge.dispatchNext()
            ACTION_PREV -> KoplingfyMediaBridge.dispatchPrevious()
            ACTION_STOP -> {
                KoplingfyMediaBridge.dispatchPause()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        refreshMediaSessionAndNotification()
        return START_NOT_STICKY
    }

    private fun refreshMediaSessionAndNotification() {
        val snap = KoplingfyMediaBridge.snapshot.value
        val track = snap.track ?: return

        if (track.artworkUrl != cachedArtUrl) {
            cachedArtUrl = track.artworkUrl
            cachedBitmap = null
            if (!track.artworkUrl.isNullOrBlank()) {
                loadArtworkAsync(track.artworkUrl)
            }
        }

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
                when {
                    snap.isBuffering -> PlaybackState.STATE_BUFFERING
                    snap.isPlaying -> PlaybackState.STATE_PLAYING
                    else -> PlaybackState.STATE_PAUSED
                },
                snap.positionMs,
                if (snap.isPlaying) 1.0f else 0f
            )
            .build()
        mediaSession.setPlaybackState(pbState)

        val metaBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, snap.durationMs.coerceAtLeast(track.durationMs))
        cachedBitmap?.let { metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
        mediaSession.setMetadata(metaBuilder.build())

        val keepForeground = snap.isPlaying || snap.isBuffering
        val notification = buildMediaNotification(track.title, track.artist, keepForeground, cachedBitmap)
        if (keepForeground) {
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
            }
        } else {
            val nm = getSystemService(NotificationManager::class.java)
            if (isForegroundStarted) {
                runCatching {
                    stopForeground(STOP_FOREGROUND_DETACH)
                    isForegroundStarted = false
                }
            }
            runCatching { nm.notify(NOTIFICATION_ID, notification) }
        }
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
                launch(Dispatchers.Main) { refreshMediaSessionAndNotification() }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun buildMediaNotification(
        title: String,
        artist: String,
        isPlaying: Boolean,
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
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Jeda" else "Putar",
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
            .setContentText(artist)
            .setSubText("Koplingfy Hi-Fi")
            .setContentIntent(contentPendingIntent)
            .setOngoing(isPlaying)
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
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundCompat() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            isForegroundStarted = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        KoplingfyMediaBridge.markServiceRunning(false)
        artworkLoadJob?.cancel()
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
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.example.action.TOGGLE_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.action.NEXT"
        const val ACTION_PREV = "com.example.action.PREV"
        const val ACTION_STOP = "com.example.action.STOP"
    }
}
