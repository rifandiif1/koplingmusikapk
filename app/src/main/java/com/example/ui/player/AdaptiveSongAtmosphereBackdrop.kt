package com.example.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.model.PlayerBackgroundStyle
import com.example.model.Track
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Song-adaptive atmospheric backdrop modes selected from track metadata,
 * AI-analyzed mood tags, and dynamic artwork colors.
 */
enum class SongAtmosphereTheme {
    EMBERS_ENERGY,
    RAIN_MELANCHOLY,
    PETALS_ROMANCE,
    NEON_PULSE,
    AURORA_COSMIC,
    FLOATING_BOKEH
}

private data class AtmosphereParticle(
    val baseX: Float,
    val baseY: Float,
    val speed: Float,
    val radiusFactor: Float,
    val phase: Float,
    val swayAmplitude: Float,
    val alpha: Float,
    val rotationSpeed: Float,
    val colorMix: Float
)

/**
 * Classifies the active song into a curated visual atmosphere using title, artist, album,
 * genre, and (when available) AI Song Meaning mood tags.
 */
fun resolveSongAtmosphereTheme(
    track: Track,
    moodTags: List<String> = emptyList()
): SongAtmosphereTheme {
    val haystack = buildString {
        append(track.title).append(' ')
        append(track.artist).append(' ')
        append(track.album).append(' ')
        append(track.genre).append(' ')
        moodTags.forEach { append(it).append(' ') }
    }.lowercase()

    val rainKeywords = listOf(
        "rain", "hujan", "sad", "sedih", "galau", "cry", "tears", "air mata",
        "lonely", "sepi", "patah hati", "broken", "melankolis", "melancholy",
        "ballad", "acoustic", "akustik", "slow", "pilu", "luka", "senja", "sore", "sendiri"
    )
    if (rainKeywords.any { haystack.contains(it) }) return SongAtmosphereTheme.RAIN_MELANCHOLY

    val emberKeywords = listOf(
        "rock", "metal", "fire", "api", "burn", "koplo", "dangdut", "remix",
        "party", "pesta", "hype", "energik", "energetic", "power", "rage",
        "hip hop", "rap", "trap", "drill", "punk", "hard", "anthem", "semangat", "goyang"
    )
    if (emberKeywords.any { haystack.contains(it) }) return SongAtmosphereTheme.EMBERS_ENERGY

    val neonKeywords = listOf(
        "edm", "electronic", "synth", "cyber", "neon", "phonk", "techno", "house",
        "club", "dance", "disco", "funk", "dubstep", "future", "k-pop", "kpop",
        "j-pop", "jpop", "anime", "ost", "bass", "dj", "electro", "nightcore"
    )
    if (neonKeywords.any { haystack.contains(it) }) return SongAtmosphereTheme.NEON_PULSE

    val romanceKeywords = listOf(
        "love", "cinta", "romantis", "romance", "kasih", "sayang", "heart", "hati",
        "sakura", "flower", "bunga", "kiss", "rindu", "miss you", "forever",
        "r&b", "soul", "sweet", "manis", "peluk", "asmara", "kekasih"
    )
    if (romanceKeywords.any { haystack.contains(it) }) return SongAtmosphereTheme.PETALS_ROMANCE

    val cosmicKeywords = listOf(
        "night", "malam", "moon", "bulan", "star", "bintang", "sky", "langit",
        "space", "galaxy", "dream", "mimpi", "chill", "lo-fi", "lofi", "ambient",
        "sleep", "tidur", "calm", "tenang", "jazz", "midnight", "aurora", "cosmos", "ocean", "laut"
    )
    if (cosmicKeywords.any { haystack.contains(it) }) return SongAtmosphereTheme.AURORA_COSMIC

    // Deterministic variety for tracks without explicit mood keywords
    val bucket = (track.id.hashCode() and 0x7FFFFFFF) % 3
    return when (bucket) {
        0 -> SongAtmosphereTheme.FLOATING_BOKEH
        1 -> SongAtmosphereTheme.AURORA_COSMIC
        else -> SongAtmosphereTheme.PETALS_ROMANCE
    }
}

