package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val supportedReactionEmojis = listOf("🔥", "❤️", "🎵", "👏", "🤘")

private data class FloatingParticle(
    val id: Long,
    val emoji: String,
    val startXFraction: Float,
    val swayAmplitudePx: Float,
    val swayFrequency: Float,
    val fontSizeSp: Float,
    val durationMs: Int,
    val delayMs: Long
)

/**
 * Non-blocking overlay that launches animated floating emoji particles rising from the bottom
 * whenever a Play Bersama reaction arrives via `reactionText` / `triggerKey`.
 */
@Composable
fun FloatingReactionsOverlay(
    triggerKey: String,
    reactionText: String,
    modifier: Modifier = Modifier
) {
    val particles = remember { mutableStateListOf<FloatingParticle>() }
    var lastHandledKey by remember { mutableStateOf(triggerKey) }

    LaunchedEffect(triggerKey, reactionText) {
        if (triggerKey == lastHandledKey) return@LaunchedEffect
        lastHandledKey = triggerKey

        val emoji = supportedReactionEmojis.firstOrNull { reactionText.startsWith(it) } ?: return@LaunchedEffect
        val baseId = System.nanoTime()
        val burst = List(6) { idx ->
            FloatingParticle(
                id = baseId + idx,
                emoji = emoji,
                startXFraction = (0.14f + idx * 0.13f + Random.nextFloat() * 0.06f).coerceIn(0.10f, 0.88f),
                swayAmplitudePx = 22f + Random.nextFloat() * 34f,
                swayFrequency = 1.6f + Random.nextFloat() * 1.4f,
                fontSizeSp = 26f + Random.nextFloat() * 14f,
                durationMs = 1750 + Random.nextInt(550),
                delayMs = idx * 110L
            )
        }
        particles.addAll(burst)
        // Trim excess particles if many users react rapidly
        if (particles.size > 30) {
            particles.removeRange(0, particles.size - 30)
        }
    }

    if (particles.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        Box(modifier = Modifier.fillMaxSize()) {
            particles.toList().forEach { particle ->
                FloatingEmojiParticleItem(
                    particle = particle,
                    containerWidthPx = widthPx,
                    containerHeightPx = heightPx,
                    onFinished = { particles.removeAll { it.id == particle.id } }
                )
            }
        }
    }
}

@Composable
private fun FloatingEmojiParticleItem(
    particle: FloatingParticle,
    containerWidthPx: Float,
    containerHeightPx: Float,
    onFinished: () -> Unit
) {
    val progress = remember(particle.id) { Animatable(0f) }

    LaunchedEffect(particle.id) {
        if (particle.delayMs > 0L) delay(particle.delayMs)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = particle.durationMs, easing = FastOutSlowInEasing)
        )
        onFinished()
    }

    val p = progress.value
    val baseX = containerWidthPx * particle.startXFraction
    val sway = sin(p * particle.swayFrequency * PI * 2.0).toFloat() * particle.swayAmplitudePx
    val currentX = (baseX + sway).roundToInt()
    val startY = containerHeightPx * 0.86f
    val endY = containerHeightPx * 0.14f
    val currentY = (startY + (endY - startY) * p).roundToInt()

    val alpha = when {
        p < 0.15f -> p / 0.15f
        p > 0.72f -> ((1f - p) / 0.28f).coerceIn(0f, 1f)
        else -> 1f
    }
    val scale = when {
        p < 0.18f -> 0.55f + (p / 0.18f) * 0.65f
        p < 0.35f -> 1.20f - ((p - 0.18f) / 0.17f) * 0.20f
        else -> 1.0f + (p - 0.35f) * 0.15f
    }

    Text(
        text = particle.emoji,
        fontSize = particle.fontSizeSp.sp,
        modifier = Modifier
            .offset { IntOffset(currentX, currentY) }
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
    )
}
