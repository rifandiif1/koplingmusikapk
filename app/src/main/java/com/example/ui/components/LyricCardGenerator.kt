package com.example.ui.components

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.model.Track

/**
 * Generates an aesthetic 1080x1350 Koplingfy Lyric Quote Card PNG from 1..4 selected lyric lines
 * and saves it to the user's Gallery via zero-permission MediaStore.
 */
object LyricCardGenerator {

    suspend fun saveLyricCardToGallery(
        context: Context,
        track: Track,
        selectedLines: List<String>,
        accentColorInt: Int
    ): Result<Uri> {
        return runCatching {
            val cleanedLines = selectedLines.map { it.trim() }.filter { it.isNotEmpty() }.take(4)
            require(cleanedLines.isNotEmpty()) { "Pilih minimal 1 baris lirik terlebih dahulu." }

            val artBitmap = loadArtworkBitmap(context, track.artworkUrl)
            val bitmap = renderLyricCardBitmap(
                track = track,
                lines = cleanedLines,
                accentColorInt = accentColorInt,
                artwork = artBitmap
            )

            val fileName = "Koplingfy_Lyric_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Koplingfy")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Gagal membuat file kartu lirik di Galeri")
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: error("Gagal menyimpan kartu lirik")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            bitmap.recycle()
            uri
        }
    }

    private suspend fun loadArtworkBitmap(context: Context, artworkUrl: String?): Bitmap? {
        if (artworkUrl.isNullOrBlank()) return null
        return runCatching {
            val loader = ImageLoader(context)
            val req = ImageRequest.Builder(context)
                .data(artworkUrl)
                .allowHardware(false)
                .size(320)
                .build()
            val result = loader.execute(req)
            if (result is SuccessResult) {
                result.drawable.toBitmap(240, 240, Bitmap.Config.ARGB_8888)
            } else null
        }.getOrNull()
    }

    private fun renderLyricCardBitmap(
        track: Track,
        lines: List<String>,
        accentColorInt: Int,
        artwork: Bitmap?
    ): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Blend accent into rich Obsidian Studio gradient
        val topTint = blendColor(Color.parseColor("#0B0E14"), accentColorInt, 0.34f)
        val midTint = Color.parseColor("#0E1118")
        val bottomTint = blendColor(Color.parseColor("#0A0B0F"), accentColorInt, 0.22f)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(topTint, midTint, bottomTint),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Subtle studio card frame
        val cardRect = RectF(64f, 84f, width - 64f, height - 110f)
        val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 17, 21, 30)
        }
        val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, Color.red(accentColorInt), Color.green(accentColorInt), Color.blue(accentColorInt))
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        canvas.drawRoundRect(cardRect, 48f, 48f, cardFill)
        canvas.drawRoundRect(cardRect, 48f, 48f, cardBorder)

        // Header: Artwork + Title + Artist
        val artLeft = cardRect.left + 54f
        val artTop = cardRect.top + 58f
        val artSize = 144f
        val artRect = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)
        if (artwork != null) {
            val shader = BitmapShader(artwork, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val matrix = Matrix().apply {
                val scaleX = artSize / artwork.width.toFloat()
                val scaleY = artSize / artwork.height.toFloat()
                setScale(scaleX, scaleY)
                postTranslate(artLeft, artTop)
            }
            shader.setLocalMatrix(matrix)
            val artPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
            canvas.drawRoundRect(artRect, 28f, 28f, artPaint)
        } else {
            val fallbackArt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColorInt }
            canvas.drawRoundRect(artRect, 28f, 28f, fallbackArt)
        }

        val textStartX = artRect.right + 34f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B5C0D0")
            textSize = 33f
        }
        val maxHeaderWidth = cardRect.right - textStartX - 44f
        canvas.drawText(ellipsize(track.title, titlePaint, maxHeaderWidth), textStartX, artTop + 62f, titlePaint)
        canvas.drawText(ellipsize(track.artist, artistPaint, maxHeaderWidth), textStartX, artTop + 112f, artistPaint)

        // Accent divider bar
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColorInt }
        canvas.drawRoundRect(RectF(artLeft, artRect.bottom + 46f, artLeft + 116f, artRect.bottom + 56f), 6f, 6f, barPaint)

        // Opening quotation mark
        val quoteMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(145, Color.red(accentColorInt), Color.green(accentColorInt), Color.blue(accentColorInt))
            textSize = 110f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText("“", artLeft - 6f, artRect.bottom + 150f, quoteMarkPaint)

        // Wrapped lyric lines
        val lyricTextSize = when {
            lines.sumOf { it.length } > 170 -> 44f
            lines.size >= 4 -> 50f
            else -> 58f
        }
        val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = lyricTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val maxLyricWidth = cardRect.width() - 112f
        val lineSpacing = lyricTextSize * 1.38f
        var cursorY = artRect.bottom + 216f
        val bottomLimit = cardRect.bottom - 120f

        for (rawLine in lines) {
            val wrapped = wrapText(rawLine, lyricPaint, maxLyricWidth)
            for (seg in wrapped) {
                if (cursorY > bottomLimit) break
                canvas.drawText(seg, artLeft, cursorY, lyricPaint)
                cursorY += lineSpacing
            }
            cursorY += 18f
        }

        // Bottom Studio Pill inside card
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColorInt
            textSize = 28f
            letterSpacing = 0.14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("KOPLINGFY • LYRIC CARD", artLeft, cardRect.bottom - 48f, badgePaint)

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7D8A9E")
            textSize = 26f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Dengarkan penuh di Koplingfy Studio Hi-Fi", width / 2f, height - 48f, footerPaint)

        return bitmap
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) result.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return text.substring(0, end) + "…"
    }

    private fun blendColor(base: Int, tint: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val r = (Color.red(base) * inv + Color.red(tint) * ratio).toInt().coerceIn(0, 255)
        val g = (Color.green(base) * inv + Color.green(tint) * ratio).toInt().coerceIn(0, 255)
        val b = (Color.blue(base) * inv + Color.blue(tint) * ratio).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
