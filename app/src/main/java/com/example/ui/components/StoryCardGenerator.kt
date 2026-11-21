package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import com.example.model.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SingleStoryStyle(val label: String) {
    POLAROID("Polaroid"),
    VINYL("Vinyl Piringan"),
    GLASS("Kaca Estetik")
}

enum class MultiStoryStyle(val label: String) {
    MIXTAPE_RECEIPT("Struk Mixtape"),
    GLASS_POSTER("Poster Kaca"),
    POLAROID_COLLAGE("Kolase Polaroid")
}

/**
 * Generates high-resolution 9:16 (1080x1920) aesthetic Instagram / WhatsApp Story PNG cards
 * for both a single Now Playing track (Polaroid / Vinyl / Frosted Glass) and multiple songs
 * (Mixtape Receipt / Glass Poster / Polaroid Collage).
 */
object StoryCardGenerator {

    private const val STORY_WIDTH = 1080
    private const val STORY_HEIGHT = 1920

    suspend fun saveSingleTrackStoryToGallery(
        context: Context,
        track: Track,
        style: SingleStoryStyle,
        accentColorInt: Int,
        caption: String = ""
    ): Result<Uri> {
        return runCatching {
            val artwork = LyricCardGenerator.loadArtworkBitmap(context, track.artworkUrl, sizePx = 760)
            val bitmap = Bitmap.createBitmap(STORY_WIDTH, STORY_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val accent = LyricCardGenerator.ensureVibrantAccent(accentColorInt)

            when (style) {
                SingleStoryStyle.POLAROID -> drawSinglePolaroid(canvas, track, artwork, accent, caption.trim())
                SingleStoryStyle.VINYL -> drawSingleVinyl(canvas, track, artwork, accent, caption.trim())
                SingleStoryStyle.GLASS -> drawSingleGlass(canvas, track, artwork, accent, caption.trim())
            }

            try {
                LyricCardGenerator.saveBitmapToMediaStore(
                    context = context,
                    bitmap = bitmap,
                    filePrefix = "Koplingfy_Story_${style.name.lowercase()}"
                )
            } finally {
                bitmap.recycle()
            }
        }
    }

    suspend fun saveMultiTrackStoryToGallery(
        context: Context,
        title: String,
        tracks: List<Track>,
        style: MultiStoryStyle,
        accentColorInt: Int
    ): Result<Uri> {
        return runCatching {
            val selected = tracks.take(6)
            require(selected.isNotEmpty()) { "Pilih minimal 1 lagu untuk kartu Story" }

            val artworks = coroutineScope {
                selected.map { track ->
                    async { LyricCardGenerator.loadArtworkBitmap(context, track.artworkUrl, sizePx = 420) }
                }.awaitAll()
            }

            val bitmap = Bitmap.createBitmap(STORY_WIDTH, STORY_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val accent = LyricCardGenerator.ensureVibrantAccent(accentColorInt)
            val safeTitle = title.trim().ifBlank { "Lagi Dengerin Ini" }

            when (style) {
                MultiStoryStyle.MIXTAPE_RECEIPT -> drawMultiMixtapeReceipt(canvas, safeTitle, selected, artworks, accent)
                MultiStoryStyle.GLASS_POSTER -> drawMultiGlassPoster(canvas, safeTitle, selected, artworks, accent)
                MultiStoryStyle.POLAROID_COLLAGE -> drawMultiPolaroidCollage(canvas, safeTitle, selected, artworks, accent)
            }

            try {
                LyricCardGenerator.saveBitmapToMediaStore(
                    context = context,
                    bitmap = bitmap,
                    filePrefix = "Koplingfy_MixStory_${style.name.lowercase()}"
                )
            } finally {
                bitmap.recycle()
            }
        }
    }

    // -------------------------------------------------------------------------
    // SINGLE TRACK STORY STYLES (1080x1920)
    // -------------------------------------------------------------------------

    private fun drawSinglePolaroid(
        canvas: Canvas,
        track: Track,
        artwork: Bitmap?,
        accent: Int,
        caption: String
    ) {
        drawAmbientStoryBackdrop(canvas, artwork, accent, warmTint = true)

        // Subtle top pill
        drawStoryTopBadge(canvas, "NOW PLAYING • FILM", y = 170f, accent = accent)

        canvas.save()
        canvas.rotate(-2.4f, STORY_WIDTH * 0.5f, STORY_HEIGHT * 0.50f)

        val cardW = 860f
        val cardH = 1140f
        val left = (STORY_WIDTH - cardW) / 2f
        val top = 330f
        val paperRect = RectF(left, top, left + cardW, top + cardH)

        // Drop shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(135, 0, 0, 0) }
        canvas.drawRoundRect(
            RectF(paperRect.left + 20f, paperRect.top + 32f, paperRect.right + 20f, paperRect.bottom + 32f),
            20f, 20f, shadowPaint
        )

        // Warm Polaroid paper
        val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF8F4EB.toInt() }
        canvas.drawRoundRect(paperRect, 18f, 18f, paperPaint)

        // Washi tape accent on top center
        val tapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LyricCardGenerator.withAlpha(LyricCardGenerator.blendColor(accent, 0xFFE5D7BE.toInt(), 0.65f), 195)
        }
        canvas.drawRoundRect(
            RectF(STORY_WIDTH * 0.5f - 110f, paperRect.top - 26f, STORY_WIDTH * 0.5f + 110f, paperRect.top + 34f),
            6f, 6f, tapePaint
        )

        // Square photo frame
        val artPad = 52f
        val artSize = cardW - artPad * 2f
        val artRect = RectF(paperRect.left + artPad, paperRect.top + 60f, paperRect.right - artPad, paperRect.top + 60f + artSize)
        LyricCardGenerator.drawRoundedArtwork(canvas, artwork, artRect, cornerRadius = 8f, fallbackColor = accent)

        // Thin inner frame edge
        val photoFrame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(38, 0, 0, 0)
        }
        canvas.drawRoundRect(artRect, 8f, 8f, photoFrame)

