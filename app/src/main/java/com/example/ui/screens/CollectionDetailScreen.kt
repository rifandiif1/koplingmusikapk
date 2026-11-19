package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.CollectionDetailType
import com.example.model.Track
import com.example.ui.components.AlbumArtImage
import com.example.ui.components.GlassActionPill
import com.example.ui.components.TrackRowItem
import com.example.viewmodel.CollectionDetailState

@Composable
fun CollectionDetailScreen(
    detailState: CollectionDetailState,
    currentTrackId: String?,
    isPlaying: Boolean,
    likedIds: Set<String>,
    bottomContentPadding: Dp,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: (List<Track>, Boolean) -> Unit,
    onToggleLike: (Track) -> Unit,
    onTrackOptions: (Track) -> Unit,
    onImportLocalAudio: () -> Unit,
    onClearHistory: () -> Unit
) {
    BackHandler(onBack = onBack)

    val accentColor = detailState.accentHex?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(accentColor.copy(alpha = 0.24f), bgColor, bgColor),
                    endY = 820f
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = bottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- Top Bar ----
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onBack,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.size(44.dp).testTag("detail_back_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (detailState.type is CollectionDetailType.History && detailState.tracks.isNotEmpty()) {
                        IconButton(onClick = onClearHistory) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = "Bersihkan riwayat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ---- Hero Artwork & Metadata ----
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(188.dp),
                        shape = RoundedCornerShape(28.dp),
                        shadowElevation = 18.dp,
                        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.45f))
                    ) {
                        AlbumArtImage(
                            url = detailState.coverUrl,
                            contentDescription = detailState.title,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = detailState.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = detailState.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (detailState.tracks.isNotEmpty()) {
                            GlassActionPill(
                                label = "Putar Semua",
                                icon = Icons.Rounded.PlayArrow,
                                highlighted = true,
                                onClick = { onPlayAll(detailState.tracks, false) },
                                modifier = Modifier.testTag("detail_play_all")
                            )
                            GlassActionPill(
                                label = "Acak",
                                icon = Icons.Rounded.Shuffle,
                                highlighted = false,
                                onClick = { onPlayAll(detailState.tracks, true) },
                                modifier = Modifier.testTag("detail_shuffle")
                            )
                        }
                        if (detailState.type is CollectionDetailType.LocalAudio) {
                            GlassActionPill(
                                label = "Impor",
                                icon = Icons.Rounded.Add,
                                highlighted = detailState.tracks.isEmpty(),
                                onClick = onImportLocalAudio
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // ---- Content List / Empty / Loading ----
            if (detailState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else if (detailState.tracks.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Belum ada lagu di sini",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (detailState.type) {
                                CollectionDetailType.LikedSongs -> "Ketuk ikon hati pada lagu mana pun untuk menyimpannya di sini."
                                CollectionDetailType.LocalAudio -> "Ketuk tombol Impor untuk memilih file MP3, M4A, atau FLAC dari perangkatmu."
                                CollectionDetailType.History -> "Lagu yang kamu dengarkan akan muncul di riwayat ini."
                                else -> "Tambahkan lagu melalui menu titik tiga pada lagu mana pun."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                itemsIndexed(detailState.tracks, key = { idx, t -> "dt_${idx}_${t.id}" }) { idx, track ->
                    TrackRowItem(
                        track = track,
                        rankNumber = idx + 1,
                        isCurrent = track.id == currentTrackId,
                        isPlaying = isPlaying && track.id == currentTrackId,
                        isLiked = track.id in likedIds,
                        onClick = { onPlayTrack(track, detailState.tracks) },
                        onLikeClick = { onToggleLike(track) },
                        onMoreClick = { onTrackOptions(track) },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}
