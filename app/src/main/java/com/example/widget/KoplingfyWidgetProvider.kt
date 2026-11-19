package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.widget.RemoteViews
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.MainActivity
import com.example.R
import com.example.player.KoplingfyMediaBridge
import com.example.player.KoplingfyPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Koplingfy Home Screen Mini Player Widget.
 * Displays active track title, artist, album art, and working Prev / Play-Pause / Next controls.
 */
class KoplingfyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_PLAY_PAUSE -> KoplingfyMediaBridge.dispatchTogglePlayPause()
            ACTION_WIDGET_NEXT -> KoplingfyMediaBridge.dispatchNext()
            ACTION_WIDGET_PREV -> KoplingfyMediaBridge.dispatchPrevious()
        }
    }

    companion object {
        const val ACTION_WIDGET_PLAY_PAUSE = "com.example.widget.ACTION_PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.example.widget.ACTION_NEXT"
        const val ACTION_WIDGET_PREV = "com.example.widget.ACTION_PREV"

        fun requestWidgetUpdate(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val component = ComponentName(context, KoplingfyWidgetProvider::class.java)
                val ids = manager.getAppWidgetIds(component)
                ids.forEach { id -> updateWidget(context, manager, id) }
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val snap = KoplingfyMediaBridge.snapshot.value
            val track = snap.track
            val views = RemoteViews(context.packageName, R.layout.widget_koplingfy_player)

            views.setTextViewText(
                R.id.widget_track_title,
                track?.title ?: "Koplingfy Hi-Fi"
            )
            views.setTextViewText(
                R.id.widget_track_artist,
                track?.artist ?: "Ketuk untuk mulai memutar musik"
            )
            views.setImageViewResource(
                R.id.widget_btn_play_pause,
                if (snap.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )

            // Open app when tapping widget container or artwork
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPending = PendingIntent.getActivity(
                context,
                300,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPending)

            // Media buttons
            views.setOnClickPendingIntent(
                R.id.widget_btn_prev,
                serviceOrBroadcastPending(context, KoplingfyPlaybackService.ACTION_PREV, 301)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_play_pause,
                serviceOrBroadcastPending(context, KoplingfyPlaybackService.ACTION_TOGGLE_PLAY_PAUSE, 302)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_next,
                serviceOrBroadcastPending(context, KoplingfyPlaybackService.ACTION_NEXT, 303)
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)

            val artUrl = track?.artworkUrl
            if (!artUrl.isNullOrBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    val bmp = runCatching {
                        val loader = ImageLoader(context)
                        val req = ImageRequest.Builder(context)
                            .data(artUrl)
                            .size(160, 160)
                            .allowHardware(false)
                            .build()
                        ((loader.execute(req) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                    }.getOrNull()
                    if (bmp != null) {
                        views.setImageViewBitmap(R.id.widget_artwork, bmp)
                        runCatching { appWidgetManager.updateAppWidget(appWidgetId, views) }
                    }
                }
            }
        }

        private fun serviceOrBroadcastPending(
            context: Context,
            serviceAction: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, KoplingfyPlaybackService::class.java).apply {
                action = serviceAction
            }
            return PendingIntent.getService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
