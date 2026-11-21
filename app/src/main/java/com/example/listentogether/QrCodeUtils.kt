package com.example.listentogether

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream

object QrCodeUtils {

    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val ROOM_CODE_REGEX = Regex("^[A-Z2-9]{6}$")
    private val EMBEDDED_URI_REGEX = Regex("""(?:koplingfy://room/|/room/|code=)([A-Za-z2-9]{6})""", RegexOption.IGNORE_CASE)
    private val STANDALONE_CODE_REGEX = Regex("""\b([A-Za-z2-9]{6})\b""")

    fun generateRoomCode(): String {
        return (1..6).map { CODE_ALPHABET.random() }.joinToString("")
    }

    fun normalizeRoomCode(input: String): String {
        return input.trim().uppercase().replace(" ", "").replace("-", "")
    }

    fun isValidRoomCode(code: String): Boolean = ROOM_CODE_REGEX.matches(code)

    fun buildRoomQrPayload(roomCode: String): String {
        val clean = normalizeRoomCode(roomCode)
        return "koplingfy://room/$clean"
    }

    /**
     * Parses Koplingfy QR payloads ("koplingfy://room/KPL74M"), deep links, or plain 6-char codes.
     */
    fun parseRoomCodeFromQr(rawText: String?): String? {
        if (rawText.isNullOrBlank()) return null
        val trimmed = rawText.trim()

        // 1. Direct normalized 6-char code
        val direct = normalizeRoomCode(trimmed)
        if (isValidRoomCode(direct)) return direct

        // 2. Koplingfy URI or URL parameter
        val uriMatch = EMBEDDED_URI_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.uppercase()
        if (uriMatch != null && isValidRoomCode(uriMatch)) return uriMatch

        // 3. Fallback standalone 6-char room code inside arbitrary QR text
        val fallback = STANDALONE_CODE_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.uppercase()
        if (fallback != null && isValidRoomCode(fallback)) return fallback

        return null
    }

