package com.example.ui.components

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.model.ListeningStats

/**
 * Renders a high-contrast shareable Koplingfy Recap poster card PNG and saves it to Gallery via zero-permission MediaStore.
 */
object RecapCardGenerator {

    fun computePersona(stats: ListeningStats): Pair<String, String> {
        return when {
            stats.totalMinutes >= 600 -> "Audiophile Sejati" to "Musik adalah bahan bakar utamamu setiap hari."
            stats.uniqueTracks >= 25 -> "Penjelajah Genre" to "Selalu haus menemukan warna musik baru."
            stats.totalPlays >= 20 -> "Pengulang Favorit" to "Setia pada lagu-lagu pilihan terbaikmu."
            stats.totalPlays >= 5 -> "Pendengar Santai" to "Menikmati ritme harian dengan kualitas Hi-Fi."
            else -> "Pendengar Baru" to "Perjalanan musikmu di Koplingfy baru saja dimulai!"
        }
    }

    fun renderRecapBitmap(stats: ListeningStats): Bitmap {
        val width = 1080
        val height = 1480
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Obsidian Studio gradient background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(Color.parseColor("#0B1618"), Color.parseColor("#090B10"), Color.parseColor("#131024")),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Top accent glow strip
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), 0f,
                Color.parseColor("#00E5A0"),
                Color.parseColor("#8B7BFF"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(RectF(68f, 72f, 320f, 84f), 8f, 8f, accentPaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5A0")
            textSize = 32f
            letterSpacing = 0.14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("KOPLINGFY RECAP", 68f, 146f, labelPaint)

        val (personaTitle, personaDesc) = computePersona(stats)

        val heroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 72f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(personaTitle, 68f, 240f, heroPaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B6C2CF")
            textSize = 34f
        }
        canvas.drawText(personaDesc.take(54), 68f, 296f, subPaint)

        // 3 Metric Cards
        val cardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#141923")
        }
        val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2A3446")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        val metrics = listOf(
            Triple("${stats.totalPlays}", "Total Putar", "#00E5A0"),
            Triple("${stats.totalMinutes}m", "Menit Aktif", "#FFB547"),
            Triple("${stats.uniqueTracks}", "Lagu Unik", "#8B7BFF")
        )
        val cardWidth = (width - 136f - 40f) / 3f
        metrics.forEachIndexed { idx, (value, title, hex) ->
            val left = 68f + idx * (cardWidth + 20f)
            val rect = RectF(left, 360f, left + cardWidth, 540f)
            canvas.drawRoundRect(rect, 32f, 32f, cardBg)
            canvas.drawRoundRect(rect, 32f, 32f, cardStroke)

            val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(hex)
                textSize = 62f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(value, rect.centerX(), rect.top + 96f, valPaint)

            val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#9BA7B8")
                textSize = 28f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(title, rect.centerX(), rect.top + 146f, capPaint)
        }

        // Top Artist Card
        val topArtist = stats.topArtists.firstOrNull()
        val artistRect = RectF(68f, 586f, width - 68f, 766f)
        canvas.drawRoundRect(artistRect, 34f, 34f, cardBg)
        canvas.drawRoundRect(artistRect, 34f, 34f, cardStroke)
        canvas.drawText("ARTIS #1 KAMU", artistRect.left + 40f, artistRect.top + 62f, labelPaint)

        val artistTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 52f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val artistLabel = topArtist?.artist?.take(28) ?: "Belum ada data"
        canvas.drawText(artistLabel, artistRect.left + 40f, artistRect.top + 128f, artistTitlePaint)

        // Top Tracks Card
        val tracksRect = RectF(68f, 806f, width - 68f, 1300f)
        canvas.drawRoundRect(tracksRect, 34f, 34f, cardBg)
        canvas.drawRoundRect(tracksRect, 34f, 34f, cardStroke)
        canvas.drawText("LAGU TERFAVORIT", tracksRect.left + 40f, tracksRect.top + 64f, labelPaint)

        val trackTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val trackArtistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9BA7B8")
            textSize = 28f
        }
        val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5A0")
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val top3 = stats.topTracks.take(4)
        if (top3.isEmpty()) {
            canvas.drawText("Putar beberapa lagu untuk mengisi Recap-mu!", tracksRect.left + 40f, tracksRect.top + 150f, subPaint)
        } else {
            top3.forEachIndexed { idx, item ->
                val yBase = tracksRect.top + 148f + idx * 96f
                canvas.drawText("#${idx + 1}", tracksRect.left + 40f, yBase, rankPaint)
                canvas.drawText(item.track.title.take(30), tracksRect.left + 122f, yBase - 6f, trackTitlePaint)
                canvas.drawText("${item.track.artist.take(32)} • ${item.playCount}x diputar", tracksRect.left + 122f, yBase + 32f, trackArtistPaint)
            }
        }

        // Footer watermark
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6D7B8F")
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Dibuat dengan Koplingfy Studio • Hi-Fi Music", width / 2f, height - 64f, footerPaint)

        return bitmap
    }

    fun saveRecapToGallery(context: Context, stats: ListeningStats): Result<String> {
        return runCatching {
            val bitmap = renderRecapBitmap(stats)
            val fileName = "Koplingfy_Recap_${System.currentTimeMillis()}.png"
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
                ?: error("Gagal membuat file gambar di Galeri")
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: error("Gagal menulis gambar Recap")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            fileName
        }
    }
}
