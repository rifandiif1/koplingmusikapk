package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    onClearHistory: () -> Unit,
    onStartArtistRadio: ((String, Track?) -> Unit)? = null,
    onPlayNext: ((Track) -> Unit)? = null,
    onAddToQueue: ((Track) -> Unit)? = null,
    swipeQueueLabel: String = "Antrean",
    onShareStory: ((String, List<Track>) -> Unit)? = null
) {
    BackHandler(onBack = onBack)

    val accentColor = detailState.accentHex?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background
    val listState = rememberLazyListState()

    val collapseProgress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 420f).coerceIn(0f, 1f)
        }
    }

    val totalDurationSummary = remember(detailState.tracks) {
        val totalMs = detailState.tracks.sumOf { it.durationMs.coerceAtLeast(0L) }
        val totalMin = (totalMs / 60_000L).toInt()
        val hours = totalMin / 60
        val mins = totalMin % 60
        when {
            detailState.tracks.isEmpty() -> null
            hours > 0 -> "${detailState.tracks.size} lagu • $hours jam $mins mnt"
            totalMin > 0 -> "${detailState.tracks.size} lagu • $totalMin menit"
            else -> "${detailState.tracks.size} lagu"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.20f * (1f - collapseProgress * 0.6f)),
                        bgColor,
                        bgColor
                    ),
                    endY = 860f
                )
            )
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 68.dp,
                bottom = bottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ---- Parallax Hero Artwork & Metadata ----
            item(key = "detail_hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp)
                        .graphicsLayer {
                            translationY = -collapseProgress * 36f
                            alpha = (1f - collapseProgress * 0.45f).coerceIn(0.45f, 1f)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val artShape = if (detailState.type is CollectionDetailType.ArtistCollection) {
                        CircleShape
                    } else {
                        RoundedCornerShape(20.dp)
                    }
                    Surface(
                        modifier = Modifier
                            .size(196.dp)
                            .graphicsLayer {
                                val s = 1f - (collapseProgress * 0.14f)
                                scaleX = s
                                scaleY = s
                            },
                        shape = artShape,
                        shadowElevation = 14.dp,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        AlbumArtImage(
                            url = detailState.coverUrl,
                            contentDescription = detailState.title,
                            modifier = Modifier.fillMaxSize(),
                            shape = artShape
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = detailState.title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
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
                    if (totalDurationSummary != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = totalDurationSummary,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (detailState.tracks.isNotEmpty()) {
                            GlassActionPill(
                                label = "Putar",
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
                        if (detailState.type is CollectionDetailType.ArtistCollection && onStartArtistRadio != null) {
                            GlassActionPill(
                                label = "Radio",
                                icon = Icons.Rounded.Radio,
                                highlighted = false,
                                onClick = {
                                    onStartArtistRadio(
                                        detailState.title,
                                        detailState.tracks.firstOrNull()
                                    )
                                },
                                modifier = Modifier.testTag("detail_artist_radio")
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
                    Spacer(modifier = Modifier.height(10.dp))
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
                                CollectionDetailType.Downloads -> "Ketuk ikon Unduh di pemutar atau menu lagu untuk memutar penuh tanpa internet."
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
                        onSwipeRight = onPlayNext?.let { cb -> { cb(track) } },
                        onSwipeLeft = onAddToQueue?.let { cb -> { cb(track) } },
                        swipeLeftLabel = swipeQueueLabel,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }

        // ---- Sticky Collapsing Top Bar ----
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = (collapseProgress * 0.96f).coerceIn(0f, 0.96f)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(
                        alpha = (0.82f - collapseProgress * 0.35f).coerceIn(0.45f, 0.85f)
                    ),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("detail_back_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                AnimatedVisibility(
                    visible = collapseProgress > 0.62f,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = detailState.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (collapseProgress <= 0.62f) {
                    Spacer(modifier = Modifier.weight(1f))
                }

                AnimatedVisibility(
                    visible = collapseProgress > 0.72f && detailState.tracks.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        onClick = { onPlayAll(detailState.tracks, false) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Putar semua",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                if (onShareStory != null && detailState.tracks.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        onClick = { onShareStory(detailState.title, detailState.tracks) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(
                            alpha = (0.82f - collapseProgress * 0.35f).coerceIn(0.45f, 0.85f)
                        ),
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("detail_share_story_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = "Buat kartu Story untuk koleksi ini",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
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
    }
}
