package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.PlayerUiState

/**
 * In-app fallback for the fixed, NON-CLICKABLE Dynamic Island pill positioned right below the
 * status bar. Used automatically whenever Android's system-wide overlay permission
 * (SYSTEM_ALERT_WINDOW) is not yet granted; when overlay permission IS granted,
 * DynamicIslandWindowController renders this pill system-wide above ALL apps.
 */
@Composable
fun DynamicIslandLyricsOverlay(
    playerState: PlayerUiState,
    accentColor: Color,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val track = playerState.currentTrack
    val lyricsState = playerState.lyricsState
    val syncedLines = lyricsState.syncedLines
    val adjustedPosMs = (playerState.positionMs + playerState.lyricsOffsetMs).coerceAtLeast(0L)
    val activeIdx = remember(syncedLines, adjustedPosMs) {
        syncedLines.indexOfLast { it.timestampMs <= adjustedPosMs + 250L }
    }
    val activeLyric = remember(syncedLines, activeIdx) {
        syncedLines.getOrNull(activeIdx)?.text?.trim()?.takeIf { it.isNotEmpty() }
    }
    val activeTranslation = remember(lyricsState.showTranslation, lyricsState.translatedSyncedLines, activeIdx, activeLyric) {
        if (lyricsState.showTranslation && activeLyric != null) {
            lyricsState.translatedSyncedLines.getOrNull(activeIdx)
                ?.trim()
                ?.takeIf { it.isNotEmpty() && !it.equals(activeLyric, ignoreCase = true) }
        } else null
    }

    val shouldShow = visible &&
        track != null &&
        playerState.isPlaying &&
        !playerState.isBuffering &&
        activeLyric != null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 4.dp, start = 24.dp, end = 24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = shouldShow,
            enter = fadeIn(tween(220)) + slideInVertically { -it / 2 },
            exit = fadeOut(tween(180)) + slideOutVertically { -it / 2 }
        ) {
            val pillShape = RoundedCornerShape(50)
            val luminousBorder = lerp(accentColor, Color.White, 0.32f).copy(alpha = 0.42f)

            // Pure visual Box with NO clickable/pointerInput -> 100% pass-through & non-interactive
            Box(
                modifier = Modifier
                    .widthIn(min = 148.dp, max = 304.dp)
                    .shadow(elevation = 10.dp, shape = pillShape, clip = false)
                    .clip(pillShape)
                    .background(Color(0xEE060609))
                    .border(width = 0.9.dp, color = luminousBorder, shape = pillShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("dynamic_island_lyrics_pill")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AlbumArtImage(
                        url = track?.artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        shape = CircleShape
                    )

                    AnimatedContent(
                        targetState = activeLyric.orEmpty() to activeTranslation,
                        transitionSpec = {
                            (slideInVertically { it / 2 } + fadeIn(tween(200))) togetherWith
                                (slideOutVertically { -it / 2 } + fadeOut(tween(150)))
                        },
                        modifier = Modifier.weight(1f, fill = false),
                        label = "dynamic_island_lyric_text"
                    ) { (lyricText, transText) ->
                        Column {
                            Text(
                                text = lyricText,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color(0xFFF6F7FB),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                            )
                            if (!transText.isNullOrBlank()) {
                                Text(
                                    text = transText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    color = Color(0xFFB8F2D6),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    AudioVisualizerBars(
                        isPlaying = playerState.isPlaying,
                        color = lerp(accentColor, Color.White, 0.35f),
                        barWidth = 2.dp,
                        maxHeight = 10.dp
                    )
                }
            }
        }
    }
}
