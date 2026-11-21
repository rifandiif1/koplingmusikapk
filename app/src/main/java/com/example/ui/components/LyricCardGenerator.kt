package com.example.ui.components

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.model.Track
import java.io.IOException

enum class LyricCardTheme(val label: String) {
    EDITORIAL_GLASS("Kaca Estetik"),
    WARM_POLAROID("Polaroid"),
    NOIR_MINIMAL("Noir Minimal"),
    COLOR_POP("Pop Warna")
}

enum class LyricCardRatio(
    val label: String,
    val widthPx: Int,
    val heightPx: Int
) {
    STORY_9_16("Story 9:16", 1080, 1920),
    POST_4_5("Feed 4:5", 1080, 1350)
}

/**
 * Generates high-resolution, aesthetic Lyric Quote Cards (PNG) in multiple editorial themes
 * and saves them directly to Pictures/Koplingfy via zero-permission MediaStore.
 */
object LyricCardGenerator {

    suspend fun saveLyricCardToGallery(
        context: Context,
        track: Track,
        selectedLines: List<String>,
        accentColorInt: Int,
        translatedLines: List<String> = emptyList(),
        theme: LyricCardTheme = LyricCardTheme.EDITORIAL_GLASS,
        ratio: LyricCardRatio = LyricCardRatio.STORY_9_16
    ): Result<Uri> {
        return runCatching {
            val cleanedLines = selectedLines.map { it.trim() }.filter { it.isNotEmpty() }.take(4)
            require(cleanedLines.isNotEmpty()) { "Pilih minimal 1 baris lirik" }
            val alignedTranslations = if (translatedLines.isNotEmpty()) {
                cleanedLines.indices.map { i ->
                    translatedLines.getOrNull(i)?.trim().orEmpty()
                }
            } else {
                emptyList()
            }

            val artworkBitmap = loadArtworkBitmap(context, track.artworkUrl)
            val cardBitmap = renderCardBitmap(
                track = track,
                lines = cleanedLines,
                translatedLines = alignedTranslations,
                artwork = artworkBitmap,
                accentColor = accentColorInt,
                theme = theme,
                ratio = ratio
            )

            try {
                saveBitmapToMediaStore(
                    context = context,
                    bitmap = cardBitmap,
                    filePrefix = "Koplingfy_Lyric_${theme.name.lowercase()}"
                )
            } finally {
                cardBitmap.recycle()
            }
        }
    }