/**
 * Lightweight, 60fps song-reactive atmospheric particle & ambient glow layer
 * rendered behind Now Playing controls without interfering with readability or gestures.
 */
@Composable
fun AdaptiveSongAtmosphereBackdrop(
    track: Track,
    moodTags: List<String>,
    accentPrimary: Color,
    accentGlow: Color,
    isPlaying: Boolean,
    backgroundStyle: PlayerBackgroundStyle,
    modifier: Modifier = Modifier
) {
    val theme = remember(track.id, track.title, track.genre, moodTags) {
        resolveSongAtmosphereTheme(track, moodTags)
    }

    val particles = remember(track.id, theme) {
        val rng = Random(track.id.hashCode() * 31 + theme.ordinal * 997)
        val count = when (theme) {
            SongAtmosphereTheme.RAIN_MELANCHOLY -> 42
            SongAtmosphereTheme.EMBERS_ENERGY -> 34
            SongAtmosphereTheme.PETALS_ROMANCE -> 24
            SongAtmosphereTheme.NEON_PULSE -> 26
            SongAtmosphereTheme.AURORA_COSMIC -> 32
            SongAtmosphereTheme.FLOATING_BOKEH -> 20
        }
        List(count) {
            AtmosphereParticle(
                baseX = rng.nextFloat(),
                baseY = rng.nextFloat(),
                speed = 0.045f + rng.nextFloat() * 0.14f,
                radiusFactor = 0.35f + rng.nextFloat() * 0.95f,
                phase = rng.nextFloat() * (2f * PI.toFloat()),
                swayAmplitude = 0.015f + rng.nextFloat() * 0.045f,
                alpha = 0.22f + rng.nextFloat() * 0.55f,
                rotationSpeed = (rng.nextFloat() - 0.5f) * 55f,
                colorMix = rng.nextFloat()
            )
        }
    }

    // Smooth energy scale so pausing gently settles the particles into a serene drift
    val activityScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.28f,
        animationSpec = tween(durationMillis = 650),
        label = "atmosphere_activity"
    )
    val styleIntensity = when (backgroundStyle) {
        PlayerBackgroundStyle.SOLID -> 0.52f
        PlayerBackgroundStyle.GRADIENT -> 0.92f
        PlayerBackgroundStyle.BLUR_ARTWORK -> 0.86f
    }

    var elapsedSeconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(track.id) {
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos != 0L) {
                    val dt = ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.06f)
                    elapsedSeconds += dt * (0.28f + 0.72f * activityScale)
                }
                lastFrameNanos = frameNanos
            }
        }
    }

    val reusablePetalPath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val t = elapsedSeconds
        val globalAlpha = styleIntensity * (0.62f + 0.38f * activityScale)

        when (theme) {
            SongAtmosphereTheme.EMBERS_ENERGY -> {
                drawEmbersEnergy(w, h, t, globalAlpha, accentPrimary, accentGlow, particles)
            }
            SongAtmosphereTheme.RAIN_MELANCHOLY -> {
                drawRainMelancholy(w, h, t, globalAlpha, accentPrimary, accentGlow, particles)
            }
            SongAtmosphereTheme.PETALS_ROMANCE -> {
                drawPetalsRomance(w, h, t, globalAlpha, accentPrimary, accentGlow, particles, reusablePetalPath)
            }
            SongAtmosphereTheme.NEON_PULSE -> {
                drawNeonPulse(w, h, t, globalAlpha, accentPrimary, accentGlow, particles)
            }
            SongAtmosphereTheme.AURORA_COSMIC -> {
                drawAuroraCosmic(w, h, t, globalAlpha, accentPrimary, accentGlow, particles)
            }
            SongAtmosphereTheme.FLOATING_BOKEH -> {
                drawFloatingBokeh(w, h, t, globalAlpha, accentPrimary, accentGlow, particles)
            }
        }
    }
}

