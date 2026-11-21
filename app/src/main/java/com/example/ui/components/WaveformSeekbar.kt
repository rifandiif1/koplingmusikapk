package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Custom animated audio waveform seekbar replacing a flat slider.
 * Generates a natural, deterministic musical contour per trackId, glows along the played
 * portion, gently breathes while playing, supports tap-to-seek and smooth drag scrubbing,
 * and visualizes optional A-B loop markers.
 */
@Composable
fun WaveformSeekbar(
    trackId: String,
    progress: Float,
    isPlaying: Boolean,
    activeColor: Color,
    inactiveColor: Color = Color.White.copy(alpha = 0.22f),
    abLoopStartFraction: Float? = null,
    abLoopEndFraction: Float? = null,
    onSeekProgress: (Float) -> Unit,
    onDragStateChange: (Boolean, Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val barCount = 48
    val baseAmplitudes = remember(trackId) {
        val rng = Random(trackId.hashCode().toLong() * 31L + 7919L)
        List(barCount) { idx ->
            val t = idx.toFloat() / (barCount - 1).coerceAtLeast(1)
            // Musical envelope: softer intro/outro, energetic verses/choruses in the middle
            val envelope = 0.42f + 0.58f * sin(t * PI).toFloat()
            val macroWave = 0.75f + 0.25f * sin(t * PI * 4.5).toFloat()
            val detail = 0.34f + rng.nextFloat() * 0.66f
            (envelope * macroWave * detail).coerceIn(0.18f, 1.0f)
        }
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }

    val latestOnSeek by rememberUpdatedState(onSeekProgress)
    val latestOnDragChange by rememberUpdatedState(onDragStateChange)

    val phase = if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "waveform_motion")
        val animatedPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "waveform_phase"
        )
        animatedPhase
    } else {
        0f
    }

    val effectiveProgress = if (isDragging) dragFraction else progress.coerceIn(0f, 1f)
    val playedBright = remember(activeColor) { lerp(activeColor, Color.White, 0.42f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics { contentDescription = "Penggeser posisi lagu gelombang audio" }
            .testTag("player_seek_slider")
            .pointerInput(trackId) {
                detectTapGestures { offset ->
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val target = (offset.x / w).coerceIn(0f, 1f)
                    latestOnDragChange(false, target)
                    latestOnSeek(target)
                }
            }
            .pointerInput(trackId) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        isDragging = true
                        dragFraction = (offset.x / w).coerceIn(0f, 1f)
                        latestOnDragChange(true, dragFraction)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        dragFraction = (change.position.x / w).coerceIn(0f, 1f)
                        latestOnDragChange(true, dragFraction)
                    },
                    onDragEnd = {
                        val finalFraction = dragFraction
                        isDragging = false
                        latestOnDragChange(false, finalFraction)
                        latestOnSeek(finalFraction)
                    },
                    onDragCancel = {
                        isDragging = false
                        latestOnDragChange(false, progress.coerceIn(0f, 1f))
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalWidth = size.width
            val totalHeight = size.height
            val centerY = totalHeight / 2f
            val maxBarHeight = totalHeight * 0.76f
            val minBarHeight = totalHeight * 0.14f

            // Optional A-B Loop highlight band behind bars
            if (abLoopStartFraction != null) {
                val startX = totalWidth * abLoopStartFraction.coerceIn(0f, 1f)
                val endX = totalWidth * (abLoopEndFraction ?: abLoopStartFraction).coerceIn(0f, 1f)
                if (endX > startX + 2f) {
                    drawRoundRect(
                        color = activeColor.copy(alpha = 0.16f),
                        topLeft = Offset(startX, totalHeight * 0.12f),
                        size = Size(endX - startX, totalHeight * 0.76f),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                }
            }

            val step = totalWidth / barCount.toFloat()
            val barWidth = (step * 0.56f).coerceIn(2.6f, 8.5f)
            val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            val playheadIndex = effectiveProgress * (barCount - 1)

            for (i in 0 until barCount) {
                val barFraction = i.toFloat() / (barCount - 1).coerceAtLeast(1)
                val isPlayed = barFraction <= effectiveProgress
                val distanceToHead = abs(i - playheadIndex)

                // Subtle, organic wave motion while playing (stronger near active playhead)
                val livePulse = if (isPlaying) {
                    val proximityBoost = (1f - (distanceToHead / 7f).coerceIn(0f, 1f))
                    val wave = sin(phase + i * 0.52f)
                    1f + wave * (0.07f + 0.18f * proximityBoost)
                } else {
                    1f
                }

                // Slight emphasis on bars right under thumb while scrubbing
                val scrubBoost = if (isDragging && distanceToHead < 2.2f) {
                    1.18f - (distanceToHead / 2.2f) * 0.18f
                } else {
                    1f
                }

                val amp = (baseAmplitudes[i] * livePulse * scrubBoost).coerceIn(0.14f, 1.0f)
                val barHeight = (minBarHeight + (maxBarHeight - minBarHeight) * amp)
                    .coerceAtMost(totalHeight * 0.86f)

                val x = i * step + (step - barWidth) / 2f
                val topY = centerY - barHeight / 2f

                val barColor = when {
                    distanceToHead < 0.85f -> Color.White
                    isPlayed -> lerp(playedBright, Color.White, (1f - (distanceToHead / 12f).coerceIn(0f, 1f)) * 0.45f)
                    else -> inactiveColor
                }

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, topY),
                    size = Size(barWidth, barHeight),
                    cornerRadius = cornerRadius
                )
            }

            // Crisp playhead glow pill
            val headX = (totalWidth * effectiveProgress).coerceIn(barWidth, totalWidth - barWidth)
            val headHeight = if (isDragging) totalHeight * 0.90f else totalHeight * 0.78f
            drawRoundRect(
                color = activeColor.copy(alpha = if (isDragging) 0.42f else 0.25f),
                topLeft = Offset(headX - barWidth * 1.15f, centerY - headHeight / 2f),
                size = Size(barWidth * 2.3f, headHeight),
                cornerRadius = CornerRadius(barWidth * 1.2f, barWidth * 1.2f)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(headX - barWidth * 0.48f, centerY - headHeight / 2f),
                size = Size(barWidth * 0.96f, headHeight),
                cornerRadius = CornerRadius(barWidth, barWidth)
            )
        }
    }
}