    /**
     * Generates a crisp high-contrast QR Code Bitmap suitable for scanning on any device.
     */
    fun generateRoomQrBitmap(roomCode: String, sizePx: Int = 720): Bitmap? {
        return runCatching {
            val payload = buildRoomQrPayload(roomCode)
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)
            val darkColor = Color.rgb(11, 14, 18) // Obsidian Studio dark modules
            val lightColor = Color.WHITE
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (matrix.get(x, y)) darkColor else lightColor
                }
            }
            val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        }.getOrNull()
    }

    /**
     * Generates a crisp high-contrast QR Code Bitmap for an HTTPS URL (e.g., direct APK download link).
     */
    fun generateUrlQrBitmap(url: String, sizePx: Int = 640): Bitmap? {
        if (url.isBlank()) return null
        return runCatching {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val matrix = QRCodeWriter().encode(url.trim(), BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)
            val darkColor = Color.rgb(11, 14, 18)
            val lightColor = Color.WHITE
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (matrix.get(x, y)) darkColor else lightColor
                }
            }
            val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        }.getOrNull()
    }

    /**
     * Generates a shareable Koplingfy APK download QR card (Obsidian header + Emerald accent + scannable QR + footer).
     */
    fun generateShareableApkQrCardBitmap(downloadUrl: String, versionLabel: String): Bitmap? {
        val qr = generateUrlQrBitmap(downloadUrl, 640) ?: return null
        val cardW = 840
        val cardH = 1080
        val out = createBitmap(cardW, cardH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        canvas.drawColor(Color.WHITE)

        // Obsidian Studio top header
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(12, 16, 22) }
        canvas.drawRect(0f, 0f, cardW.toFloat(), 196f, headerPaint)

        // Emerald accent bar
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 229, 160) }
        canvas.drawRect(0f, 188f, cardW.toFloat(), 196f, accentPaint)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 229, 160)
            textSize = 44f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("KOPLINGFY • UNDUH APK", cardW / 2f, 92f, brandPaint)

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(220, 226, 234)
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Rilis Resmi GitHub • ${versionLabel.take(20)}", cardW / 2f, 148f, subtitlePaint)

        // QR Frame border
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(230, 235, 240)
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRoundRect(RectF(80f, 232f, cardW - 80f, 912f), 32f, 32f, framePaint)
        canvas.drawBitmap(qr, (cardW - qr.width) / 2f, 252f, null)

        val footerLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(95, 108, 122)
            textSize = 26f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("SCAN QR UNTUK LANGSUNG UNDUH KOPLINGFY", cardW / 2f, 974f, footerLabelPaint)

        val ownerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(11, 14, 18)
            textSize = 34f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("TikTok Owner: @budikopling10", cardW / 2f, 1032f, ownerPaint)

        return out
    }

    /**
     * Saves the shareable Koplingfy APK QR Card PNG to Gallery using zero-permission MediaStore (API 29+).
     */
    fun saveApkShareQrToGallery(
        context: Context,
        downloadUrl: String,
        versionLabel: String
    ): Result<String> {
        return runCatching {
            val bitmap = generateShareableApkQrCardBitmap(downloadUrl, versionLabel)
                ?: error("Gagal membuat kartu QR APK")
            val safeVer = versionLabel.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "latest" }
            val fileName = "Koplingfy_APK_QR_${safeVer}_${System.currentTimeMillis()}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Koplingfy")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Gagal menyimpan QR ke Galeri")
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: error("Tidak dapat membuka aliran penyimpanan")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Pictures/Koplingfy/$fileName"
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
                val target = File(dir, fileName)
                FileOutputStream(target).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                target.absolutePath
            }
        }
    }

    /**
     * Generates a shareable QRIS-style Koplingfy Room Pass card image (white card + header + QR + code).
     */
    fun generateShareablePassBitmap(roomCode: String, roomName: String): Bitmap? {
        val qr = generateRoomQrBitmap(roomCode, 640) ?: return null
        val cardW = 840
        val cardH = 1080
        val out = createBitmap(cardW, cardH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // Card background
        canvas.drawColor(Color.WHITE)

        // Top Obsidian Studio header banner
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(12, 16, 22) }
        canvas.drawRect(0f, 0f, cardW.toFloat(), 196f, headerPaint)

        // Emerald accent bar
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 229, 160) }
        canvas.drawRect(0f, 188f, cardW.toFloat(), 196f, accentPaint)

        // Brand title
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 229, 160)
            textSize = 44f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("KOPLINGFY • PLAY BERSAMA", cardW / 2f, 92f, brandPaint)

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(220, 226, 234)
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(roomName.take(32), cardW / 2f, 148f, subtitlePaint)

        // QR Frame border
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(230, 235, 240)
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRoundRect(RectF(80f, 232f, cardW - 80f, 912f), 32f, 32f, framePaint)

        // QR bitmap centered
        canvas.drawBitmap(qr, (cardW - qr.width) / 2f, 252f, null)

        // Room Code footer
        val codeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(95, 108, 122)
            textSize = 26f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("SCAN QR ATAU MASUKKAN KODE RUANG", cardW / 2f, 972f, codeLabelPaint)

        val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(11, 14, 18)
            textSize = 54f
            isFakeBoldText = true
            letterSpacing = 0.25f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(roomCode, cardW / 2f, 1038f, codePaint)

        return out
    }

    /**
     * Saves the QR Pass image to the device Gallery using zero-permission MediaStore (API 29+)
     * or app external pictures fallback on older versions.
     */
    fun saveQrPassToGallery(context: Context, roomCode: String, roomName: String): Result<String> {
        return runCatching {
            val bitmap = generateShareablePassBitmap(roomCode, roomName)
                ?: error("Gagal membuat gambar QR")
            val fileName = "Koplingfy_QR_${roomCode}_${System.currentTimeMillis()}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Koplingfy")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Gagal menyimpan ke Galeri")
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: error("Tidak dapat membuka aliran penyimpanan")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Pictures/Koplingfy/$fileName"
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
                val target = File(dir, fileName)
                FileOutputStream(target).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                target.absolutePath
            }
        }
    }

    /**
     * Decodes a QR code from a CameraX YUV_420_888 luminance plane (Y-plane).
     */
    fun decodeQrFromYuvPlane(yBytes: ByteArray, width: Int, height: Int): String? {
        return runCatching {
            val reader = MultiFormatReader().apply {
                setHints(
                    mapOf(
                        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                        DecodeHintType.CHARACTER_SET to "UTF-8"
                    )
                )
            }
            val source = PlanarYUVLuminanceSource(
                yBytes,
                width,
                height,
                0,
                0,
                width,
                height,
                false
            )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            reader.decodeWithState(binaryBitmap)?.text
        }.getOrNull()
    }

    /**
     * Decodes a QR code from a gallery image URI (zero-permission Photo Picker).
     */
    fun decodeQrFromImageUri(context: Context, uri: Uri): String? {
        return runCatching {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = android.graphics.BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                android.graphics.BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            val scaled = if (bitmap.width > 1200 || bitmap.height > 1200) {
                val ratio = 1200f / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt().coerceAtLeast(1),
                    (bitmap.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else bitmap

            val width = scaled.width
            val height = scaled.height
            val pixels = IntArray(width * height)
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)

            val reader = MultiFormatReader().apply {
                setHints(
                    mapOf(
                        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                        DecodeHintType.TRY_HARDER to true,
                        DecodeHintType.CHARACTER_SET to "UTF-8"
                    )
                )
            }
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            reader.decode(binaryBitmap)?.text
        }.getOrNull()
    }
}