private fun DrawScope.drawEmbersEnergy(
    w: Float,
    h: Float,
    t: Float,
    globalAlpha: Float,
    accentPrimary: Color,
    accentGlow: Color,
    particles: List<AtmosphereParticle>
) {
    // Warm rising floor glow
    val pulse = 0.85f + 0.15f * sin(t * 2.8f)
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                accentPrimary.copy(alpha = 0.08f * globalAlpha * pulse),
                Color(0xFFFF6D2E).copy(alpha = 0.16f * globalAlpha * pulse)
            ),
            startY = h * 0.48f,
            endY = h
        )
    )

    val emberWarm = Color(0xFFFFA73B)
    for (p in particles) {
        val progress = (p.baseY - t * p.speed * 1.45f).mod(1.0f)
        val xNorm = (p.baseX + sin(t * 1.9f + p.phase) * p.swayAmplitude).mod(1.0f)
        val x = xNorm * w
        val y = progress * h
        // Fade gently near top edge
        val edgeFade = (progress * 3.2f).coerceIn(0f, 1f) * ((1f - progress) * 4f).coerceIn(0f, 1f)
        val alpha = (p.alpha * edgeFade * globalAlpha * 0.88f).coerceIn(0f, 0.9f)
        if (alpha < 0.01f) continue

        val baseColor = if (p.colorMix > 0.45f) emberWarm else accentGlow
        val r = (2.2f + p.radiusFactor * 3.8f) * density / 2.6f

        // Soft outer ember halo
        drawCircle(
            color = baseColor.copy(alpha = alpha * 0.34f),
            radius = r * 2.9f,
            center = Offset(x, y)
        )
        // Bright ember core
        drawCircle(
            color = Color.White.copy(alpha = alpha * 0.75f),
            radius = r * 0.72f,
            center = Offset(x, y)
        )
        // Subtle upward spark streak
        drawLine(
            color = baseColor.copy(alpha = alpha * 0.42f),
            start = Offset(x, y),
            end = Offset(x - sin(t * 1.9f + p.phase) * 4f, y + r * 4.5f),
            strokeWidth = (r * 0.65f).coerceAtLeast(1f),
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawRainMelancholy(
    w: Float,
    h: Float,
    t: Float,
    globalAlpha: Float,
    accentPrimary: Color,
    accentGlow: Color,
    particles: List<AtmosphereParticle>
) {
    // Cool atmospheric mist shimmer near upper half
    val mistShift = sin(t * 0.5f) * w * 0.06f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accentGlow.copy(alpha = 0.14f * globalAlpha),
                Color.Transparent
            ),
            center = Offset(w * 0.5f + mistShift, h * 0.24f),
            radius = w * 0.72f
        ),
        radius = w * 0.72f,
        center = Offset(w * 0.5f + mistShift, h * 0.24f)
    )

    val coolTint = Color(0xFFA7D8FF)
    for (p in particles) {
        val fallSpeed = 0.36f + p.speed * 2.1f
        val yNorm = (p.baseY + t * fallSpeed).mod(1.08f) - 0.04f
        val xNorm = (p.baseX - yNorm * 0.11f).mod(1.0f)
        val x = xNorm * w
        val y = yNorm * h
        val len = (18f + p.radiusFactor * 26f) * (density / 2.6f)
        val alpha = (p.alpha * globalAlpha * 0.46f).coerceIn(0f, 0.55f)
        val dropColor = if (p.colorMix > 0.5f) coolTint else accentGlow

        drawLine(
            color = dropColor.copy(alpha = alpha),
            start = Offset(x, y),
            end = Offset(x + len * 0.13f, y - len),
            strokeWidth = (1.1f + p.radiusFactor * 0.9f),
            cap = StrokeCap.Round
        )
    }

    // Occasional soft ripple glow near bottom
    for (i in 0..2) {
        val phase = (t * 0.42f + i * 0.33f).mod(1f)
        val rx = w * (0.22f + i * 0.28f)
        val ry = h * (0.78f + (i % 2) * 0.07f)
        val rippleAlpha = (1f - phase) * 0.16f * globalAlpha
        drawOval(
            color = accentPrimary.copy(alpha = rippleAlpha),
            topLeft = Offset(rx - phase * 58f, ry - phase * 14f),
            size = Size(phase * 116f, phase * 28f),
            style = Stroke(width = 1.6f)
        )
    }
}

