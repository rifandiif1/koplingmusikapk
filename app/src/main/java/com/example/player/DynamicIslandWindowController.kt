package com.example.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.sin

/**
 * System-wide, fixed, NON-CLICKABLE Dynamic Island lyrics pill drawn via Android WindowManager
 * so live lyrics (and optional Indonesian translation) float gracefully right below the status bar
 * above ALL other apps (Home Screen, WhatsApp, Instagram, Browser, Games, etc.).
 *
 * Uses FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE so 100% of touch events pass straight through
 * without ever intercepting taps or blocking the screen.
 */
class DynamicIslandWindowController(private val context: Context) {

    private val windowManager: WindowManager? =
        context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    private var rootContainer: LinearLayout? = null
    private var artworkView: ImageView? = null
    private var primaryLyricView: TextView? = null
    private var translatedLyricView: TextView? = null
    private var visualizerView: MiniEqBarsView? = null
    private var pillBackground: GradientDrawable? = null

    private var isAttached: Boolean = false
    private var lastPrimaryText: String? = null
    private var lastTranslatedText: String? = null
    private var lastAccentColor: Int = 0
    private var lastBitmapRef: Bitmap? = null

    fun canDrawSystemOverlay(): Boolean {
        return runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)
    }

    fun update(snap: MediaBridgeSnapshot, artwork: Bitmap?) {
        val track = snap.track
        val shouldShow = canDrawSystemOverlay() &&
            snap.dynamicIslandEnabled &&
            !snap.suppressSystemDynamicIsland &&
            track != null &&
            snap.isPlaying &&
            !snap.isBuffering

        if (!shouldShow || track == null) {
            detach()
            return
        }

        val liveLyric = snap.activeLyricLine?.trim()?.takeIf { it.isNotEmpty() }
        val primaryText = liveLyric ?: "♪ ${track.title} • ${track.artist}"
        val translatedText = if (liveLyric != null) {
            snap.activeTranslatedLyricLine
                ?.trim()
                ?.takeIf { it.isNotEmpty() && !it.equals(liveLyric, ignoreCase = true) }
        } else null

        ensureAttached()
        if (!isAttached) return

        // Update luminous pill border accent color when changed
        if (snap.accentColorArgb != lastAccentColor) {
            lastAccentColor = snap.accentColorArgb
            val borderColor = blendWithWhite(snap.accentColorArgb, 0.34f, alpha = 0x72)
            pillBackground?.setStroke(dp(1f), borderColor)
            visualizerView?.setBarColor(blendWithWhite(snap.accentColorArgb, 0.38f, alpha = 0xFF))
        }

        // Update circular album art
        if (artwork !== lastBitmapRef) {
            lastBitmapRef = artwork
            if (artwork != null && !artwork.isRecycled) {
                artworkView?.setImageBitmap(artwork)
                artworkView?.visibility = View.VISIBLE
            } else {
                artworkView?.visibility = View.GONE
            }
        }

        // Smooth crossfade when primary lyric text changes
        if (primaryText != lastPrimaryText) {
            lastPrimaryText = primaryText
            primaryLyricView?.let { tv ->
                tv.animate().cancel()
                tv.alpha = 0.25f
                tv.text = primaryText
                tv.isSelected = true
                tv.animate().alpha(1f).setDuration(200L).start()
            }
        }

        // Optional compact Indonesian translation line directly below
        if (translatedText != lastTranslatedText) {
            lastTranslatedText = translatedText
            translatedLyricView?.let { tv ->
                if (!translatedText.isNullOrBlank()) {
                    tv.text = translatedText
                    tv.visibility = View.VISIBLE
                } else {
                    tv.visibility = View.GONE
                }
            }
        }

        visualizerView?.setAnimating(snap.isPlaying)
    }

    private fun ensureAttached() {
        if (isAttached) return
        val wm = windowManager ?: return
        if (!canDrawSystemOverlay()) return

        val view = rootContainer ?: buildOverlayView().also { rootContainer = it }
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = getStatusBarHeightPx() + dp(6f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        runCatching {
            view.alpha = 0f
            view.scaleX = 0.92f
            view.scaleY = 0.92f
            wm.addView(view, params)
            isAttached = true
            view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220L)
                .start()
        }.onFailure {
            isAttached = false
        }
    }

    fun detach() {
        visualizerView?.setAnimating(false)
        if (!isAttached) return
        val wm = windowManager
        val view = rootContainer
        if (wm != null && view != null) {
            runCatching {
                view.animate().cancel()
                wm.removeViewImmediate(view)
            }
        }
        isAttached = false
        lastPrimaryText = null
        lastTranslatedText = null
    }

    fun destroy() {
        detach()
        rootContainer = null
        artworkView = null
        primaryLyricView = null
        translatedLyricView = null
        visualizerView = null
        pillBackground = null
        lastBitmapRef = null
    }

    private fun buildOverlayView(): LinearLayout {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(999f).toFloat()
            setColor(Color.parseColor("#EE060609"))
            setStroke(dp(1f), blendWithWhite(0xFF00E5A0.toInt(), 0.34f, alpha = 0x72))
        }
        pillBackground = bg

        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bg
            elevation = dp(10f).toFloat()
            setPadding(dp(10f), dp(6f), dp(11f), dp(6f))
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

        // 1. Circular Album Artwork (22dp)
        val artSize = dp(22f)
        val artIv = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(artSize, artSize).apply {
                marginEnd = dp(8f)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            visibility = View.GONE
        }
        artworkView = artIv
        pill.addView(artIv)

        // 2. Lyrics Text Column (Original + optional compact Indonesian translation)
        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val maxTextWidthPx = dp(238f)
        val primaryTv = TextView(context).apply {
            setTextColor(Color.parseColor("#F6F7FB"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.2f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isSingleLine = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            maxWidth = maxTextWidthPx
            isSelected = true
        }
        primaryLyricView = primaryTv
        textCol.addView(primaryTv)

        val transTv = TextView(context).apply {
            setTextColor(Color.parseColor("#B8F2D6"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.2f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isSingleLine = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = maxTextWidthPx
            visibility = View.GONE
        }
        translatedLyricView = transTv
        textCol.addView(transTv)
        pill.addView(textCol)

        // 3. Animated 3-bar Mini Audio Visualizer
        val eqView = MiniEqBarsView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(14f), dp(12f)).apply {
                marginStart = dp(8f)
            }
        }
        visualizerView = eqView
        pill.addView(eqView)

        return pill
    }

    private fun getStatusBarHeightPx(): Int {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) context.resources.getDimensionPixelSize(resId) else dp(28f)
    }

    private fun dp(value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics
        ).toInt().coerceAtLeast(1)
    }

    private fun blendWithWhite(color: Int, whiteRatio: Float, alpha: Int): Int {
        val r = (Color.red(color) * (1f - whiteRatio) + 255f * whiteRatio).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * (1f - whiteRatio) + 255f * whiteRatio).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * (1f - whiteRatio) + 255f * whiteRatio).toInt().coerceIn(0, 255)
        return Color.argb(alpha.coerceIn(0, 255), r, g, b)
    }

    /**
     * Lightweight native 3-bar animated equalizer indicator (~12fps) for the floating Dynamic Island.
     */
    private class MiniEqBarsView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6CF3C6")
            style = Paint.Style.FILL
        }
        private val handler = Handler(Looper.getMainLooper())
        private val barRect = RectF()
        private var phase: Float = 0f
        private var animating: Boolean = false

        private val tickRunnable = object : Runnable {
            override fun run() {
                if (!animating) return
                phase += 0.42f
                invalidate()
                handler.postDelayed(this, 85L)
            }
        }

        fun setBarColor(color: Int) {
            paint.color = color
            invalidate()
        }

        fun setAnimating(active: Boolean) {
            if (animating == active) return
            animating = active
            handler.removeCallbacks(tickRunnable)
            if (active) {
                handler.post(tickRunnable)
            } else {
                invalidate()
            }
        }

        override fun onDetachedFromWindow() {
            animating = false
            handler.removeCallbacks(tickRunnable)
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val barCount = 3
            val gap = w * 0.16f
            val barW = ((w - gap * (barCount - 1)) / barCount).coerceAtLeast(2f)
            val radius = barW / 2f

            for (i in 0 until barCount) {
                val frac = if (animating) {
                    val wave = (sin((phase + i * 1.35f).toDouble()).toFloat() + 1f) * 0.5f
                    0.28f + wave * 0.72f
                } else {
                    0.3f
                }
                val barH = (h * frac).coerceAtLeast(barW)
                val left = i * (barW + gap)
                val top = h - barH
                barRect.set(left, top, left + barW, h)
                canvas.drawRoundRect(barRect, radius, radius, paint)
            }
        }
    }
}