        // Chin content: title, artist, optional note, waveform
        val chinTop = artRect.bottom + 42f
        LyricCardGenerator.drawSingleOrTwoLineText(
            canvas = canvas,
            text = track.title,
            x = artRect.left,
            y = chinTop,
            maxWidth = artSize.toInt(),
            textSize = 44f,
            color = 0xFF1A1816.toInt(),
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD),
            maxLines = 1
        )
        LyricCardGenerator.drawSingleOrTwoLineText(
            canvas = canvas,
            text = track.artist,
            x = artRect.left,
            y = chinTop + 56f,
            maxWidth = artSize.toInt(),
            textSize = 30f,
            color = 0xFF665E55.toInt(),
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL),
            maxLines = 1
        )

        if (caption.isNotBlank()) {
            LyricCardGenerator.drawSingleOrTwoLineText(
                canvas = canvas,
                text = "“$caption”",
                x = artRect.left,
                y = chinTop + 106f,
                maxWidth = artSize.toInt(),
                textSize = 27f,
                color = 0xFF423C36.toInt(),
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC),
                maxLines = 1
            )
        }

        // Bottom waveform + timestamp inside Polaroid chin
        val waveY = paperRect.bottom - 62f
        LyricCardGenerator.drawMiniWaveform(
            canvas = canvas,
            startX = artRect.left,
            centerY = waveY,
            width = 300f,
            barCount = 24,
            activeColor = 0xFF1E1C1A.toInt(),
            inactiveColor = 0xFFD2C9BC.toInt(),
            progressFraction = 0.64f,
            maxBarHeight = 28f
        )
        val durPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF7D7469.toInt()
            textSize = 24f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(formatDuration(track.durationMs), artRect.right, waveY + 8f, durPaint)

        canvas.restore()

        drawStoryFooterBrand(canvas, y = 1720f)
    }

    private fun drawSingleVinyl(
        canvas: Canvas,
        track: Track,
        artwork: Bitmap?,
        accent: Int,
        caption: String
    ) {
        drawAmbientStoryBackdrop(canvas, artwork, accent, warmTint = false)
        drawStoryTopBadge(canvas, "SPINNING ON VINYL", y = 180f, accent = accent)

        // 1. Realistic 12" Vinyl Disc peeking out to the right of sleeve
        val sleeveSize = 620f
        val sleeveLeft = 112f
        val sleeveTop = 430f
        val vinylCenterX = sleeveLeft + sleeveSize - 42f
        val vinylCenterY = sleeveTop + sleeveSize * 0.5f
        val vinylRadius = sleeveSize * 0.47f

        // Vinyl shadow
        val vinylShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(145, 0, 0, 0) }
        canvas.drawCircle(vinylCenterX + 12f, vinylCenterY + 18f, vinylRadius, vinylShadow)

        // Glossy black vinyl base
        val vinylBase = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                vinylCenterX, vinylCenterY, vinylRadius,
                intArrayOf(0xFF1A1B20.toInt(), 0xFF0A0A0D.toInt(), 0xFF1E1F26.toInt(), 0xFF08080A.toInt()),
                floatArrayOf(0.28f, 0.55f, 0.78f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(vinylCenterX, vinylCenterY, vinylRadius, vinylBase)

        // Concentric vinyl grooves
        val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
            color = Color.argb(46, 255, 255, 255)
        }
        var r = vinylRadius * 0.44f
        while (r < vinylRadius - 14f) {
            canvas.drawCircle(vinylCenterX, vinylCenterY, r, groovePaint)
            r += 16f
        }

        // Vinyl sheen reflection arc
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                vinylCenterX - vinylRadius, vinylCenterY - vinylRadius,
                vinylCenterX + vinylRadius, vinylCenterY + vinylRadius,
                intArrayOf(Color.TRANSPARENT, Color.argb(54, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0.25f, 0.50f, 0.75f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(vinylCenterX, vinylCenterY, vinylRadius, sheenPaint)

        // Center record label (tinted with accent + spindle hole)
        val labelRadius = vinylRadius * 0.34f
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        canvas.drawCircle(vinylCenterX, vinylCenterY, labelRadius, labelPaint)
        val labelRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.argb(140, 255, 255, 255)
        }
        canvas.drawCircle(vinylCenterX, vinylCenterY, labelRadius * 0.74f, labelRing)
        val spindlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0E0E12.toInt() }
        canvas.drawCircle(vinylCenterX, vinylCenterY, 16f, spindlePaint)

        // 2. Square Album Sleeve on top
        val sleeveRect = RectF(sleeveLeft, sleeveTop, sleeveLeft + sleeveSize, sleeveTop + sleeveSize)
        val sleeveShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) }
        canvas.drawRoundRect(
            RectF(sleeveRect.left + 14f, sleeveRect.top + 22f, sleeveRect.right + 14f, sleeveRect.bottom + 22f),
            26f, 26f, sleeveShadow
        )
        LyricCardGenerator.drawRoundedArtwork(canvas, artwork, sleeveRect, cornerRadius = 26f, fallbackColor = accent)

        // Sleeve crease highlight on left edge
        val crease = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(46, 255, 255, 255)
            strokeWidth = 3f
        }
        canvas.drawLine(sleeveRect.left + 26f, sleeveRect.top + 16f, sleeveRect.left + 26f, sleeveRect.bottom - 16f, crease)

        // 3. Track metadata & glowing waveform below
        val metaLeft = 104f
        val metaWidth = (STORY_WIDTH - metaLeft * 2).toInt()
        val infoY = sleeveRect.bottom + 110f

        LyricCardGenerator.drawSingleOrTwoLineText(
            canvas = canvas,
            text = track.title,
            x = metaLeft,
            y = infoY,
            maxWidth = metaWidth,
            textSize = 54f,
            color = Color.WHITE,
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
            maxLines = 2
        )
        LyricCardGenerator.drawSingleOrTwoLineText(
            canvas = canvas,
            text = track.artist,
            x = metaLeft,
            y = infoY + 126f,
            maxWidth = metaWidth,
            textSize = 34f,
            color = Color.argb(205, 255, 255, 255),
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL),
            maxLines = 1
        )
        if (caption.isNotBlank()) {
            LyricCardGenerator.drawSingleOrTwoLineText(
                canvas = canvas,
                text = caption,
                x = metaLeft,
                y = infoY + 186f,
                maxWidth = metaWidth,
                textSize = 28f,
                color = LyricCardGenerator.blendColor(accent, Color.WHITE, 0.55f),
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC),
                maxLines = 1
            )
        }

        // Full-width audiophile waveform bar
        val waveY = infoY + 286f
        LyricCardGenerator.drawMiniWaveform(
            canvas = canvas,
            startX = metaLeft,
            centerY = waveY,
            width = STORY_WIDTH - metaLeft * 2,
            barCount = 44,
            activeColor = LyricCardGenerator.blendColor(accent, Color.WHITE, 0.38f),
            inactiveColor = Color.argb(72, 255, 255, 255),
            progressFraction = 0.56f,
            maxBarHeight = 44f
        )

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 255, 255, 255)
            textSize = 25f
            typeface = Typeface.MONOSPACE
        }
        canvas.drawText("01:42", metaLeft, waveY + 58f, timePaint)
        timePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(formatDuration(track.durationMs), STORY_WIDTH - metaLeft, waveY + 58f, timePaint)

        drawStoryFooterBrand(canvas, y = 1740f)
    }

    private fun drawSingleGlass(
        canvas: Canvas,
        track: Track,
        artwork: Bitmap?,
        accent: Int,
        caption: String
    ) {
        drawAmbientStoryBackdrop(canvas, artwork, accent, warmTint = false)

        // Centered Frosted Glass Player Card
        val cardMarginX = 96f
        val cardRect = RectF(cardMarginX, 270f, STORY_WIDTH - cardMarginX, 1590f)

        val glassBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                intArrayOf(
                    Color.argb(66, 255, 255, 255),
                    Color.argb(30, 255, 255, 255),
                    Color.argb(46, 10, 12, 18)
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardRect, 64f, 64f, glassBg)

        val glassRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.6f
            shader = LinearGradient(
                cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                Color.argb(145, 255, 255, 255),
                Color.argb(42, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardRect, 64f, 64f, glassRim)

        val pad = 64f
        val artSize = cardRect.width() - pad * 2f
        val artRect = RectF(cardRect.left + pad, cardRect.top + pad, cardRect.right - pad, cardRect.top + pad + artSize)
        LyricCardGenerator.drawRoundedArtwork(canvas, artwork, artRect, cornerRadius = 40f, fallbackColor = accent)

        // Track title + artist
        val titleY = artRect.bottom + 56f
        LyricCardGenerator.drawSingleOrTwoLineText(
            canvas = canvas,
            text = track.title,
            x = artRect.left,
            y = titleY,
            maxWidth = artSize.toInt(),
            textSize = 46f,
            color = Color.WHITE,
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
            maxLines = 2
        )
        LyricCardGenerator.drawSingleOrTwoLineText(
            canvas = canvas,
            text = track.artist,
            x = artRect.left,
            y = titleY + 106f,
            maxWidth = artSize.toInt(),
            textSize = 31f,
            color = Color.argb(205, 255, 255, 255),
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL),
            maxLines = 1
        )

        if (caption.isNotBlank()) {
            LyricCardGenerator.drawSingleOrTwoLineText(
                canvas = canvas,
                text = caption,
                x = artRect.left,
                y = titleY + 158f,
                maxWidth = artSize.toInt(),
                textSize = 26f,
                color = LyricCardGenerator.blendColor(accent, Color.WHITE, 0.55f),
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC),
                maxLines = 1
            )
        }

        // Waveform bar
        val waveY = cardRect.bottom - 208f
        LyricCardGenerator.drawMiniWaveform(
            canvas = canvas,
            startX = artRect.left,
            centerY = waveY,
            width = artSize,
            barCount = 36,
            activeColor = Color.WHITE,
            inactiveColor = Color.argb(80, 255, 255, 255),
            progressFraction = 0.62f,
            maxBarHeight = 38f
        )

        // Minimal aesthetic playback controls graphic (prev • play circle • next)
        val ctrlY = cardRect.bottom - 94f
        val cx = cardRect.centerX()
        val playCircle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(cx, ctrlY, 44f, playCircle)

        val pauseBar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF101116.toInt() }
        canvas.drawRoundRect(RectF(cx - 14f, ctrlY - 17f, cx - 4f, ctrlY + 17f), 4f, 4f, pauseBar)
        canvas.drawRoundRect(RectF(cx + 4f, ctrlY - 17f, cx + 14f, ctrlY + 17f), 4f, 4f, pauseBar)

        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(215, 255, 255, 255) }
        // Previous triangle
        val prevPath = Path().apply {
            moveTo(cx - 122f, ctrlY)
            lineTo(cx - 94f, ctrlY - 18f)
            lineTo(cx - 94f, ctrlY + 18f)
            close()
        }
        canvas.drawPath(prevPath, iconPaint)
        canvas.drawRect(cx - 130f, ctrlY - 17f, cx - 124f, ctrlY + 17f, iconPaint)

        // Next triangle
        val nextPath = Path().apply {
            moveTo(cx + 122f, ctrlY)
            lineTo(cx + 94f, ctrlY - 18f)
            lineTo(cx + 94f, ctrlY + 18f)
            close()
        }
        canvas.drawPath(nextPath, iconPaint)
        canvas.drawRect(cx + 124f, ctrlY - 17f, cx + 130f, ctrlY + 17f, iconPaint)

        drawStoryFooterBrand(canvas, y = 1730f)
    }

    // -------------------------------------------------------------------------
    // MULTI-TRACK STORY STYLES (1080x1920)
    // -------------------------------------------------------------------------

    private fun drawMultiMixtapeReceipt(
        canvas: Canvas,
        title: String,
        tracks: List<Track>,
        artworks: List<Bitmap?>,
        accent: Int
    ) {
        drawAmbientStoryBackdrop(canvas, artworks.firstOrNull(), accent, warmTint = true)

        val receiptLeft = 104f
        val receiptRight = STORY_WIDTH - 104f
        val receiptTop = 190f
        val receiptBottom = 1690f
        val receiptWidth = receiptRight - receiptLeft

        // Receipt shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(135, 0, 0, 0) }
        canvas.drawRect(receiptLeft + 16f, receiptTop + 26f, receiptRight + 16f, receiptBottom + 26f, shadowPaint)

        // Thermal paper body with serrated top & bottom edges
        val paperPath = Path().apply {
            val zigW = 24f
            val zigH = 16f
            moveTo(receiptLeft, receiptTop + zigH)
            var x = receiptLeft
            while (x < receiptRight) {
                lineTo((x + zigW / 2f).coerceAtMost(receiptRight), receiptTop)
                lineTo((x + zigW).coerceAtMost(receiptRight), receiptTop + zigH)
                x += zigW
            }
            lineTo(receiptRight, receiptBottom - zigH)
            x = receiptRight
            while (x > receiptLeft) {
                lineTo((x - zigW / 2f).coerceAtLeast(receiptLeft), receiptBottom)
                lineTo((x - zigW).coerceAtLeast(receiptLeft), receiptBottom - zigH)
                x -= zigW
            }
            close()
        }
        val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5F1E7.toInt() }
        canvas.drawPath(paperPath, paperPaint)

        val innerL = receiptLeft + 56f
        val innerR = receiptRight - 56f
        val innerW = (innerR - innerL).toInt()
        val inkDark = 0xFF191816.toInt()
        val inkMuted = 0xFF6A645B.toInt()

        // Receipt Header
        val centerBrand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkDark
            textSize = 42f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.14f
        }
        canvas.drawText("KOPLINGFY MIX", STORY_WIDTH * 0.5f, receiptTop + 104f, centerBrand)

        val dateStr = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault()).format(Date()).uppercase(Locale.getDefault())
        val subHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkMuted
            textSize = 23f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("$dateStr  •  ${tracks.size} LAGU", STORY_WIDTH * 0.5f, receiptTop + 148f, subHeader)

        // Editable Mix Title
        val mixTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkDark
            textSize = 36f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(title.take(28), STORY_WIDTH * 0.5f, receiptTop + 216f, mixTitlePaint)

        // Artwork strip (up to 4 rounded mini squares)
        val thumbs = artworks.take(4)
        val thumbSize = 136f
        val gap = 20f
        val totalThumbW = thumbs.size * thumbSize + (thumbs.size - 1).coerceAtLeast(0) * gap
        var thumbX = STORY_WIDTH * 0.5f - totalThumbW / 2f
        val thumbY = receiptTop + 254f
        thumbs.forEachIndexed { idx, bmp ->
            LyricCardGenerator.drawRoundedArtwork(
                canvas = canvas,
                artwork = bmp,
                rect = RectF(thumbX, thumbY, thumbX + thumbSize, thumbY + thumbSize),
                cornerRadius = 14f,
                fallbackColor = accent
            )
            thumbX += thumbSize + gap
        }

        // Dashed separator
        val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFB5ADA0.toInt()
            textSize = 24f
            typeface = Typeface.MONOSPACE
        }
        val sepY1 = thumbY + thumbSize + 58f
        canvas.drawText("- - - - - - - - - - - - - - - - - - - - - -", innerL, sepY1, dashPaint)

        // Tracklist rows
        var rowY = sepY1 + 42f
        val rowStep = ((1320f - rowY) / tracks.size.coerceAtLeast(1)).coerceIn(96f, 134f)
        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkMuted
            textSize = 26f
            typeface = Typeface.MONOSPACE
        }
        val durPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkDark
            textSize = 25f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.RIGHT
        }

        tracks.forEachIndexed { index, track ->
            val numStr = String.format(Locale.US, "%02d", index + 1)
            canvas.drawText(numStr, innerL, rowY + 26f, numPaint)

            val textX = innerL + 54f
            val textW = (innerR - 115f - textX).toInt().coerceAtLeast(160)
            LyricCardGenerator.drawSingleOrTwoLineText(
                canvas = canvas,
                text = track.title,
                x = textX,
                y = rowY,
                maxWidth = textW,
                textSize = 30f,
                color = inkDark,
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
                maxLines = 1
            )
            LyricCardGenerator.drawSingleOrTwoLineText(
                canvas = canvas,
                text = track.artist,
                x = textX,
                y = rowY + 38f,
                maxWidth = textW,
                textSize = 23f,
                color = inkMuted,
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL),
                maxLines = 1
            )

            canvas.drawText(formatDuration(track.durationMs), innerR, rowY + 28f, durPaint)
            rowY += rowStep
        }

        // Total duration & separator
        val sepY2 = 1368f
        canvas.drawText("- - - - - - - - - - - - - - - - - - - - - -", innerL, sepY2, dashPaint)

        val totalMs = tracks.sumOf { it.durationMs }
        val totalLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkDark
            textSize = 28f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText("TOTAL DURASI", innerL, sepY2 + 54f, totalLabelPaint)
        totalLabelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(formatDuration(totalMs), innerR, sepY2 + 54f, totalLabelPaint)

        // Crisp barcode graphic at bottom
        val barcodeTop = sepY2 + 94f
        val barcodeH = 104f
        val barPaint = Paint().apply { color = inkDark }
        val pattern = intArrayOf(4, 2, 7, 3, 2, 6, 4, 2, 8, 3, 5, 2, 4, 6, 2, 3, 7, 2, 5, 3, 4, 2, 6, 3, 5, 2, 7, 4)
        val totalUnits = pattern.sum() + pattern.size * 2
        val unitW = (innerW * 0.84f) / totalUnits
        var bx = STORY_WIDTH * 0.5f - (innerW * 0.84f) / 2f
        pattern.forEach { w ->
            canvas.drawRect(bx, barcodeTop, bx + w * unitW, barcodeTop + barcodeH, barPaint)
            bx += (w + 2) * unitW
        }

        val thankYou = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkMuted
            textSize = 21f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.12f
        }
        canvas.drawText("TERIMA KASIH SUDAH MENDENGARKAN", STORY_WIDTH * 0.5f, barcodeTop + barcodeH + 44f, thankYou)

        drawStoryFooterBrand(canvas, y = 1785f)
    }

    private fun drawMultiGlassPoster(
        canvas: Canvas,
        title: String,
        tracks: List<Track>,
        artworks: List<Bitmap?>,
        accent: Int
    ) {
        drawAmbientStoryBackdrop(canvas, artworks.firstOrNull(), accent, warmTint = false)

        val cardL = 78f
        val cardR = STORY_WIDTH - 78f
        val cardTop = 185f
        val cardBottom = 1695f
        val cardRect = RectF(cardL, cardTop, cardR, cardBottom)

        // Translucent frosted glass sheet
        val glassBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                intArrayOf(
                    Color.argb(64, 255, 255, 255),
                    Color.argb(28, 255, 255, 255),
                    Color.argb(46, 12, 13, 20)
                ),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardRect, 58f, 58f, glassBg)

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.argb(105, 255, 255, 255)
        }
        canvas.drawRoundRect(cardRect, 58f, 58f, stroke)

        val innerL = cardL + 56f
        val innerR = cardR - 56f
        val innerW = innerR - innerL

        // 2x2 Mosaic Artwork Grid at top center
        val gridSize = 420f
        val gridL = STORY_WIDTH * 0.5f - gridSize / 2f
        val gridTop = cardTop + 62f
        val cellGap = 16f
        val cellSize = (gridSize - cellGap) / 2f
        for (i in 0 until 4) {
            val col = i % 2
            val row = i / 2
            val cx = gridL + col * (cellSize + cellGap)
            val cy = gridTop + row * (cellSize + cellGap)
            val bmp = artworks.getOrNull(i % artworks.size.coerceAtLeast(1))
            LyricCardGenerator.drawRoundedArtwork(
                canvas = canvas,
                artwork = bmp,
                rect = RectF(cx, cy, cx + cellSize, cy + cellSize),
                cornerRadius = 24f,
                fallbackColor = accent
            )
        }

        // Bold Story Header Title + Subtitle
        val titleY = gridTop + gridSize + 58f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 48f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(title.take(26), STORY_WIDTH * 0.5f, titleY, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LyricCardGenerator.blendColor(accent, Color.WHITE, 0.55f)
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.12f
        }
        canvas.drawText("${tracks.size} LAGU PILIHAN • KOPLINGFY", STORY_WIDTH * 0.5f, titleY + 42f, subPaint)

        // Track Rows
        var rowY = titleY + 86f
        val availableH = cardBottom - 64f - rowY
        val rowStep = (availableH / tracks.size.coerceAtLeast(1)).coerceIn(96f, 134f)
        val thumbSize = (rowStep * 0.68f).coerceIn(66f, 84f)

        val rowCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(46, 0, 0, 0) }
        val durPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(175, 255, 255, 255)
            textSize = 24f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.RIGHT
        }

        tracks.forEachIndexed { idx, track ->
            val rowRect = RectF(innerL, rowY, innerR, rowY + rowStep - 16f)
            canvas.drawRoundRect(rowRect, 24f, 24f, rowCardPaint)

            val artTop = rowRect.centerY() - thumbSize / 2f
            LyricCardGenerator.drawRoundedArtwork(
                canvas = canvas,
                artwork = artworks.getOrNull(idx),
                rect = RectF(rowRect.left + 16f, artTop, rowRect.left + 16f + thumbSize, artTop + thumbSize),
                cornerRadius = 14f,
                fallbackColor = accent
            )

            val tx = rowRect.left + 16f + thumbSize + 22f
            val tw = (rowRect.right - 108f - tx).toInt().coerceAtLeast(150)
            LyricCardGenerator.drawSingleOrTwoLineText(
                canvas = canvas,
                text = track.title,
                x = tx,
                y = rowRect.centerY() - 32f,
                maxWidth = tw,
                textSize = 29f,
                color = Color.WHITE,
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
                maxLines = 1
            )
            LyricCardGenerator.drawSingleOrTwoLineText(
                canvas = canvas,
                text = track.artist,
                x = tx,
                y = rowRect.centerY() + 6f,
                maxWidth = tw,
                textSize = 23f,
                color = Color.argb(190, 255, 255, 255),
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL),
                maxLines = 1
            )

            canvas.drawText(formatDuration(track.durationMs), rowRect.right - 22f, rowRect.centerY() + 8f, durPaint)
            rowY += rowStep
        }

        drawStoryFooterBrand(canvas, y = 1785f)
    }

    private fun drawMultiPolaroidCollage(
        canvas: Canvas,
        title: String,
        tracks: List<Track>,
        artworks: List<Bitmap?>,
        accent: Int
    ) {
        drawAmbientStoryBackdrop(canvas, artworks.firstOrNull(), accent, warmTint = true)
        drawStoryTopBadge(canvas, "PLAYLIST MOODBOARD", y = 160f, accent = accent)

        // Overlapping tilted mini Polaroids in upper half (up to 4)
        val collageCount = artworks.size.coerceAtMost(4)
        val placements = listOf(
            Triple(290f, 445f, -9f),
            Triple(770f, 465f, 8f),
            Triple(340f, 760f, 5f),
            Triple(740f, 775f, -6f)
        )
        val miniW = 340f
        val miniH = 408f
        val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF7F2E8.toInt() }
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 0, 0, 0) }

        for (i in 0 until collageCount) {
            val (cx, cy, angle) = placements[i]
            val bmp = artworks.getOrNull(i)
            val track = tracks.getOrNull(i)
            canvas.save()
            canvas.rotate(angle, cx, cy)
            val rect = RectF(cx - miniW / 2f, cy - miniH / 2f, cx + miniW / 2f, cy + miniH / 2f)
            canvas.drawRoundRect(
                RectF(rect.left + 12f, rect.top + 18f, rect.right + 12f, rect.bottom + 18f),
                12f, 12f, shadowPaint
            )
            canvas.drawRoundRect(rect, 12f, 12f, paperPaint)
            val artPad = 22f
            val artSize = miniW - artPad * 2f
            LyricCardGenerator.drawRoundedArtwork(
                canvas = canvas,
                artwork = bmp,
                rect = RectF(rect.left + artPad, rect.top + artPad, rect.right - artPad, rect.top + artPad + artSize),
                cornerRadius = 6f,
                fallbackColor = accent
            )
            if (track != null) {
                LyricCardGenerator.drawSingleOrTwoLineText(
                    canvas = canvas,
                    text = track.title,
                    x = rect.left + artPad,
                    y = rect.top + artPad + artSize + 18f,
                    maxWidth = artSize.toInt(),
                    textSize = 22f,
                    color = 0xFF24201C.toInt(),
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD),
                    maxLines = 1
                )
            }
            canvas.restore()
        }

        // Bottom Editorial Tracklist Card
        val listCard = RectF(84f, 1020f, STORY_WIDTH - 84f, 1685f)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(226, 20, 18, 17)
        }
        canvas.drawRoundRect(listCard, 44f, 44f, cardPaint)
        val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(72, 247, 242, 232)
        }
        canvas.drawRoundRect(listCard, 44f, 44f, cardBorder)

        val padX = listCard.left + 52f
        val maxW = (listCard.right - 52f - padX).toInt()
        LyricCardGenerator.drawSingleOrTwoLineText(
            canvas = canvas,
            text = title,
            x = padX,
            y = listCard.top + 42f,
            maxWidth = maxW,
            textSize = 42f,
            color = 0xFFF7F2E8.toInt(),
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD),
            maxLines = 1
        )

        var y = listCard.top + 114f
        val rowStep = ((listCard.bottom - 44f - y) / tracks.size.coerceAtLeast(1)).coerceIn(78f, 96f)
        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LyricCardGenerator.blendColor(accent, 0xFFF7F2E8.toInt(), 0.45f)
            textSize = 26f
            typeface = Typeface.MONOSPACE
        }
        tracks.forEachIndexed { idx, t ->
            canvas.drawText("${idx + 1}.", padX, y + 26f, numPaint)
            LyricCardGenerator.drawSingleOrTwoLineText(
                canvas = canvas,
                text = t.title,
                x = padX + 46f,
                y = y,
                maxWidth = maxW - 46,
                textSize = 29f,
                color = Color.WHITE,
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
                maxLines = 1
            )
            LyricCardGenerator.drawSingleOrTwoLineText(
                canvas = canvas,
                text = t.artist,
                x = padX + 46f,
                y = y + 36f,
                maxWidth = maxW - 46,
                textSize = 22f,
                color = Color.argb(180, 247, 242, 232),
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL),
                maxLines = 1
            )
            y += rowStep
        }

        drawStoryFooterBrand(canvas, y = 1785f)
    }

    // -------------------------------------------------------------------------
    // SHARED BACKDROP & BRAND CHROME
    // -------------------------------------------------------------------------

    private fun drawAmbientStoryBackdrop(
        canvas: Canvas,
        artwork: Bitmap?,
        accent: Int,
        warmTint: Boolean
    ) {
        val topBase = if (warmTint) {
            LyricCardGenerator.blendColor(0xFF1A1512.toInt(), accent, 0.36f)
        } else {
            LyricCardGenerator.blendColor(0xFF0E1018.toInt(), accent, 0.44f)
        }
        val midBase = if (warmTint) 0xFF12100E.toInt() else 0xFF08090E.toInt()
        val botBase = LyricCardGenerator.blendColor(0xFF06070A.toInt(), accent, 0.24f)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, STORY_WIDTH.toFloat(), STORY_HEIGHT.toFloat(),
                intArrayOf(topBase, midBase, botBase),
                floatArrayOf(0f, 0.54f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, STORY_WIDTH.toFloat(), STORY_HEIGHT.toFloat(), bgPaint)

        if (artwork != null) {
            val wash = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 48 }
            LyricCardGenerator.drawCenterCroppedBitmap(
                canvas,
                artwork,
                RectF(0f, 0f, STORY_WIDTH.toFloat(), STORY_HEIGHT.toFloat()),
                wash
            )
            val scrim = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, STORY_HEIGHT.toFloat(),
                    intArrayOf(
                        Color.argb(145, 9, 9, 13),
                        Color.argb(175, 9, 9, 13),
                        Color.argb(230, 5, 6, 9)
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, STORY_WIDTH.toFloat(), STORY_HEIGHT.toFloat(), scrim)
        }

        // Ambient glow orbs
        val orbTop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                STORY_WIDTH * 0.2f, STORY_HEIGHT * 0.16f, STORY_WIDTH * 0.78f,
                intArrayOf(LyricCardGenerator.withAlpha(accent, 115), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(STORY_WIDTH * 0.2f, STORY_HEIGHT * 0.16f, STORY_WIDTH * 0.78f, orbTop)

        val orbBottom = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                STORY_WIDTH * 0.86f, STORY_HEIGHT * 0.84f, STORY_WIDTH * 0.72f,
                intArrayOf(LyricCardGenerator.withAlpha(LyricCardGenerator.blendColor(accent, Color.WHITE, 0.2f), 85), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(STORY_WIDTH * 0.86f, STORY_HEIGHT * 0.84f, STORY_WIDTH * 0.72f, orbBottom)
    }

    private fun drawStoryTopBadge(canvas: Canvas, text: String, y: Float, accent: Int) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.16f
            textAlign = Paint.Align.CENTER
        }
        val textW = textPaint.measureText(text)
        val pillW = textW + 78f
        val pillRect = RectF(STORY_WIDTH * 0.5f - pillW / 2f, y - 34f, STORY_WIDTH * 0.5f + pillW / 2f, y + 22f)
        val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(66, 255, 255, 255) }
        canvas.drawRoundRect(pillRect, 40f, 40f, pillBg)

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LyricCardGenerator.blendColor(accent, Color.WHITE, 0.35f) }
        canvas.drawCircle(pillRect.left + 26f, pillRect.centerY(), 6f, dotPaint)

        canvas.drawText(text, STORY_WIDTH * 0.5f + 8f, pillRect.centerY() + 9f, textPaint)
    }

    private fun drawStoryFooterBrand(canvas: Canvas, y: Float) {
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(215, 255, 255, 255)
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.22f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("KOPLINGFY", STORY_WIDTH * 0.5f, y, brandPaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(145, 255, 255, 255)
            textSize = 20f
            typeface = Typeface.SANS_SERIF
            letterSpacing = 0.08f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Dengarkan musik favoritmu", STORY_WIDTH * 0.5f, y + 34f, subPaint)
    }
}
