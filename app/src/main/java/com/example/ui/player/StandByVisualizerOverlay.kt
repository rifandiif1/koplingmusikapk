package com.example.ui.player

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AlbumArtImage
import com.example.ui.components.WaveformSeekbar
import com.example.viewmodel.PlayerUiState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class StandByVisualizerStyle(val label: String) {
    NEON_SPECTRUM("Spektrum Neon"),
    OSCILLOSCOPE("Osiloskop"),
    LIGHT_RING("Cincin Cahaya"),
    FULLSCREEN_LYRICS("Lirik Penuh")
}

/**
 * Full-Screen Desk / Bedside StandBy Clock, Aesthetic Audio Visualizer, & Full-Screen Lyrics Overlay.
 * Features a giant OLED digital clock, live synced lyrics, 3 selectable audio visualizer modes
 * (Neon Spectrum, Oscilloscope Wave, Aurora Light Ring), a dedicated Full-Screen Lyrics mode
 * with bounded auto-sizing typography, keep-screen-on support, and adaptive Portrait + Landscape layouts.
 */
@Composable
fun StandByVisualizerOverlay(
    playerState: PlayerUiState,
    isLiked: Boolean,
    accentPrimary: Color,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleTranslation: () -> Unit = {},
    onExitStandBy: () -> Unit
) {
    val track = playerState.currentTrack ?: return
    val context = LocalContext.current
    BackHandler(onBack = onExitStandBy)

    // Keep screen awake while in Desk / StandBy mode
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var visualizerStyle by remember { mutableStateOf(StandByVisualizerStyle.NEON_SPECTRUM) }
    var fullLyricsListMode by remember { mutableStateOf(false) }
    var showHud by remember { mutableStateOf(true) }
    var hudInteractionTick by remember { mutableLongStateOf(0L) }

    // Auto-hide HUD controls after 5s of inactivity so the desk clock remains clean
    LaunchedEffect(showHud, hudInteractionTick) {
        if (showHud) {
            delay(5_200L)
            showHud = false
        }
    }

    // Live ticking clock (updates every second)
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val clockTime = remember(nowMillis / 1000L) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nowMillis))
    }
    val clockSeconds = remember(nowMillis / 1000L) {
        SimpleDateFormat("ss", Locale.getDefault()).format(Date(nowMillis))
    }
    val clockDate = remember(nowMillis / 60_000L) {
        SimpleDateFormat("EEEE, d MMM", Locale("id", "ID")).format(Date(nowMillis))
    }

    // Live synced lyric line
    val syncedLines = playerState.lyricsState.syncedLines
    val adjustedPosMs = (playerState.positionMs + playerState.lyricsOffsetMs).coerceAtLeast(0L)
    val activeLyric = remember(syncedLines, adjustedPosMs) {
        val idx = syncedLines.indexOfLast { it.timestampMs <= adjustedPosMs + 250L }
        syncedLines.getOrNull(idx)?.text?.trim()?.takeIf { it.isNotEmpty() }
    }

    // Continuous smooth phase driving the aesthetic audio visualizers
    val infiniteTransition = rememberInfiniteTransition(label = "standby_vis_loop")
    val animPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "standby_phase"
    )
    val secondaryPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "standby_phase_2"
    )

    val neonGlow = remember(accentPrimary) { lerp(accentPrimary, Color.White, 0.25f) }
    val secondaryGlow = remember(accentPrimary) { lerp(accentPrimary, Color(0xFF7CE8FF), 0.38f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030305))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        accentPrimary.copy(alpha = if (playerState.isPlaying) 0.16f else 0.08f),
                        Color(0xFF040407),
                        Color(0xFF020204)
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showHud = !showHud
                if (showHud) hudInteractionTick++
            }
            .testTag("standby_visualizer_overlay")
    ) {
        val isLandscape = maxWidth > maxHeight && maxWidth >= 560.dp

        if (visualizerStyle == StandByVisualizerStyle.FULLSCREEN_LYRICS) {
            StandByFullScreenLyricsStage(
                track = track,
                playerState = playerState,
                clockTime = clockTime,
                accentPrimary = accentPrimary,
                isLandscape = isLandscape,
                isListMode = fullLyricsListMode,
                onSeekToLine = { tsMs ->
                    hudInteractionTick++
                    onSeek(tsMs)
                }
            )
        } else if (isLandscape) {
            // ---- LANDSCAPE DESK / BEDSIDE STANDBY LAYOUT ----
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 32.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // Left Pane: Giant StandBy Digital Clock + Date + Live Synced Lyric
                Column(
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = clockDate.uppercase(Locale("id", "ID")),
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 2.4.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = neonGlow.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = clockTime,
                            color = Color(0xFFF6F7FB),
                            fontSize = 88.sp,
                            lineHeight = 88.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = (-2).sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = clockSeconds,
                            color = neonGlow.copy(alpha = 0.78f),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    StandByLyricTicker(
                        activeLyric = activeLyric,
                        fallbackSubtitle = "${track.title} — ${track.artist}",
                        accentColor = neonGlow,
                        textAlign = TextAlign.Start
                    )
                }

                // Right Pane: Visualizer Stage + Track Info
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StandByVisualizerStage(
                        style = visualizerStyle,
                        artworkUrl = track.artworkUrl,
                        trackTitle = track.title,
                        isPlaying = playerState.isPlaying,
                        phase = animPhase,
                        phase2 = secondaryPhase,
                        accentPrimary = neonGlow,
                        accentSecondary = secondaryGlow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.68f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // ---- PORTRAIT DESK / BEDSIDE STANDBY LAYOUT ----
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // Top: Giant StandBy Digital Clock + Date
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 24.dp)
                ) {
                    Text(
                        text = clockDate.uppercase(Locale("id", "ID")),
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 2.4.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = neonGlow.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = clockTime,
                            color = Color(0xFFF6F7FB),
                            fontSize = 84.sp,
                            lineHeight = 84.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-2).sp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = clockSeconds,
                            color = neonGlow.copy(alpha = 0.78f),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }

                // Center: Aesthetic Full-Width Audio Visualizer
                StandByVisualizerStage(
                    style = visualizerStyle,
                    artworkUrl = track.artworkUrl,
                    trackTitle = track.title,
                    isPlaying = playerState.isPlaying,
                    phase = animPhase,
                    phase2 = secondaryPhase,
                    accentPrimary = neonGlow,
                    accentSecondary = secondaryGlow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(235.dp)
                )

                // Bottom: Track Info + Synced Lyric Line
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 48.dp)
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.68f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    StandByLyricTicker(
                        activeLyric = activeLyric,
                        fallbackSubtitle = if (playerState.isPlaying) "Mode Meja • Ketuk layar untuk kontrol" else "Dijeda • Ketuk layar untuk kontrol",
                        accentColor = neonGlow,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // ---- AUTO-HIDING TOP HUD: Visualizer Style Switcher & Exit ----
        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn(tween(220)) + slideInVertically { -it / 2 },
            exit = fadeOut(tween(260)) + slideOutVertically { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StandByVisualizerStyle.entries.forEach { style ->
                        val selected = visualizerStyle == style
                        val icon = when (style) {
                            StandByVisualizerStyle.NEON_SPECTRUM -> Icons.Rounded.Equalizer
                            StandByVisualizerStyle.OSCILLOSCOPE -> Icons.Rounded.GraphicEq
                            StandByVisualizerStyle.LIGHT_RING -> Icons.Rounded.AutoAwesome
                            StandByVisualizerStyle.FULLSCREEN_LYRICS -> Icons.Rounded.Lyrics
                        }
                        Surface(
                            onClick = {
                                visualizerStyle = style
                                hudInteractionTick++
                            },
                            shape = RoundedCornerShape(50),
                            color = if (selected) neonGlow else Color.White.copy(alpha = 0.12f),
                            border = BorderStroke(
                                0.8.dp,
                                if (selected) Color.Transparent else Color.White.copy(alpha = 0.18f)
                            ),
                            modifier = Modifier.testTag("standby_style_${style.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (selected) Color(0xFF0A0A0D) else Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = style.label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (selected) Color(0xFF0A0A0D) else Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Surface(
                    onClick = onExitStandBy,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.14f),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("exit_standby_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Keluar Mode Meja",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // ---- AUTO-HIDING BOTTOM HUD: Subtle Transport Controls + Waveform ----
        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn(tween(220)) + slideInVertically { it / 2 },
            exit = fadeOut(tween(260)) + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xCC101116),
                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.14f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val progress = if (playerState.durationMs > 0L) {
                        (playerState.positionMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    WaveformSeekbar(
                        progress = progress,
                        isPlaying = playerState.isPlaying,
                        trackId = track.id,
                        activeColor = neonGlow,
                        inactiveColor = Color.White.copy(alpha = 0.22f),
                        onSeekProgress = { frac ->
                            hudInteractionTick++
                            onSeek((frac * playerState.durationMs).toLong())
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (visualizerStyle == StandByVisualizerStyle.FULLSCREEN_LYRICS) {
                        val lyricsState = playerState.lyricsState
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = {
                                    hudInteractionTick++
                                    onToggleTranslation()
                                },
                                shape = RoundedCornerShape(50),
                                color = if (lyricsState.showTranslation) neonGlow.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(
                                    0.8.dp,
                                    if (lyricsState.showTranslation) neonGlow else Color.White.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier.testTag("standby_lyrics_translate_chip")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (lyricsState.isTranslating) {
                                        CircularProgressIndicator(
                                            color = neonGlow,
                                            strokeWidth = 1.8.dp,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Translate,
                                            contentDescription = null,
                                            tint = if (lyricsState.showTranslation) neonGlow else Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = if (lyricsState.isTranslating) "Menerjemahkan..." else "Terjemahan ID",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (lyricsState.showTranslation) neonGlow else Color.White
                                    )
                                }
                            }

                            if (lyricsState.syncedLines.isNotEmpty()) {
                                Surface(
                                    onClick = {
                                        hudInteractionTick++
                                        fullLyricsListMode = !fullLyricsListMode
                                    },
                                    shape = RoundedCornerShape(50),
                                    color = if (fullLyricsListMode) neonGlow.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f),
                                    border = BorderStroke(
                                        0.8.dp,
                                        if (fullLyricsListMode) neonGlow else Color.White.copy(alpha = 0.16f)
                                    ),
                                    modifier = Modifier.testTag("standby_lyrics_layout_chip")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (fullLyricsListMode) Icons.Rounded.Lyrics else Icons.Rounded.FormatListBulleted,
                                            contentDescription = null,
                                            tint = if (fullLyricsListMode) neonGlow else Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (fullLyricsListMode) "Mode Fokus Besar" else "Mode Daftar Lirik",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (fullLyricsListMode) neonGlow else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                hudInteractionTick++
                                onToggleLike()
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Sukai lagu",
                                tint = if (isLiked) neonGlow else Color.White.copy(alpha = 0.72f)
                            )
                        }
                        IconButton(
                            onClick = {
                                hudInteractionTick++
                                onPrevious()
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = "Lagu sebelumnya",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Surface(
                            onClick = {
                                hudInteractionTick++
                                onTogglePlayPause()
                            },
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier.size(52.dp).testTag("standby_play_pause")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Putar atau jeda",
                                    tint = Color(0xFF0A0A0D),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                hudInteractionTick++
                                onNext()
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Lagu berikutnya",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.size(44.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StandByLyricTicker(
    activeLyric: String?,
    fallbackSubtitle: String,
    accentColor: Color,
    textAlign: TextAlign
) {
    AnimatedContent(
        targetState = activeLyric ?: fallbackSubtitle,
        transitionSpec = {
            (slideInVertically { it / 2 } + fadeIn(tween(240))) togetherWith
                (slideOutVertically { -it / 2 } + fadeOut(tween(180)))
        },
        label = "standby_lyric_ticker"
    ) { line ->
        val isRealLyric = activeLyric != null
        Text(
            text = if (isRealLyric) "♪ $line" else line,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = if (isRealLyric) FontWeight.SemiBold else FontWeight.Normal,
                lineHeight = 22.sp
            ),
            color = if (isRealLyric) lerp(accentColor, Color.White, 0.55f) else Color.White.copy(alpha = 0.50f),
            textAlign = textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StandByVisualizerStage(
    style: StandByVisualizerStyle,
    artworkUrl: String?,
    trackTitle: String,
    isPlaying: Boolean,
    phase: Float,
    phase2: Float,
    accentPrimary: Color,
    accentSecondary: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (style) {
            StandByVisualizerStyle.NEON_SPECTRUM -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    AlbumArtImage(
                        url = artworkUrl,
                        contentDescription = trackTitle,
                        modifier = Modifier.size(72.dp),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val barCount = 34
                        val step = size.width / barCount
                        val barW = (step * 0.56f).coerceAtLeast(3.dp.toPx())
                        val baselineY = size.height * 0.72f
                        val maxUp = size.height * 0.66f
                        val activity = if (isPlaying) 1f else 0.18f

                        for (i in 0 until barCount) {
                            val centerDist = abs((i - (barCount - 1) / 2f) / (barCount / 2f))
                            val envelope = (1f - centerDist * 0.62f).coerceAtLeast(0.22f)
                            val w1 = (sin(phase * 2.0 + i * 0.48) * 0.5 + 0.5).toFloat()
                            val w2 = (sin(phase2 * 3.0 - i * 0.72) * 0.5 + 0.5).toFloat()
                            val amp = ((w1 * 0.58f + w2 * 0.42f) * envelope * activity).coerceIn(0.08f, 1f)
                            val h = (amp * maxUp).coerceAtLeast(6.dp.toPx())
                            val x = i * step + (step - barW) / 2f
                            val barColor = lerp(accentPrimary, accentSecondary, i.toFloat() / barCount)

                            // Soft outer neon glow
                            drawRoundRect(
                                color = barColor.copy(alpha = 0.22f),
                                topLeft = Offset(x - 2f, baselineY - h - 4f),
                                size = Size(barW + 4f, h + 6f),
                                cornerRadius = CornerRadius(barW, barW)
                            )
                            // Main bar
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.White.copy(alpha = 0.92f), barColor),
                                    startY = baselineY - h,
                                    endY = baselineY
                                ),
                                topLeft = Offset(x, baselineY - h),
                                size = Size(barW, h),
                                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                            )
                            // Mirrored desk reflection below baseline
                            val refH = h * 0.34f
                            drawRoundRect(
                                color = barColor.copy(alpha = 0.22f),
                                topLeft = Offset(x, baselineY + 4.dp.toPx()),
                                size = Size(barW, refH),
                                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                            )
                        }
                    }
                }
            }

            StandByVisualizerStyle.OSCILLOSCOPE -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cy = size.height * 0.52f
                        val points = 96
                        val dx = size.width / points
                        val ampScale = if (isPlaying) size.height * 0.30f else size.height * 0.05f

                        val layers = listOf(
                            Triple(accentSecondary.copy(alpha = 0.32f), 5.dp.toPx(), 1.15f),
                            Triple(accentPrimary.copy(alpha = 0.65f), 3.dp.toPx(), 1.0f),
                            Triple(Color.White.copy(alpha = 0.92f), 1.6.dp.toPx(), 0.84f)
                        )
                        layers.forEachIndexed { layerIdx, (color, strokeW, mult) ->
                            val path = Path()
                            for (i in 0..points) {
                                val t = i.toFloat() / points
                                val x = i * dx
                                // Smooth Hann window so wave tapers neatly at both edges
                                val window = sin(t * PI).toFloat()
                                val wave = sin(t * 4.5 * PI + phase * (1.2 + layerIdx * 0.35)) * 0.60 +
                                    sin(t * 9.0 * PI - phase2 * (1.6 + layerIdx * 0.25)) * 0.40
                                val y = cy + (wave.toFloat() * ampScale * mult * window)
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(
                                path = path,
                                color = color,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round)
                            )
                        }
                    }
                    AlbumArtImage(
                        url = artworkUrl,
                        contentDescription = trackTitle,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape),
                        shape = CircleShape
                    )
                }
            }

            StandByVisualizerStyle.LIGHT_RING -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(216.dp)) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val baseR = size.minDimension * 0.28f
                        val activity = if (isPlaying) 1f else 0.16f

                        // Soft breathing aurora halo
                        val pulseR = baseR + (18.dp.toPx() + 8.dp.toPx() * sin(phase)) * activity
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentPrimary.copy(alpha = 0.34f * activity + 0.1f),
                                    accentSecondary.copy(alpha = 0.12f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = size.minDimension * 0.50f
                            ),
                            radius = size.minDimension * 0.50f,
                            center = center
                        )

                        // Concentric glowing rings
                        drawCircle(
                            color = accentPrimary.copy(alpha = 0.45f),
                            radius = pulseR,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )

                        // 56 radial frequency spikes around the album circle
                        val spikes = 56
                        for (i in 0 until spikes) {
                            val angle = (i.toDouble() / spikes) * 2.0 * PI
                            val w = (sin(phase * 2.0 + i * 0.45) * 0.5 + 0.5) * 0.65 +
                                (cos(phase2 * 3.0 - i * 0.7) * 0.5 + 0.5) * 0.35
                            val spikeLen = (6.dp.toPx() + (w.toFloat() * 34.dp.toPx() * activity))
                            val startR = baseR + 6.dp.toPx()
                            val endR = startR + spikeLen
                            val start = Offset(
                                center.x + (cos(angle) * startR).toFloat(),
                                center.y + (sin(angle) * startR).toFloat()
                            )
                            val end = Offset(
                                center.x + (cos(angle) * endR).toFloat(),
                                center.y + (sin(angle) * endR).toFloat()
                            )
                            val c = lerp(accentPrimary, accentSecondary, (i % 28) / 28f)
                            drawLine(
                                color = c.copy(alpha = 0.88f),
                                start = start,
                                end = end,
                                strokeWidth = 2.6.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    AlbumArtImage(
                        url = artworkUrl,
                        contentDescription = trackTitle,
                        modifier = Modifier.size(108.dp),
                        shape = CircleShape
                    )
                }
            }

            StandByVisualizerStyle.FULLSCREEN_LYRICS -> Unit
        }
    }
}
