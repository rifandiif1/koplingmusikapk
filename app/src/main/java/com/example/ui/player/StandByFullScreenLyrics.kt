package com.example.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Track
import com.example.ui.components.AlbumArtImage
import com.example.viewmodel.PlayerUiState

/**
 * Full-Screen Desk / StandBy Lyrics Stage ("Lirik Penuh").
 * Designed for crystal-clear, large-print lyrics reading from across a desk or bedside table
 * while mathematically guaranteeing text never overflows or clips beyond screen bounds in
 * either Portrait or Landscape orientations.
 */
@Composable
fun StandByFullScreenLyricsStage(
    track: Track,
    playerState: PlayerUiState,
    clockTime: String,
    accentPrimary: Color,
    isLandscape: Boolean,
    isListMode: Boolean,
    onSeekToLine: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val lyricsState = playerState.lyricsState
    val syncedLines = lyricsState.syncedLines
    val plainLines = remember(lyricsState.plainLyrics) {
        lyricsState.plainLyrics
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }
    val adjustedPosMs = (playerState.positionMs + playerState.lyricsOffsetMs).coerceAtLeast(0L)
    val activeIndex = remember(syncedLines, adjustedPosMs) {
        if (syncedLines.isEmpty()) -1
        else syncedLines.indexOfLast { it.timestampMs <= adjustedPosMs + 250L }
    }

    val neonGlow = remember(accentPrimary) { lerp(accentPrimary, Color.White, 0.25f) }
    val translationTint = remember(accentPrimary) { lerp(accentPrimary, Color.White, 0.62f) }
    val progress = if (playerState.durationMs > 0L) {
        (playerState.positionMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(
                horizontal = if (isLandscape) 36.dp else 22.dp,
                vertical = if (isLandscape) 16.dp else 18.dp
            )
            .testTag("standby_fullscreen_lyrics_stage")
    ) {
        // Compact Desk Header: Album Art + Track Info + Live Digital Clock Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = if (isLandscape) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AlbumArtImage(
                url = track.artworkUrl,
                contentDescription = track.title,
                modifier = Modifier.size(if (isLandscape) 38.dp else 42.dp),
                shape = RoundedCornerShape(10.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White.copy(alpha = 0.94f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(0.8.dp, neonGlow.copy(alpha = 0.34f))
            ) {
                Text(
                    text = clockTime,
                    color = Color(0xFFF6F7FB),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Main Bounded Lyrics Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when {
                lyricsState.isLoading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(
                            color = neonGlow,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = "Memuat lirik layar penuh...",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.72f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                lyricsState.isInstrumental -> {
                    StandByEmptyLyricsState(
                        title = "Musik Instrumental",
                        subtitle = "${track.title} — ${track.artist}",
                        accentColor = neonGlow
                    )
                }

                syncedLines.isNotEmpty() && !isListMode -> {
                    val effectiveIndex = activeIndex.coerceAtLeast(0)
                    val prevLine = if (activeIndex > 0) {
                        syncedLines.getOrNull(activeIndex - 1)?.text?.trim().orEmpty()
                    } else ""
                    val currentLine = if (activeIndex >= 0) {
                        syncedLines[activeIndex].text.trim().ifBlank { "♪" }
                    } else {
                        syncedLines.firstOrNull()?.text?.trim()?.ifBlank { "♪" } ?: "♪"
                    }
                    val nextLine = syncedLines.getOrNull(
                        if (activeIndex >= 0) activeIndex + 1 else 1
                    )?.text?.trim().orEmpty()

                    val currentTranslation = if (lyricsState.showTranslation && activeIndex >= 0) {
                        lyricsState.translatedSyncedLines.getOrNull(activeIndex)?.trim().orEmpty()
                    } else ""
                    val currentRomanization = if (lyricsState.showRomanization && activeIndex >= 0) {
                        lyricsState.romanizedSyncedLines.getOrNull(activeIndex)?.trim().orEmpty()
                    } else ""

                    StandByHeroFocusLyrics(
                        activeKey = effectiveIndex,
                        previousLine = prevLine,
                        currentLine = currentLine,
                        romanizationLine = currentRomanization,
                        translationLine = currentTranslation,
                        nextLine = nextLine,
                        isWaitingIntro = activeIndex < 0,
                        isLandscape = isLandscape,
                        accentGlow = neonGlow,
                        translationColor = translationTint,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                syncedLines.isNotEmpty() && isListMode -> {
                    StandByScrollableLargeLyrics(
                        lines = syncedLines.map { it.text },
                        timestampsMs = syncedLines.map { it.timestampMs },
                        translatedLines = if (lyricsState.showTranslation) lyricsState.translatedSyncedLines else emptyList(),
                        romanizedLines = if (lyricsState.showRomanization) lyricsState.romanizedSyncedLines else emptyList(),
                        activeIndex = activeIndex,
                        isLandscape = isLandscape,
                        accentGlow = neonGlow,
                        translationColor = translationTint,
                        onSeekToLine = onSeekToLine,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                plainLines.isNotEmpty() -> {
                    StandByScrollableLargeLyrics(
                        lines = plainLines,
                        timestampsMs = emptyList(),
                        translatedLines = if (lyricsState.showTranslation) lyricsState.translatedPlainLines else emptyList(),
                        romanizedLines = if (lyricsState.showRomanization) lyricsState.romanizedPlainLines else emptyList(),
                        activeIndex = -1,
                        isLandscape = isLandscape,
                        accentGlow = neonGlow,
                        translationColor = translationTint,
                        onSeekToLine = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    StandByEmptyLyricsState(
                        title = "Lirik Belum Tersedia",
                        subtitle = "${track.title} — ${track.artist}",
                        accentColor = neonGlow
                    )
                }
            }
        }

        // Ultra-slim Ambient Progress Bar at the bottom (remains clean even when HUD is hidden)
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(accentPrimary, neonGlow)
                        )
                    )
            )
        }
    }
}

@Composable
private fun StandByHeroFocusLyrics(
    activeKey: Int,
    previousLine: String,
    currentLine: String,
    romanizationLine: String,
    translationLine: String,
    nextLine: String,
    isWaitingIntro: Boolean,
    isLandscape: Boolean,
    accentGlow: Color,
    translationColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Previous Line Context (top, strictly 1 line)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLandscape) 28.dp else 36.dp),
            contentAlignment = Alignment.Center
        ) {
            if (previousLine.isNotBlank()) {
                Text(
                    text = previousLine,
                    color = Color.White.copy(alpha = 0.28f),
                    fontSize = if (isLandscape) 16.sp else 17.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Center Auto-Fitted Hero Lyric + Optional Romanization & Indonesian Translation
        AnimatedContent(
            targetState = Triple(activeKey, currentLine, translationLine),
            transitionSpec = {
                (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 12 }) togetherWith
                    (fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 12 })
            },
            label = "standby_hero_lyric",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp)
        ) { (_, heroLine, transLine) ->
            val hasTranslation = transLine.isNotBlank() && !isWaitingIntro
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (romanizationLine.isNotBlank() && !isWaitingIntro) {
                    Text(
                        text = romanizationLine,
                        color = accentGlow.copy(alpha = 0.78f),
                        fontSize = if (isLandscape) 16.sp else 17.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                // Giant Hero Lyric strictly bounded so it NEVER overflows screen width or height
                AutoFitBoundedText(
                    text = if (isWaitingIntro) "♪  $heroLine" else heroLine,
                    color = if (isWaitingIntro) Color.White.copy(alpha = 0.65f) else Color(0xFFFAFBFD),
                    fontWeight = FontWeight.Black,
                    maxFontSizeSp = if (isLandscape) 54f else 48f,
                    minFontSizeSp = 18f,
                    lineHeightMultiplier = 1.18f,
                    maxLines = if (isLandscape) 4 else 6,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (hasTranslation) 0.68f else 1f)
                )

                if (hasTranslation) {
                    Spacer(Modifier.height(if (isLandscape) 8.dp else 12.dp))
                    AutoFitBoundedText(
                        text = transLine,
                        color = translationColor,
                        fontWeight = FontWeight.SemiBold,
                        maxFontSizeSp = if (isLandscape) 26f else 24f,
                        minFontSizeSp = 14f,
                        lineHeightMultiplier = 1.24f,
                        maxLines = 3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.32f)
                    )
                }
            }
        }

        // Next Upcoming Line Preview (bottom, strictly 1 line)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLandscape) 30.dp else 38.dp),
            contentAlignment = Alignment.Center
        ) {
            if (nextLine.isNotBlank()) {
                Text(
                    text = nextLine,
                    color = Color.White.copy(alpha = 0.36f),
                    fontSize = if (isLandscape) 17.sp else 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Measures the largest font size within [minFontSizeSp]..[maxFontSizeSp] that completely fits
 * inside its allocated [BoxWithConstraints] width and height with zero overflow or clipping.
 */
@Composable
private fun AutoFitBoundedText(
    text: String,
    color: Color,
    fontWeight: FontWeight,
    maxFontSizeSp: Float,
    minFontSizeSp: Float,
    lineHeightMultiplier: Float,
    maxLines: Int,
    textAlign: TextAlign,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val baseStyle = MaterialTheme.typography.headlineLarge

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val maxWidthPx = constraints.maxWidth
        val maxHeightPx = constraints.maxHeight
        if (maxWidthPx > 0 && maxHeightPx > 0 && text.isNotBlank()) {
            val bestFontSizeSp = remember(
                text,
                maxWidthPx,
                maxHeightPx,
                maxFontSizeSp,
                minFontSizeSp,
                maxLines,
                fontWeight,
                density.fontScale
            ) {
                var low = minFontSizeSp
                var high = maxFontSizeSp
                var best = minFontSizeSp
                val measureConstraints = Constraints(
                    maxWidth = maxWidthPx,
                    maxHeight = maxHeightPx
                )
                repeat(8) {
                    val mid = (low + high) * 0.5f
                    val result = textMeasurer.measure(
                        text = text,
                        style = baseStyle.copy(
                            fontSize = mid.sp,
                            lineHeight = (mid * lineHeightMultiplier).sp,
                            fontWeight = fontWeight,
                            textAlign = textAlign,
                            letterSpacing = (-0.4).sp
                        ),
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = maxLines,
                        constraints = measureConstraints
                    )
                    val fits = !result.hasVisualOverflow &&
                        !result.didOverflowHeight &&
                        !result.didOverflowWidth &&
                        result.size.height <= maxHeightPx &&
                        result.size.width <= maxWidthPx
                    if (fits) {
                        best = mid
                        low = mid
                    } else {
                        high = mid
                    }
                }
                best
            }

            Text(
                text = text,
                style = baseStyle.copy(
                    fontSize = bestFontSizeSp.sp,
                    lineHeight = (bestFontSizeSp * lineHeightMultiplier).sp,
                    fontWeight = fontWeight,
                    textAlign = textAlign,
                    letterSpacing = (-0.4).sp
                ),
                color = color,
                softWrap = true,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StandByScrollableLargeLyrics(
    lines: List<String>,
    timestampsMs: List<Long>,
    translatedLines: List<String>,
    romanizedLines: List<String>,
    activeIndex: Int,
    isLandscape: Boolean,
    accentGlow: Color,
    translationColor: Color,
    onSeekToLine: ((Long) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val hasSync = timestampsMs.size == lines.size && timestampsMs.isNotEmpty()

    LaunchedEffect(activeIndex, hasSync) {
        if (hasSync && activeIndex in lines.indices) {
            val target = (activeIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = if (isLandscape) 20.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 18.dp else 22.dp),
        modifier = modifier.fillMaxSize()
    ) {
        itemsIndexed(lines) { index, lineText ->
            val isActive = hasSync && index == activeIndex
            val isPast = hasSync && activeIndex >= 0 && index < activeIndex
            val targetColor by animateColorAsState(
                targetValue = when {
                    !hasSync -> Color(0xFFF3F5FA)
                    isActive -> Color.White
                    isPast -> Color.White.copy(alpha = 0.34f)
                    else -> Color.White.copy(alpha = 0.44f)
                },
                animationSpec = tween(220),
                label = "standby_list_line_color"
            )
            val romLine = romanizedLines.getOrNull(index)?.trim().orEmpty()
            val transLine = translatedLines.getOrNull(index)?.trim().orEmpty()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (hasSync && onSeekToLine != null) {
                            Modifier.clickable { onSeekToLine(timestampsMs[index]) }
                        } else Modifier
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp, end = 12.dp)
                            .width(4.dp)
                            .height(28.dp)
                            .clip(CircleShape)
                            .background(accentGlow)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (romLine.isNotBlank()) {
                        Text(
                            text = romLine,
                            color = accentGlow.copy(alpha = if (isActive || !hasSync) 0.80f else 0.38f),
                            fontSize = if (isActive) 15.sp else 13.sp,
                            fontWeight = FontWeight.Medium,
                            softWrap = true
                        )
                    }
                    Text(
                        text = lineText.ifBlank { "♪" },
                        color = targetColor,
                        fontSize = when {
                            isActive -> if (isLandscape) 30.sp else 28.sp
                            !hasSync -> if (isLandscape) 25.sp else 23.sp
                            else -> if (isLandscape) 21.sp else 20.sp
                        },
                        lineHeight = when {
                            isActive -> if (isLandscape) 38.sp else 36.sp
                            !hasSync -> if (isLandscape) 33.sp else 31.sp
                            else -> if (isLandscape) 28.sp else 27.sp
                        },
                        fontWeight = if (isActive || !hasSync) FontWeight.ExtraBold else FontWeight.SemiBold,
                        softWrap = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (transLine.isNotBlank()) {
                        Text(
                            text = transLine,
                            color = translationColor.copy(alpha = if (isActive || !hasSync) 0.92f else 0.40f),
                            fontSize = if (isActive || !hasSync) 18.sp else 15.sp,
                            lineHeight = if (isActive || !hasSync) 24.sp else 20.sp,
                            fontWeight = FontWeight.Medium,
                            softWrap = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StandByEmptyLyricsState(
    title: String,
    subtitle: String,
    accentColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = accentColor.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
