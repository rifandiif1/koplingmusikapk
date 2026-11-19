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
    onOpenStats: () -> Unit
) {
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 4..10 -> "Selamat Pagi"
            in 11..14 -> "Selamat Siang"
            in 15..18 -> "Selamat Sore"
            else -> "Selamat Malam"
        }
    }

    val primaryGlow = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        primaryGlow.copy(alpha = 0.14f),
                        bgColor,
                        bgColor
                    ),
                    endY = 720f
                )
            )
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

            // ---- Mood & Vibe Filter Pills ----
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(KoplingfyMoods.allMoods, key = { it.id }) { mood ->
                        val selected = homeState.selectedMood.id == mood.id
                        Surface(
                            onClick = { onSelectMood(mood) },
                            shape = RoundedCornerShape(50),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                            border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.testTag("mood_chip_${mood.id}")
                        ) {
                            Text(
                                text = mood.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "Koplingfy",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (youtubeAccount.isLoggedIn) "$greeting, ${youtubeAccount.name.substringBefore(" ")}" else greeting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Koplingfy",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "HI-FI",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (youtubeAccount.isLoggedIn) {
                Surface(
                    onClick = onOpenSettings,
                    shape = CircleShape,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(40.dp).testTag("home_account_avatar")
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
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier.testTag("home_login_ytm_pill")
                ) {
                    Text(
                        text = "Login YTM",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
            Surface(
                onClick = onOpenListenTogether,
                shape = if (activeRoomCode != null) RoundedCornerShape(50) else CircleShape,
                color = if (activeRoomCode != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                modifier = if (activeRoomCode != null) Modifier.testTag("open_listen_together_button") else Modifier.size(42.dp).testTag("open_listen_together_button")
            ) {
                if (activeRoomCode != null) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = "Play Bersama Aktif",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = activeRoomCode,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = "Play Bersama",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Surface(
                onClick = onOpenStats,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(42.dp).testTag("open_stats_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.AutoGraph,
                        contentDescription = "Statistik",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Surface(
                onClick = onOpenSettings,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(42.dp).testTag("open_settings_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Pengaturan",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
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
            .padding(horizontal = 20.dp)
            .height(210.dp),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        shadowElevation = 10.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_spotlight_1795073960647),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xE80A0B0F),
                                Color(0xB30A0B0F),
                                Color(0x450A0B0F)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = "SPOTLIGHT • ${mood.label.uppercase()}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    if (featuredTrack?.artworkUrl != null) {
                        AlbumArtImage(
                            url = featuredTrack.artworkUrl,
                            contentDescription = featuredTrack.title,
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = featuredTrack?.title ?: "Audio Tanpa Batas",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = featuredTrack?.let { "${it.artist} • ${it.album}" } ?: mood.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassActionPill(
                            label = "Putar Mix",
                            icon = Icons.Rounded.PlayArrow,
                            highlighted = true,
                            onClick = onPlayFeatured,
                            modifier = Modifier.testTag("hero_play_button")
                        )
                        GlassActionPill(
                            label = "Acak",
                            icon = Icons.Rounded.Shuffle,
                            highlighted = false,
                            onClick = onShuffleFeatured,
                            modifier = Modifier.testTag("hero_shuffle_button")
                        )
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
    Surface(
        onClick = onClick,
        modifier = Modifier.width(156.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp)
            ) {
                AlbumArtImage(
                    url = track.artworkUrl,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(14.dp)
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xCC0A0B0F)
                ) {
                    Text(
                        text = "#$rank",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (rank <= 3) MaterialTheme.colorScheme.secondary else Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(34.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Putar",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactSquareCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    isActive: Boolean,
    cardWidth: androidx.compose.ui.unit.Dp = 138.dp,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(cardWidth)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(cardWidth)
                .clip(RoundedCornerShape(18.dp))
        ) {
            AlbumArtImage(
                url = artworkUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(18.dp)
            )
            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
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
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(86.dp),
            shape = CircleShape,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            AlbumArtImage(
                url = artist.artworkUrl,
                contentDescription = artist.name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
                shape = CircleShape
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
