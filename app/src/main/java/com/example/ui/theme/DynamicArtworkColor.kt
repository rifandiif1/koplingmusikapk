package com.example.ui.theme

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DynamicArtworkPalette(
    val primaryAccent: Color,
    val deepAmbient: Color,
    val glowHighlight: Color
)

/**
 * Extracts vibrant & deep ambient colors from a track's album artwork URL via Coil + HSV clustering.
 * Falls back cleanly to the current MaterialTheme accent when disabled or unavailable.
 */
@Composable
fun rememberDynamicArtworkPalette(
    artworkUrl: String?,
    enabled: Boolean
): DynamicArtworkPalette {
    val defaultPrimary = MaterialTheme.colorScheme.primary
    val defaultSecondary = MaterialTheme.colorScheme.secondary
    val context = LocalContext.current

    var rawPrimary by remember { mutableStateOf(defaultPrimary) }
    var rawAmbient by remember { mutableStateOf(defaultPrimary) }
    var rawGlow by remember { mutableStateOf(defaultSecondary) }

    LaunchedEffect(artworkUrl, enabled, defaultPrimary, defaultSecondary) {
        if (!enabled || artworkUrl.isNullOrBlank()) {
            rawPrimary = defaultPrimary
            rawAmbient = defaultPrimary
            rawGlow = defaultSecondary
            return@LaunchedEffect
        }
        val extracted = withContext(Dispatchers.IO) {
            extractPaletteFromUrl(context, artworkUrl)
        }
        if (extracted != null) {
            rawPrimary = extracted.primaryAccent
            rawAmbient = extracted.deepAmbient
            rawGlow = extracted.glowHighlight
        } else {
            rawPrimary = defaultPrimary
            rawAmbient = defaultPrimary
            rawGlow = defaultSecondary
        }
    }

    val animPrimary by animateColorAsState(
        targetValue = rawPrimary,
        animationSpec = tween(durationMillis = 650),
        label = "dynamic_art_primary"
    )
    val animAmbient by animateColorAsState(
        targetValue = rawAmbient,
        animationSpec = tween(durationMillis = 750),
        label = "dynamic_art_ambient"
    )
    val animGlow by animateColorAsState(
        targetValue = rawGlow,
        animationSpec = tween(durationMillis = 700),
        label = "dynamic_art_glow"
    )

    return DynamicArtworkPalette(
        primaryAccent = animPrimary,
        deepAmbient = animAmbient,
        glowHighlight = animGlow
    )
}

private suspend fun extractPaletteFromUrl(
    context: android.content.Context,
    url: String
): DynamicArtworkPalette? {
    return runCatching {
        val loader = ImageLoader(context)
        val req = ImageRequest.Builder(context)
            .data(url)
            .size(64, 64)
            .allowHardware(false)
            .build()
        val result = loader.execute(req) as? SuccessResult ?: return null
        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return null
        extractPaletteFromBitmap(bitmap)
    }.getOrNull()
}

private fun extractPaletteFromBitmap(bitmap: Bitmap): DynamicArtworkPalette? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null

    // 24 hue buckets (each 15 degrees) weighted by saturation & mid-range brightness
    val hueWeights = FloatArray(24)
    val hueSatSum = FloatArray(24)
    val hueValSum = FloatArray(24)
    val hueCount = IntArray(24)
    val hsv = FloatArray(3)

    val stepX = (width / 24).coerceAtLeast(1)
    val stepY = (height / 24).coerceAtLeast(1)

    for (y in 0 until height step stepY) {
        for (x in 0 until width step stepX) {
            val pixel = bitmap.getPixel(x, y)
            if (android.graphics.Color.alpha(pixel) < 200) continue
            android.graphics.Color.colorToHSV(pixel, hsv)
            val h = hsv[0]
            val s = hsv[1]
            val v = hsv[2]
            if (v < 0.12f) continue
            val bucket = ((h / 15f).toInt()).coerceIn(0, 23)
            val weight = (s * s * 2.2f + 0.15f) * (1f - kotlin.math.abs(v - 0.62f))
            hueWeights[bucket] += weight
            hueSatSum[bucket] += s
            hueValSum[bucket] += v
            hueCount[bucket] += 1
        }
    }

    var bestBucket = -1
    var bestWeight = 0f
    for (i in 0 until 24) {
        if (hueWeights[i] > bestWeight && hueCount[i] > 0) {
            bestWeight = hueWeights[i]
            bestBucket = i
        }
    }
    if (bestBucket < 0) return null

    val avgHue = bestBucket * 15f + 7.5f
    val avgSat = (hueSatSum[bestBucket] / hueCount[bestBucket]).coerceIn(0.45f, 0.92f)
    val avgVal = (hueValSum[bestBucket] / hueCount[bestBucket]).coerceIn(0.68f, 0.95f)

    val primaryInt = android.graphics.Color.HSVToColor(floatArrayOf(avgHue, avgSat, avgVal))
    val ambientInt = android.graphics.Color.HSVToColor(floatArrayOf(avgHue, (avgSat * 0.85f).coerceIn(0.35f, 0.85f), 0.42f))
    val glowHue = (avgHue + 32f) % 360f
    val glowInt = android.graphics.Color.HSVToColor(floatArrayOf(glowHue, (avgSat * 0.9f).coerceIn(0.45f, 0.9f), 0.88f))

    return DynamicArtworkPalette(
        primaryAccent = Color(primaryInt),
        deepAmbient = Color(ambientInt),
        glowHighlight = Color(glowInt)
    )
}