private fun DrawScope.drawPetalsRomance(
    w: Float,
    h: Float,
    t: Float,
    globalAlpha: Float,
    accentPrimary: Color,
    accentGlow: Color,
    particles: List<AtmosphereParticle>,
    petalPath: Path
) {
    val blush = Color(0xFFFFB7D5)
    // Soft romantic radial bloom
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accentPrimary.copy(alpha = 0.15f * globalAlpha),
                blush.copy(alpha = 0.06f * globalAlpha),
                Color.Transparent
            ),
            center = Offset(w * 0.5f, h * 0.34f),
            radius = w * 0.68f
        ),
        radius = w * 0.68f,
        center = Offset(w * 0.5f, h * 0.34f)
    )

    for (p in particles) {
        val yNorm = (p.baseY + t * p.speed * 0.65f).mod(1.06f) - 0.03f
        val sway = sin(t * 1.15f + p.phase) * p.swayAmplitude * 1.6f
        val xNorm = (p.baseX + sway + t * 0.015f).mod(1.0f)
        val x = xNorm * w
        val y = yNorm * h
        val fade = ((1f - yNorm) * 4f).coerceIn(0f, 1f) * (yNorm * 5f).coerceIn(0f, 1f)
        val alpha = (p.alpha * fade * globalAlpha * 0.65f).coerceIn(0f, 0.72f)
        if (alpha < 0.01f) continue

        val sz = (6.5f + p.radiusFactor * 7.5f) * (density / 2.6f)
        val angle = (p.phase * 57.3f) + t * p.rotationSpeed
        val tint = if (p.colorMix > 0.5f) blush else accentGlow

        rotate(degrees = angle, pivot = Offset(x, y)) {
            petalPath.reset()
            petalPath.moveTo(x, y - sz)
            petalPath.cubicTo(
                x + sz * 0.85f, y - sz * 0.4f,
                x + sz * 0.65f, y + sz * 0.75f,
                x, y + sz
            )
            petalPath.cubicTo(
                x - sz * 0.65f, y + sz * 0.75f,
                x - sz * 0.85f, y - sz * 0.4f,
                x, y - sz
            )
            petalPath.close()
            drawPath(path = petalPath, color = tint.copy(alpha = alpha))
        }
    }
}

private fun DrawScope.drawNeonPulse(
    w: Float,
    h: Float,
    t: Float,
    globalAlpha: Float,
    accentPrimary: Color,
    accentGlow: Color,
    particles: List<AtmosphereParticle>
) {
    val cx = w * 0.5f
    val cy = h * 0.34f
    val maxR = maxOf(w, h) * 0.58f

    // Rhythmic expanding neon sound-wave rings centered behind album art
    for (ring in 0..3) {
        val progress = (t * 0.34f + ring * 0.25f).mod(1f)
        val r = 36f + progress * maxR
        val ringAlpha = (1f - progress) * (1f - progress) * 0.26f * globalAlpha
        drawCircle(
            color = if (ring % 2 == 0) accentGlow.copy(alpha = ringAlpha) else accentPrimary.copy(alpha = ringAlpha),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 2.2f * (1f - progress * 0.5f))
        )
    }

    // Crisp luminous starlight diamond prisms drifting outward/upward
    for (p in particles) {
        val yNorm = (p.baseY - t * p.speed * 0.85f).mod(1.0f)
        val xNorm = (p.baseX + cos(t * 1.4f + p.phase) * p.swayAmplitude * 0.7f).mod(1.0f)
        val x = xNorm * w
        val y = yNorm * h
        val twinkle = 0.45f + 0.55f * sin(t * 4.2f + p.phase)
        val alpha = (p.alpha * twinkle * globalAlpha * 0.78f).coerceIn(0f, 0.85f)
        val r = (2.8f + p.radiusFactor * 4.2f) * (density / 2.6f)
        val tint = if (p.colorMix > 0.5f) accentGlow else accentPrimary

        drawCircle(
            color = tint.copy(alpha = alpha * 0.28f),
            radius = r * 2.5f,
            center = Offset(x, y)
        )
        rotate(degrees = 45f + t * p.rotationSpeed * 0.4f, pivot = Offset(x, y)) {
            drawRect(
                color = Color.White.copy(alpha = alpha * 0.82f),
                topLeft = Offset(x - r * 0.55f, y - r * 0.55f),
                size = Size(r * 1.1f, r * 1.1f)
            )
        }
    }
}

