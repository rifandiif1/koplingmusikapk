package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AlbumSummary
import com.example.model.ArtistSummary
import com.example.model.KoplingfyMoods
import com.example.model.MoodFilter
import com.example.model.RemotePlaylistSummary
import com.example.model.Track
import com.example.model.YouTubeAccount
import com.example.ui.components.AlbumArtImage
import com.example.ui.components.GlassActionPill
import com.example.ui.components.HomeFeedSkeletonShimmer
import com.example.ui.components.SectionHeader
import com.example.ui.components.TrackRowItem
import com.example.viewmodel.HomeUiState
import java.util.Calendar

@Composable
fun HomeScreen(
    homeState: HomeUiState,
    recentTracks: List<Track>,
    youtubeAccount: YouTubeAccount,
    currentTrackId: String?,
    isPlaying: Boolean,
    likedIds: Set<String>,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    onSelectMood: (MoodFilter) -> Unit,
    onRetry: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: (List<Track>, Boolean) -> Unit,
    onToggleLike: (Track) -> Unit,
    onTrackOptions: (Track) -> Unit,
    onAlbumClick: (AlbumSummary) -> Unit,
    onArtistClick: (ArtistSummary) -> Unit,
    onRemotePlaylistClick: (RemotePlaylistSummary) -> Unit,
    onOpenYouTubeLogin: () -> Unit,
    onOpenListenTogether: () -> Unit,
    activeRoomCode: String? = null,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onPlayNext: ((Track) -> Unit)? = null,
    onAddToQueue: ((Track) -> Unit)? = null,
    swipeQueueLabel: String = "Antrean"
) {
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 4..10 -> "Selamat Pagi"
            in 11..14 -> "Selamat Siang"
            in 15..18 -> "Selamat Sore"
            else -> "Selamat Malam"
        }
    }

    val bgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = bottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ---- Top Brand Header ----
            item {
                HomeTopHeader(
                    greeting = greeting,
                    youtubeAccount = youtubeAccount,
                    activeRoomCode = activeRoomCode,
                    onOpenListenTogether = onOpenListenTogether,
                    onOpenYouTubeLogin = onOpenYouTubeLogin,
                    onOpenStats = onOpenStats,
                    onOpenSettings = onOpenSettings
                )
            }

            // ---- Clean YouTube Music / Metrolist Mood Filter Pills ----
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(KoplingfyMoods.allMoods, key = { it.id }) { mood ->
                        val selected = homeState.selectedMood.id == mood.id
                        Surface(
                            onClick = { onSelectMood(mood) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant,
                            border = null,
                            modifier = Modifier.testTag("mood_chip_${mood.id}")
                        ) {
                            Text(
                                text = mood.label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // ---- Editorial Hero Spotlight Card ----
            item {
                val heroTrack = homeState.quickPicks.firstOrNull() ?: homeState.chartTracks.firstOrNull()
                HeroSpotlightCard(
                    mood = homeState.selectedMood,
                    featuredTrack = heroTrack,
                    onPlayFeatured = {
                        val list = if (homeState.quickPicks.isNotEmpty()) homeState.quickPicks else homeState.chartTracks
                        if (list.isNotEmpty()) onPlayAll(list, false)
                    },
                    onShuffleFeatured = {
                        val list = if (homeState.quickPicks.isNotEmpty()) homeState.quickPicks else homeState.chartTracks
                        if (list.isNotEmpty()) onPlayAll(list, true)
                    }
                )
            }

            // ---- Loading or Error Banner ----
            if (homeState.isLoading) {
                item {
                    HomeFeedSkeletonShimmer()
                }
            } else if (homeState.errorMessage != null) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = homeState.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            GlassActionPill(
                                label = "Muat Ulang",
                                icon = Icons.Rounded.Refresh,
                                highlighted = true,
                                onClick = onRetry
                            )
                        }
                    }
                }
            }

            // ---- Recently Played Strip ----
            if (recentTracks.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(
                            title = "Lanjutkan Mendengar",
                            subtitle = "Lagu yang baru saja kamu putar",
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(recentTracks.distinctBy { it.id }.take(10), key = { "recent_${it.id}" }) { track ->
                                CompactSquareCard(
                                    title = track.title,
                                    subtitle = track.artist,
                                    artworkUrl = track.artworkUrl,
                                    isActive = track.id == currentTrackId,
                                    onClick = { onPlayTrack(track, recentTracks.distinctBy { it.id }) }
                                )
                            }
                        }
                    }
                }
            }

            // ---- Quick Picks (Metrolist signature section) ----
            if (homeState.quickPicks.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Pilihan Cepat",
                        subtitle = homeState.selectedMood.subtitle,
                        actionLabel = "Putar Semua",
                        onActionClick = { onPlayAll(homeState.quickPicks, false) },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                items(homeState.quickPicks.take(6), key = { "qp_${it.id}" }) { track ->
                    TrackRowItem(
                        track = track,
                        isCurrent = track.id == currentTrackId,
                        isPlaying = isPlaying && track.id == currentTrackId,
                        isLiked = track.id in likedIds,
                        onClick = { onPlayTrack(track, homeState.quickPicks) },
                        onLikeClick = { onToggleLike(track) },
                        onMoreClick = { onTrackOptions(track) },
                        onSwipeRight = onPlayNext?.let { cb -> { cb(track) } },
                        onSwipeLeft = onAddToQueue?.let { cb -> { cb(track) } },
                        swipeLeftLabel = swipeQueueLabel,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            // ---- Top Global Charts Carousel ----
            if (homeState.chartTracks.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(
                            title = "Sedang Tren Global",
                            subtitle = "Tangga lagu teratas hari ini",
                            actionLabel = "Acak",
                            onActionClick = { onPlayAll(homeState.chartTracks, true) },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(homeState.chartTracks.take(12), key = { _, t -> "chart_${t.id}" }) { idx, track ->
                                RankedPosterCard(
                                    rank = idx + 1,
                                    track = track,
                                    isActive = track.id == currentTrackId,
                                    onClick = { onPlayTrack(track, homeState.chartTracks) }
                                )
                            }
                        }
                    }
                }
            }

            // ---- Popular Artists Circular Row ----
            if (homeState.featuredArtists.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(
                            title = "Artis Populer",
                            subtitle = "Ketuk untuk mendengar karya terbaiknya",
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(homeState.featuredArtists.take(10), key = { it.id }) { artist ->
                                ArtistAvatarCard(
                                    artist = artist,
                                    onClick = { onArtistClick(artist) }
                                )
                            }
                        }
                    }
                }
            }

            // ---- Featured Albums Showcase ----
            if (homeState.featuredAlbums.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(
                            title = "Album Pilihan",
                            subtitle = "Rilisan berkualitas tinggi",
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(homeState.featuredAlbums.take(10), key = { it.id }) { album ->
                                CompactSquareCard(
                                    title = album.title,
                                    subtitle = album.artist,
                                    artworkUrl = album.artworkUrl,
                                    isActive = false,
                                    cardWidth = 152.dp,
                                    onClick = { onAlbumClick(album) }
                                )
                            }
                        }
                    }
                }
            }

            // ---- Mix & Playlist YouTube Music ----
            if (homeState.featuredPlaylists.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(
                            title = "Mix & Playlist YouTube Music",
                            subtitle = "Kurasi otomatis dan komunitas",
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(homeState.featuredPlaylists.take(10), key = { it.id }) { playlist ->
                                CompactSquareCard(
                                    title = playlist.title,
                                    subtitle = playlist.author.ifBlank { "YouTube Music" },
                                    artworkUrl = playlist.artworkUrl,
                                    isActive = false,
                                    cardWidth = 152.dp,
                                    onClick = { onRemotePlaylistClick(playlist) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTopHeader(
    greeting: String,
    youtubeAccount: YouTubeAccount,
    activeRoomCode: String?,
    onOpenListenTogether: () -> Unit,
    onOpenYouTubeLogin: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (youtubeAccount.isLoggedIn) "$greeting, ${youtubeAccount.name.substringBefore(" ")}" else greeting,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Koplingfy",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (activeRoomCode != null) {
                Surface(
                    onClick = onOpenListenTogether,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("open_listen_together_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = "Play Bersama Aktif",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = activeRoomCode,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            IconButton(
                onClick = onOpenStats,
                modifier = Modifier.size(44.dp).testTag("open_stats_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoGraph,
                    contentDescription = "Statistik",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(44.dp).testTag("open_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Pengaturan",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            if (youtubeAccount.isLoggedIn) {
                Surface(
                    onClick = onOpenSettings,
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp).testTag("home_account_avatar")
                ) {
                    AlbumArtImage(
                        url = youtubeAccount.avatarUrl,
                        contentDescription = youtubeAccount.name,
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape
                    )
                }
            } else {
                Surface(
                    onClick = onOpenYouTubeLogin,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.testTag("home_login_ytm_pill")
                ) {
                    Text(
                        text = "Masuk",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroSpotlightCard(
    mood: MoodFilter,
    featuredTrack: Track?,
    onPlayFeatured: () -> Unit,
    onShuffleFeatured: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Subtle atmospheric tint from the mood without garish AI wallpaper clash
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(mood.startColor).copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AlbumArtImage(
                    url = featuredTrack?.artworkUrl,
                    contentDescription = featuredTrack?.title ?: mood.label,
                    modifier = Modifier.size(104.dp),
                    shape = RoundedCornerShape(10.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = mood.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = featuredTrack?.title ?: "Mulai Mendengar",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = featuredTrack?.artist ?: mood.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassActionPill(
                            label = "Putar",
                            icon = Icons.Rounded.PlayArrow,
                            highlighted = true,
                            onClick = onPlayFeatured,
                            modifier = Modifier.testTag("hero_play_button")
                        )
                        Surface(
                            onClick = onShuffleFeatured,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(40.dp).testTag("hero_shuffle_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = "Acak",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankedPosterCard(
    rank: Int,
    track: Track,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(RoundedCornerShape(10.dp))
        ) {
            AlbumArtImage(
                url = track.artworkUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(10.dp)
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                shape = RoundedCornerShape(6.dp),
                color = Color(0xB80A0A0C)
            ) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CompactSquareCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    isActive: Boolean,
    cardWidth: androidx.compose.ui.unit.Dp = 142.dp,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(cardWidth)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(cardWidth)
                .clip(RoundedCornerShape(10.dp))
        ) {
            AlbumArtImage(
                url = artworkUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(10.dp)
            )
            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArtistAvatarCard(
    artist: ArtistSummary,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AlbumArtImage(
            url = artist.artworkUrl,
            contentDescription = artist.name,
            modifier = Modifier.size(88.dp),
            shape = CircleShape
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
