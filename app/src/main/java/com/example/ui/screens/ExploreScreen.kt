package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AlbumSummary
import com.example.model.KoplingfyMoods
import com.example.model.MoodFilter
import com.example.model.Track
import com.example.ui.components.AlbumArtImage
import com.example.ui.components.GlassActionPill
import com.example.ui.components.SectionHeader
import com.example.ui.components.TrackRowItem
import com.example.viewmodel.HomeUiState

@Composable
fun ExploreScreen(
    homeState: HomeUiState,
    currentTrackId: String?,
    isPlaying: Boolean,
    likedIds: Set<String>,
    bottomContentPadding: Dp,
    onMoodCardClick: (MoodFilter) -> Unit,
    onAlbumClick: (AlbumSummary) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: (List<Track>, Boolean) -> Unit,
    onToggleLike: (Track) -> Unit,
    onTrackOptions: (Track) -> Unit,
    onPlayNext: ((Track) -> Unit)? = null,
    onAddToQueue: ((Track) -> Unit)? = null,
    swipeQueueLabel: String = "Antrean"
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // ---- Clean Editorial Header ----
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 14.dp)
            ) {
                Text(
                    text = "Jelajah",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Tangga lagu dunia, genre, dan rilisan terbaru",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---- Moods & Genres 2-Column Editorial Grid ----
        item {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionHeader(
                    title = "Mood & Genre",
                    subtitle = "Pilih atmosfer yang cocok dengan harimu"
                )
                val rows = KoplingfyMoods.allMoods.chunked(2)
                rows.forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { mood ->
                            MoodGradientTile(
                                mood = mood,
                                onClick = { onMoodCardClick(mood) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // ---- Featured Albums Strip (Frameless Artwork-First) ----
        if (homeState.featuredAlbums.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = "Album Teratas",
                        subtitle = "Rilisan paling banyak diputar",
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(homeState.featuredAlbums, key = { "exp_alb_${it.id}" }) { album ->
                            Column(
                                modifier = Modifier
                                    .width(148.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onAlbumClick(album) }
                            ) {
                                AlbumArtImage(
                                    url = album.artworkUrl,
                                    contentDescription = album.title,
                                    modifier = Modifier.size(148.dp),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = album.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = album.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- Global Top Charts Ranked List ----
        if (homeState.chartTracks.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.TrendingUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Top 15 Global",
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Lagu paling populer hari ini",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassActionPill(
                            label = "Putar Semua",
                            icon = Icons.Rounded.PlayArrow,
                            highlighted = true,
                            onClick = { onPlayAll(homeState.chartTracks, false) }
                        )
                        GlassActionPill(
                            label = "Acak",
                            icon = Icons.Rounded.Shuffle,
                            highlighted = false,
                            onClick = { onPlayAll(homeState.chartTracks, true) }
                        )
                    }
                }
            }

            itemsIndexed(homeState.chartTracks.take(15), key = { _, t -> "top_${t.id}" }) { index, track ->
                TrackRowItem(
                    track = track,
                    rankNumber = index + 1,
                    isCurrent = track.id == currentTrackId,
                    isPlaying = isPlaying && track.id == currentTrackId,
                    isLiked = track.id in likedIds,
                    onClick = { onPlayTrack(track, homeState.chartTracks) },
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
}

@Composable
private fun MoodGradientTile(
    mood: MoodFilter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Authentic YouTube Music / Metrolist genre tile: neutral matte surface with left color bar
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .height(64.dp)
            .testTag("explore_mood_${mood.id}")
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxSize()
                    .background(Color(mood.startColor))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = mood.label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = mood.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