private fun DrawScope.drawAuroraCosmic(
    w: Float,
    h: Float,
    t: Float,
    globalAlpha: Float,
    accentPrimary: Color,
    accentGlow: Color,
    particles: List<AtmosphereParticle>
) {
    // Twin drifting aurora nebulae
    val orb1 = Offset(
        x = w * (0.28f + 0.16f * sin(t * 0.38f)),
        y = h * (0.24f + 0.08f * cos(t * 0.31f))
    )
    val orb2 = Offset(
        x = w * (0.74f + 0.14f * cos(t * 0.33f)),
        y = h * (0.62f + 0.09f * sin(t * 0.29f))
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accentGlow.copy(alpha = 0.20f * globalAlpha), Color.Transparent),
            center = orb1,
            radius = w * 0.65f
        ),
        radius = w * 0.65f,
        center = orb1
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accentPrimary.copy(alpha = 0.18f * globalAlpha), Color.Transparent),
            center = orb2,
            radius = w * 0.60f
        ),
        radius = w * 0.60f,
        center = orb2
    )

    // Twinkling starlight dust motes
    for (p in particles) {
        val x = ((p.baseX + sin(t * 0.22f + p.phase) * p.swayAmplitude).mod(1.0f)) * w
        val y = ((p.baseY - t * p.speed * 0.22f).mod(1.0f)) * h
        val twinkle = 0.30f + 0.70f * (0.5f + 0.5f * sin(t * 2.4f + p.phase))
        val alpha = (p.alpha * twinkle * globalAlpha * 0.80f).coerceIn(0f, 0.85f)
        val r = (1.6f + p.radiusFactor * 2.4f) * (density / 2.6f)
        val color = if (p.colorMix > 0.6f) Color.White else accentGlow

        drawCircle(color = color.copy(alpha = alpha * 0.32f), radius = r * 2.6f, center = Offset(x, y))
        drawCircle(color = color.copy(alpha = alpha), radius = r * 0.85f, center = Offset(x, y))
    }
}

private fun DrawScope.drawFloatingBokeh(
    w: Float,
    h: Float,
    t: Float,
    globalAlpha: Float,
    accentPrimary: Color,
    accentGlow: Color,
    particles: List<AtmosphereParticle>
) {
    for (p in particles) {
        val yNorm = (p.baseY - t * p.speed * 0.35f).mod(1.12f) - 0.06f
        val xNorm = (p.baseX + sin(t * 0.55f + p.phase) * p.swayAmplitude).mod(1.0f)
        val x = xNorm * w
        val y = yNorm * h
        val radius = (14f + p.radiusFactor * 28f) * (density / 2.6f)
        val alpha = (p.alpha * globalAlpha * 0.22f).coerceIn(0f, 0.26f)
        val color = if (p.colorMix > 0.45f) accentGlow else accentPrimary

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = alpha),
                    color.copy(alpha = alpha * 0.35f),
                    Color.Transparent
                ),
                center = Offset(x, y),
                radius = radius
            ),
            radius = radius,
            center = Offset(x, y)
        )
    }
}