    internal suspend fun loadArtworkBitmap(context: Context, url: String?, sizePx: Int = 640): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(sizePx, sizePx)
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                result.drawable.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            } else null
        }.getOrNull()
    }

    private fun renderCardBitmap(
        track: Track,
        lines: List<String>,
        translatedLines: List<String>,
        artwork: Bitmap?,
        accentColor: Int,
        theme: LyricCardTheme,
        ratio: LyricCardRatio
    ): Bitmap {
        val width = ratio.widthPx
        val height = ratio.heightPx
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val isStory = ratio == LyricCardRatio.STORY_9_16
        val safeAccent = ensureVibrantAccent(accentColor)
        val darkAccent = blendColor(0xFF0A0B10.toInt(), safeAccent, 0.36f)
        val deepBase = blendColor(0xFF07080C.toInt(), safeAccent, 0.14f)

        when (theme) {
            LyricCardTheme.EDITORIAL_GLASS -> drawEditorialGlassCard(
                canvas, width, height, isStory, track, lines, translatedLines, artwork, safeAccent, darkAccent, deepBase
            )
            LyricCardTheme.WARM_POLAROID -> drawWarmPolaroidCard(
                canvas, width, height, isStory, track, lines, translatedLines, artwork, safeAccent, darkAccent
            )
            LyricCardTheme.NOIR_MINIMAL -> drawNoirMinimalCard(
                canvas, width, height, isStory, track, lines, translatedLines, artwork, safeAccent
            )
            LyricCardTheme.COLOR_POP -> drawColorPopCard(
                canvas, width, height, isStory, track, lines, translatedLines, artwork, safeAccent
            )
        }

        return bitmap
    }

    private fun drawEditorialGlassCard(
        canvas: Canvas,
        width: Int,
        height: Int,
        isStory: Boolean,
        track: Track,
        lines: List<String>,
        translatedLines: List<String>,
        artwork: Bitmap?,
        accent: Int,
        darkAccent: Int,
        deepBase: Int
    ) {
        // 1. Rich Atmospheric Backdrop
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(darkAccent, deepBase, blendColor(0xFF050608.toInt(), accent, 0.22f)),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Subtle blurred ambient artwork wash if available
        if (artwork != null) {
            val washPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = 44
            }
            drawCenterCroppedBitmap(canvas, artwork, RectF(0f, 0f, width.toFloat(), height.toFloat()), washPaint)
            val scrimPaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    intArrayOf(
                        Color.argb(135, 8, 9, 14),
                        Color.argb(175, 7, 8, 12),
                        Color.argb(225, 5, 6, 9)
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        }

        // Top-left & bottom-right ambient orbs
        val orb1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.16f, height * 0.18f, width * 0.72f,
                intArrayOf(withAlpha(accent, 118), withAlpha(accent, 0)),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.16f, height * 0.18f, width * 0.72f, orb1)

        val orb2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.88f, height * 0.82f, width * 0.65f,
                intArrayOf(withAlpha(blendColor(accent, Color.WHITE, 0.25f), 85), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.88f, height * 0.82f, width * 0.65f, orb2)

        // 2. Centered Frosted Glass Card
        val horizontalMargin = 72f
        val topMargin = if (isStory) 250f else 90f
        val bottomMargin = if (isStory) 240f else 90f
        val cardRect = RectF(horizontalMargin, topMargin, width - horizontalMargin, height - bottomMargin)

        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                intArrayOf(
                    Color.argb(56, 255, 255, 255),
                    Color.argb(28, 255, 255, 255),
                    Color.argb(44, 15, 16, 24)
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardRect, 54f, 54f, cardBgPaint)

        val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            shader = LinearGradient(
                cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                Color.argb(120, 255, 255, 255),
                Color.argb(38, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardRect, 54f, 54f, cardStroke)

        // Inner layout
        val innerPad = 64f
        val contentLeft = cardRect.left + innerPad
        val contentRight = cardRect.right - innerPad
        val contentWidth = (contentRight - contentLeft).toInt()

        // Track header inside card
        val artSize = 128f
        val headerTop = cardRect.top + 64f
        drawRoundedArtwork(
            canvas = canvas,
            artwork = artwork,
            rect = RectF(contentLeft, headerTop, contentLeft + artSize, headerTop + artSize),
            cornerRadius = 26f,
            fallbackColor = accent
        )

        val textStartX = contentLeft + artSize + 30f
        val titleWidth = (contentRight - textStartX).toInt().coerceAtLeast(180)
        drawSingleOrTwoLineText(
            canvas = canvas,
            text = track.title,
            x = textStartX,
            y = headerTop + 14f,
            maxWidth = titleWidth,
            textSize = 36f,
            color = Color.WHITE,
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
            maxLines = 2
        )
        drawSingleOrTwoLineText(
            canvas = canvas,
            text = track.artist,
            x = textStartX,
            y = headerTop + 74f,
            maxWidth = titleWidth,
            textSize = 26f,
            color = Color.argb(200, 255, 255, 255),
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL),
            maxLines = 1
        )

        // Subtle divider + quote mark
        val quoteY = headerTop + artSize + 52f
        val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(blendColor(accent, Color.WHITE, 0.45f), 210)
            textSize = 96f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText("“", contentLeft - 6f, quoteY + 52f, quotePaint)

        // Lyrics block
        val lyricsTop = quoteY + 70f
        val lyricsBottom = cardRect.bottom - 140f
        drawLyricsBlock(
            canvas = canvas,
            lines = lines,
            translatedLines = translatedLines,
            left = contentLeft,
            top = lyricsTop,
            bottom = lyricsBottom,
            maxWidth = contentWidth,
            primaryColor = Color.WHITE,
            translationColor = Color.argb(195, 232, 236, 245),
            lineTypeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
            baseTextSize = if (isStory) 50f else 44f
        )

        // Card Footer: waveform + Koplingfy badge
        val footerY = cardRect.bottom - 76f
        drawMiniWaveform(
            canvas = canvas,
            startX = contentLeft,
            centerY = footerY,
            width = 160f,
            barCount = 16,
            activeColor = blendColor(accent, Color.WHITE, 0.4f),
            inactiveColor = Color.argb(75, 255, 255, 255)
        )
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(215, 255, 255, 255)
            textSize = 25f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.14f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("KOPLINGFY", contentRight, footerY + 9f, brandPaint)
    }

    private fun drawWarmPolaroidCard(
        canvas: Canvas,
        width: Int,
        height: Int,
        isStory: Boolean,
        track: Track,
        lines: List<String>,
        translatedLines: List<String>,
        artwork: Bitmap?,
        accent: Int,
        darkAccent: Int
    ) {
        // Warm moody studio backdrop
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(
                    blendColor(0xFF1A1614.toInt(), darkAccent, 0.42f),
                    0xFF110F0E.toInt(),
                    blendColor(0xFF181412.toInt(), accent, 0.20f)
                ),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Soft warm spotlight behind polaroid
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.5f, height * 0.46f, width * 0.7f,
                intArrayOf(withAlpha(accent, 95), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.5f, height * 0.46f, width * 0.7f, glow)

        canvas.save()
        canvas.rotate(-1.6f, width * 0.5f, height * 0.5f)

        val marginX = 92f
        val topY = if (isStory) 220f else 84f
        val bottomY = if (isStory) height - 210f else height - 84f
        val paperRect = RectF(marginX, topY, width - marginX, bottomY)

        // Drop shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 0, 0, 0)
        }
        canvas.drawRoundRect(
            RectF(paperRect.left + 16f, paperRect.top + 26f, paperRect.right + 16f, paperRect.bottom + 26f),
            20f, 20f, shadowPaint
        )

        // Warm off-white Polaroid film paper
        val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF7F2E8.toInt()
        }
        canvas.drawRoundRect(paperRect, 18f, 18f, paperPaint)

        // Decorative translucent masking tape at top center
        val tapeRect = RectF(width * 0.5f - 105f, paperRect.top - 26f, width * 0.5f + 105f, paperRect.top + 32f)
        val tapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(155, 224, 211, 186)
        }
        canvas.drawRoundRect(tapeRect, 6f, 6f, tapePaint)

        // Dark photo well inside top portion of Polaroid
        val wellPad = 48f
        val wellTop = paperRect.top + 56f
        val wellHeight = if (isStory) 760f else 560f
        val wellRect = RectF(paperRect.left + wellPad, wellTop, paperRect.right - wellPad, wellTop + wellHeight)

        val wellBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                wellRect.left, wellRect.top, wellRect.right, wellRect.bottom,
                blendColor(0xFF141418.toInt(), accent, 0.35f),
                0xFF0E0E12.toInt(),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(wellRect, 10f, 10f, wellBg)

        // Artwork faded subtly inside upper corner or full washed film
        if (artwork != null) {
            val filmArtPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = 72
            }
            canvas.save()
            val clipPath = Path().apply { addRoundRect(wellRect, 10f, 10f, Path.Direction.CW) }
            canvas.clipPath(clipPath)
            drawCenterCroppedBitmap(canvas, artwork, wellRect, filmArtPaint)
            canvas.restore()
        }

        // Lyrics inside the dark film well
        val lyricsPad = 46f
        drawLyricsBlock(
            canvas = canvas,
            lines = lines,
            translatedLines = translatedLines,
            left = wellRect.left + lyricsPad,
            top = wellRect.top + 54f,
            bottom = wellRect.bottom - 52f,
            maxWidth = (wellRect.width() - lyricsPad * 2f).toInt(),
            primaryColor = 0xFFFAF7F0.toInt(),
            translationColor = Color.argb(205, 235, 228, 215),
            lineTypeface = Typeface.create(Typeface.SERIF, Typeface.BOLD),
            baseTextSize = if (isStory) 45f else 39f
        )

        // Bottom white Polaroid chin: mini cover + song caption + Koplingfy film stamp
        val chinTop = wellRect.bottom + 42f
        val miniArtSize = 112f
        drawRoundedArtwork(
            canvas = canvas,
            artwork = artwork,
            rect = RectF(wellRect.left, chinTop, wellRect.left + miniArtSize, chinTop + miniArtSize),
            cornerRadius = 14f,
            fallbackColor = accent
        )

        val captionX = wellRect.left + miniArtSize + 26f
        val captionMaxW = (wellRect.right - captionX).toInt().coerceAtLeast(160)
        drawSingleOrTwoLineText(
            canvas = canvas,
            text = track.title,
            x = captionX,
            y = chinTop + 8f,
            maxWidth = captionMaxW,
            textSize = 35f,
            color = 0xFF1D1B18.toInt(),
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD),
            maxLines = 2
        )
        drawSingleOrTwoLineText(
            canvas = canvas,
            text = track.artist,
            x = captionX,
            y = chinTop + 66f,
            maxWidth = captionMaxW,
            textSize = 25f,
            color = 0xFF6B655D.toInt(),
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL),
            maxLines = 1
        )

        // Film date / brand stamp at bottom of Polaroid chin
        val stampY = paperRect.bottom - 48f
        val stampLeft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF9C9386.toInt()
            textSize = 22f
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.08f
        }
        canvas.drawText("LYRIC ARCHIVE • 35MM", wellRect.left, stampY, stampLeft)

        val stampRight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF3A352E.toInt()
            textSize = 23f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.14f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("KOPLINGFY", wellRect.right, stampY, stampRight)

        canvas.restore()
    }

    private fun drawNoirMinimalCard(
        canvas: Canvas,
        width: Int,
        height: Int,
        isStory: Boolean,
        track: Track,
        lines: List<String>,
        translatedLines: List<String>,
        artwork: Bitmap?,
        accent: Int
    ) {
        // Pure OLED charcoal with ultra-clean Swiss typography layout
        canvas.drawColor(0xFF070709.toInt())

        // Crisp subtle hairline border frame
        val frameMargin = 56f
        val frameRect = RectF(frameMargin, frameMargin, width - frameMargin, height - frameMargin)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(42, 255, 255, 255)
        }
        canvas.drawRoundRect(frameRect, 36f, 36f, borderPaint)

        val padX = 112f
        val contentWidth = (width - padX * 2).toInt()
        val topSectionY = if (isStory) 220f else 130f

        // Accent pill label
        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = blendColor(accent, Color.WHITE, 0.3f)
            textSize = 24f
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.18f
        }
        canvas.drawText("LYRIC QUOTE", padX, topSectionY, tagPaint)

        // Vertical accent bar next to lyrics
        val lyricsTop = topSectionY + 80f
        val lyricsBottom = height - (if (isStory) 420f else 310f)
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
        }
        canvas.drawRoundRect(
            RectF(padX, lyricsTop + 8f, padX + 8f, (lyricsTop + 260f).coerceAtMost(lyricsBottom)),
            4f, 4f, barPaint
        )

        drawLyricsBlock(
            canvas = canvas,
            lines = lines,
            translatedLines = translatedLines,
            left = padX + 38f,
            top = lyricsTop,
            bottom = lyricsBottom,
            maxWidth = contentWidth - 38,
            primaryColor = 0xFFF4F4F6.toInt(),
            translationColor = Color.argb(175, 188, 192, 204),
            lineTypeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
            baseTextSize = if (isStory) 52f else 45f
        )

        // Bottom hairline divider
        val divY = height - (if (isStory) 340f else 255f)
        val divPaint = Paint().apply {
            color = Color.argb(38, 255, 255, 255)
            strokeWidth = 2f
        }
        canvas.drawLine(padX, divY, width - padX, divY, divPaint)

        // Bottom track row
        val artY = divY + 46f
        val artSize = 116f
        drawRoundedArtwork(
            canvas = canvas,
            artwork = artwork,
            rect = RectF(padX, artY, padX + artSize, artY + artSize),
            cornerRadius = 18f,
            fallbackColor = accent
        )

        val infoX = padX + artSize + 28f
        val infoMaxW = (width - padX - infoX).toInt().coerceAtLeast(160)
        drawSingleOrTwoLineText(
            canvas = canvas,
            text = track.title,
            x = infoX,
            y = artY + 12f,
            maxWidth = infoMaxW,
            textSize = 35f,
            color = Color.WHITE,
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
            maxLines = 2
        )
        drawSingleOrTwoLineText(
            canvas = canvas,
            text = track.artist,
            x = infoX,
            y = artY + 68f,
            maxWidth = infoMaxW,
            textSize = 25f,
            color = Color.argb(175, 255, 255, 255),
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL),
            maxLines = 1
        )

        // Minimal watermark
        val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 255, 255, 255)
            textSize = 22f
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.2f
        }
        canvas.drawText("KOPLINGFY", padX, height - 104f, wmPaint)
    }

    private fun drawColorPopCard(
        canvas: Canvas,
        width: Int,
        height: Int,
        isStory: Boolean,
        track: Track,
        lines: List<String>,
        translatedLines: List<String>,
        artwork: Bitmap?,
        accent: Int
    ) {
        // Bold Spotify/Apple-style saturated editorial gradient card
        val topColor = blendColor(accent, 0xFF161820.toInt(), 0.24f)
        val bottomColor = blendColor(accent, 0xFF0A0A10.toInt(), 0.56f)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                topColor, bottomColor, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Bright bold inner card
        val pad = 74f
        val topY = if (isStory) 240f else 84f
        val bottomY = if (isStory) height - 240f else height - 84f
        val popRect = RectF(pad, topY, width - pad, bottomY)

        val popBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                popRect.left, popRect.top, popRect.right, popRect.bottom,
                blendColor(accent, Color.WHITE, 0.08f),
                blendColor(accent, 0xFF12121A.toInt(), 0.34f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(popRect, 52f, 52f, popBg)

        // Subtle highlight ring
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.argb(72, 255, 255, 255)
        }
        canvas.drawRoundRect(popRect, 52f, 52f, stroke)

        val innerLeft = popRect.left + 64f
        val innerRight = popRect.right - 64f
        val innerWidth = (innerRight - innerLeft).toInt()

        // Bold large lyrics at top-center
        val lyricsTop = popRect.top + 86f
        val lyricsBottom = popRect.bottom - 270f
        drawLyricsBlock(
            canvas = canvas,
            lines = lines,
            translatedLines = translatedLines,
            left = innerLeft,
            top = lyricsTop,
            bottom = lyricsBottom,
            maxWidth = innerWidth,
            primaryColor = Color.WHITE,
            translationColor = Color.argb(215, 255, 255, 255),
            lineTypeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
            baseTextSize = if (isStory) 54f else 46f
        )

        // Bottom pill with artwork + track + Koplingfy
        val pillRect = RectF(innerLeft, popRect.bottom - 210f, innerRight, popRect.bottom - 62f)
        val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(72, 0, 0, 0)
        }
        canvas.drawRoundRect(pillRect, 34f, 34f, pillBg)

        val artSize = 104f
        val artLeft = pillRect.left + 22f
        val artTop = pillRect.centerY() - artSize / 2f
        drawRoundedArtwork(
            canvas = canvas,
            artwork = artwork,
            rect = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize),
            cornerRadius = 20f,
            fallbackColor = Color.BLACK
        )

        val metaX = artLeft + artSize + 24f
        val metaW = (pillRect.right - 24f - metaX).toInt().coerceAtLeast(150)
        drawSingleOrTwoLineText(
            canvas = canvas,
            text = track.title,
            x = metaX,
            y = artTop + 8f,
            maxWidth = metaW,
            textSize = 32f,
            color = Color.WHITE,
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
            maxLines = 1
        )
        drawSingleOrTwoLineText(
            canvas = canvas,
            text = "${track.artist} • KOPLINGFY",
            x = metaX,
            y = artTop + 56f,
            maxWidth = metaW,
            textSize = 23f,
            color = Color.argb(210, 255, 255, 255),
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL),
            maxLines = 1
        )
    }

    private fun drawLyricsBlock(
        canvas: Canvas,
        lines: List<String>,
        translatedLines: List<String>,
        left: Float,
        top: Float,
        bottom: Float,
        maxWidth: Int,
        primaryColor: Int,
        translationColor: Int,
        lineTypeface: Typeface,
        baseTextSize: Float
    ) {
        val availableHeight = (bottom - top).coerceAtLeast(220f)
        val hasTranslations = translatedLines.any { it.isNotBlank() }
        val totalChars = lines.sumOf { it.length } + translatedLines.sumOf { it.length }

        var primarySize = when {
            lines.size >= 4 || totalChars > 190 -> baseTextSize * 0.78f
            lines.size == 3 || totalChars > 130 -> baseTextSize * 0.88f
            else -> baseTextSize
        }
        if (hasTranslations) primarySize *= 0.88f

        // Iteratively shrink if needed to guarantee zero overflow
        var renderedLayouts: List<Pair<StaticLayout, StaticLayout?>> = emptyList()
        var blockHeight = 0f

        for (attempt in 0..4) {
            val mainPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = primaryColor
                textSize = primarySize
                typeface = lineTypeface
            }
            val transPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = translationColor
                textSize = (primarySize * 0.60f).coerceAtLeast(22f)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            }

            val items = mutableListOf<Pair<StaticLayout, StaticLayout?>>()
            var totalH = 0f
            lines.forEachIndexed { idx, line ->
                val mainLayout = buildStaticLayout(line, mainPaint, maxWidth, 1.15f)
                totalH += mainLayout.height
                val trans = translatedLines.getOrNull(idx)?.takeIf {
                    it.isNotBlank() && !it.equals(line, ignoreCase = true)
                }
                val transLayout = if (trans != null) {
                    val tl = buildStaticLayout(trans, transPaint, maxWidth, 1.15f)
                    totalH += 8f + tl.height
                    tl
                } else null
                if (idx < lines.lastIndex) {
                    totalH += if (hasTranslations) 32f else 26f
                }
                items.add(mainLayout to transLayout)
            }

            renderedLayouts = items
            blockHeight = totalH
            if (totalH <= availableHeight || primarySize <= 24f) break
            primarySize *= 0.88f
        }

        // Vertically center nicely inside the allocated lyrics zone
        var cursorY = top + ((availableHeight - blockHeight).coerceAtLeast(0f) * 0.42f)
        renderedLayouts.forEachIndexed { idx, (mainLayout, transLayout) ->
            canvas.save()
            canvas.translate(left, cursorY)
            mainLayout.draw(canvas)
            canvas.restore()
            cursorY += mainLayout.height

            if (transLayout != null) {
                cursorY += 8f
                canvas.save()
                canvas.translate(left, cursorY)
                transLayout.draw(canvas)
                canvas.restore()
                cursorY += transLayout.height
            }

            if (idx < renderedLayouts.lastIndex) {
                cursorY += if (hasTranslations) 32f else 26f
            }
        }
    }

    internal fun buildStaticLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        spacingMult: Float = 1.15f
    ): StaticLayout {
        val safeWidth = width.coerceAtLeast(100)
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, safeWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, spacingMult)
            .setIncludePad(false)
            .build()
    }

    internal fun drawSingleOrTwoLineText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Int,
        textSize: Float,
        color: Int,
        typeface: Typeface,
        maxLines: Int = 1
    ) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = textSize
            this.typeface = typeface
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth.coerceAtLeast(80))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    internal fun drawRoundedArtwork(
        canvas: Canvas,
        artwork: Bitmap?,
        rect: RectF,
        cornerRadius: Float,
        fallbackColor: Int
    ) {
        canvas.save()
        val path = Path().apply { addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW) }
        canvas.clipPath(path)
        if (artwork != null) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            drawCenterCroppedBitmap(canvas, artwork, rect, paint)
        } else {
            val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    rect.left, rect.top, rect.right, rect.bottom,
                    blendColor(fallbackColor, Color.WHITE, 0.12f),
                    blendColor(fallbackColor, Color.BLACK, 0.45f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(rect, fallbackPaint)
            val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(180, 255, 255, 255)
                textSize = rect.width() * 0.42f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText("♪", rect.centerX(), rect.centerY() + rect.width() * 0.14f, notePaint)
        }
        canvas.restore()
    }

    internal fun drawCenterCroppedBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        dest: RectF,
        paint: Paint
    ) {
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        val dw = dest.width()
        val dh = dest.height()
        val scale = maxOf(dw / bw, dh / bh)
        val scaledW = dw / scale
        val scaledH = dh / scale
        val srcLeft = ((bw - scaledW) / 2f).toInt().coerceAtLeast(0)
        val srcTop = ((bh - scaledH) / 2f).toInt().coerceAtLeast(0)
        val src = Rect(
            srcLeft,
            srcTop,
            (srcLeft + scaledW).toInt().coerceAtMost(bitmap.width),
            (srcTop + scaledH).toInt().coerceAtMost(bitmap.height)
        )
        canvas.drawBitmap(bitmap, src, dest, paint)
    }

    internal fun drawMiniWaveform(
        canvas: Canvas,
        startX: Float,
        centerY: Float,
        width: Float,
        barCount: Int,
        activeColor: Int,
        inactiveColor: Int,
        progressFraction: Float = 0.62f,
        maxBarHeight: Float = 32f
    ) {
        val heights = floatArrayOf(0.35f, 0.68f, 0.95f, 0.52f, 0.85f, 1.0f, 0.44f, 0.78f, 0.92f, 0.58f, 0.40f, 0.88f, 0.66f, 0.48f, 0.74f, 0.36f)
        val step = width / barCount.coerceAtLeast(1)
        val barWidth = (step * 0.52f).coerceAtLeast(4f)
        val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = activeColor }
        val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = inactiveColor }

        for (i in 0 until barCount) {
            val frac = i.toFloat() / barCount.toFloat()
            val h = (heights[i % heights.size] * maxBarHeight).coerceAtLeast(8f)
            val x = startX + i * step
            val rect = RectF(x, centerY - h / 2f, x + barWidth, centerY + h / 2f)
            canvas.drawRoundRect(rect, barWidth / 2f, barWidth / 2f, if (frac <= progressFraction) activePaint else inactivePaint)
        }
    }

    internal fun saveBitmapToMediaStore(
        context: Context,
        bitmap: Bitmap,
        filePrefix: String
    ): Uri {
        val filename = "${filePrefix}_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Koplingfy")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Gagal mengakses MediaStore Galeri")
        resolver.openOutputStream(uri)?.use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 96, out)) {
                throw IOException("Gagal mengompres gambar PNG")
            }
        } ?: throw IOException("Gagal membuka output stream gambar")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val finishValues = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(uri, finishValues, null, null)
        }
        return uri
    }

    internal fun ensureVibrantAccent(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        if (hsv[1] < 0.30f) hsv[1] = 0.55f
        if (hsv[2] < 0.45f) hsv[2] = 0.72f
        return Color.HSVToColor(hsv)
    }

    internal fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }

    internal fun blendColor(c1: Int, c2: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val r = (Color.red(c1) * inv + Color.red(c2) * ratio).toInt().coerceIn(0, 255)
        val g = (Color.green(c1) * inv + Color.green(c2) * ratio).toInt().coerceIn(0, 255)
        val b = (Color.blue(c1) * inv + Color.blue(c2) * ratio).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
