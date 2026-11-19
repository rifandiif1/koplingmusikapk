package com.example.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AlbumArtImage
import com.example.ui.components.formatDuration
import com.example.viewmodel.PlayerUiState

/**
 * High-contrast, distraction-free Driving Mode overlay with extra-large touch targets (84dp - 104dp)
 * for safe glancing and effortless control while on the road.
 */
@Composable
fun DrivingModeOverlay(
    playerState: PlayerUiState,
    isLiked: Boolean,
    accentPrimary: Color,
    activeRoomCode: String?,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleLike: () -> Unit,
    onExitDrivingMode: () -> Unit
) {
    BackHandler(onBack = onExitDrivingMode)
    val track = playerState.currentTrack ?: return
    val duration = playerState.durationMs.coerceAtLeast(1L)
    val progress = (playerState.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accentPrimary.copy(alpha = 0.22f),
                        Color(0xFF040507),
                        Color(0xFF000000)
                    )
                )
            )
            .testTag("driving_mode_overlay")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .widthIn(max = 600.dp)
                .align(Alignment.Center)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ---- Header Badge & Exit Button ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accentPrimary.copy(alpha = 0.18f),
                    border = BorderStroke(1.2.dp, accentPrimary.copy(alpha = 0.55f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DirectionsCar,
                            contentDescription = null,
                            tint = accentPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "MODE BERKENDARA",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            color = accentPrimary
                        )
                    }
                }

                if (activeRoomCode != null) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF161A22),
                        border = BorderStroke(1.dp, accentPrimary.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Groups, contentDescription = null, tint = accentPrimary, modifier = Modifier.size(16.dp))
                            Text(
                                text = activeRoomCode,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // ---- Artwork & High-Contrast Typography ----
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(2.dp, accentPrimary.copy(alpha = 0.55f)),
                    shadowElevation = 20.dp,
                    modifier = Modifier.size(196.dp)
                ) {
                    AlbumArtImage(
                        url = track.artworkUrl,
                        contentDescription = track.title,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = track.title,
                        fontSize = 28.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFB7C2D2),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Clear high-contrast progress bar
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(50)),
                        color = accentPrimary,
                        trackColor = Color(0xFF232833)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(playerState.positionMs),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFB7C2D2)
                        )
                        Text(
                            text = formatDuration(duration),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFB7C2D2)
                        )
                    }
                }
            }

            // ---- Extra-Large Driving Controls ----
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onPrevious,
                        shape = CircleShape,
                        color = Color(0xFF171C24),
                        border = BorderStroke(1.5.dp, Color(0xFF313A4A)),
                        modifier = Modifier
                            .size(84.dp)
                            .testTag("driving_prev_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = "Lagu sebelumnya",
                                tint = Color.White,
                                modifier = Modifier.size(46.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onTogglePlayPause,
                        shape = CircleShape,
                        color = accentPrimary,
                        shadowElevation = 16.dp,
                        modifier = Modifier
                            .size(108.dp)
                            .testTag("driving_play_pause_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (playerState.isBuffering) {
                                CircularProgressIndicator(
                                    color = Color(0xFF0A0B0F),
                                    strokeWidth = 4.dp,
                                    modifier = Modifier.size(38.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (playerState.isPlaying) "Jeda" else "Putar",
                                    tint = Color(0xFF0A0B0F),
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = onNext,
                        shape = CircleShape,
                        color = Color(0xFF171C24),
                        border = BorderStroke(1.5.dp, Color(0xFF313A4A)),
                        modifier = Modifier
                            .size(84.dp)
                            .testTag("driving_next_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Lagu berikutnya",
                                tint = Color.White,
                                modifier = Modifier.size(46.dp)
                            )
                        }
                    }
                }

                // Bottom wide action buttons (Like + Exit)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        onClick = onToggleLike,
                        shape = RoundedCornerShape(18.dp),
                        color = if (isLiked) accentPrimary.copy(alpha = 0.22f) else Color(0xFF141820),
                        border = BorderStroke(1.2.dp, if (isLiked) accentPrimary else Color(0xFF2B3444)),
                        modifier = Modifier
                            .height(60.dp)
                            .width(72.dp)
                            .testTag("driving_like_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Sukai lagu",
                                tint = if (isLiked) accentPrimary else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onExitDrivingMode,
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF1B202B),
                        border = BorderStroke(1.2.dp, Color(0xFF354052)),
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .testTag("driving_exit_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Keluar Mode Berkendara",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
