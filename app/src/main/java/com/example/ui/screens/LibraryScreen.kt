package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.CollectionDetailType
import com.example.model.PlaylistSummary
import com.example.model.Track
import com.example.model.YouTubeAccount
import com.example.ui.components.AlbumArtImage
import com.example.ui.components.GlassActionPill
import com.example.ui.components.SectionHeader

private data class SmartMixItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val tracks: List<Track>,
    val shuffle: Boolean,
    val icon: ImageVector,
    val startColor: Color,
    val endColor: Color
)

@Composable
fun LibraryScreen(
    likedCount: Int,
    downloadedCount: Int,
    historyCount: Int,
    localCount: Int,
    totalPlays: Int,
    youtubeAccount: YouTubeAccount,
    playlists: List<PlaylistSummary>,
    bottomContentPadding: Dp,
    onOpenCollection: (CollectionDetailType) -> Unit,
    onOpenStats: () -> Unit,
    onOpenListenTogether: () -> Unit,
    activeRoomCode: String? = null,
    onOpenYouTubeLogin: () -> Unit,
    onSyncYouTubeLibrary: () -> Unit,
    onImportLocalAudio: () -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    topPlayedTracks: List<Track> = emptyList(),
    likedTracks: List<Track> = emptyList(),
    recentTracks: List<Track> = emptyList(),
    downloadedTracks: List<Track> = emptyList(),
    onPlaySmartMix: (tracks: List<Track>, shuffle: Boolean) -> Unit = { _, _ -> }
) {
    val distinctRecent = recentTracks.distinctBy { it.id }
    val smartMixes = buildList {
        if (topPlayedTracks.isNotEmpty()) {
            add(
                SmartMixItem(
                    id = "top_played",
                    title = "Paling Sering Diputar",
                    subtitle = "${topPlayedTracks.size} lagu andalanmu",
                    tracks = topPlayedTracks,
                    shuffle = false,
                    icon = Icons.Rounded.AutoAwesome,
                    startColor = Color(0xFF00C98D),
                    endColor = Color(0xFF065F46)
                )
            )
        }
        if (likedTracks.isNotEmpty()) {
            add(
                SmartMixItem(
                    id = "liked_shuffle",
                    title = "Acak Lagu Disukai",
                    subtitle = "${likedTracks.size} favorit diacak",
                    tracks = likedTracks,
                    shuffle = true,
                    icon = Icons.Rounded.Shuffle,
                    startColor = Color(0xFFFF5A7E),
                    endColor = Color(0xFF881337)
                )
            )
        }
        if (distinctRecent.isNotEmpty()) {
            add(
                SmartMixItem(
                    id = "recent_mix",
                    title = "Baru Diputar",
                    subtitle = "${distinctRecent.size} lagu terakhir",
                    tracks = distinctRecent,
                    shuffle = false,
                    icon = Icons.Rounded.History,
                    startColor = Color(0xFFF59E0B),
                    endColor = Color(0xFF78350F)
                )
            )
        }
        if (downloadedTracks.isNotEmpty()) {
            add(
                SmartMixItem(
                    id = "offline_ready",
                    title = "Siap Offline",
                    subtitle = "${downloadedTracks.size} lagu tanpa kuota",
                    tracks = downloadedTracks,
                    shuffle = false,
                    icon = Icons.Rounded.DownloadDone,
                    startColor = Color(0xFF00B4D8),
                    endColor = Color(0xFF0C4A6E)
                )
            )
        }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ---- Header ----
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Koleksi Kamu",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Lagu disukai, unduhan offline, playlist & lokal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    onClick = onCreatePlaylistClick,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp).testTag("library_add_playlist_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Buat Playlist",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        // ---- YouTube Music Account (Google Sign-In & Optional Sync) Banner ----
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.9.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (youtubeAccount.isLoggedIn) {
                        AlbumArtImage(
                            url = youtubeAccount.avatarUrl,
                            contentDescription = youtubeAccount.name,
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Login,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (youtubeAccount.isLoggedIn) youtubeAccount.name.ifBlank { "Akun Google" } else "Akun YouTube Music",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when {
                                youtubeAccount.hasCookieSession -> "Tarik Lagu Disukai & Playlist akunmu"
                                youtubeAccount.isLoggedIn -> youtubeAccount.email.ifBlank { "Terhubung dengan Google" }
                                else -> "Masuk 1-ketuk dengan akun Google"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    GlassActionPill(
                        label = when {
                            youtubeAccount.hasCookieSession -> "Sync"
                            youtubeAccount.isLoggedIn -> "Cookie"
                            else -> "Masuk"
                        },
                        icon = if (youtubeAccount.isLoggedIn) Icons.Rounded.Sync else Icons.Rounded.Login,
                        highlighted = !youtubeAccount.isLoggedIn || youtubeAccount.hasCookieSession,
                        onClick = if (youtubeAccount.hasCookieSession) onSyncYouTubeLibrary else onOpenYouTubeLogin,
                        modifier = Modifier.testTag("library_ytm_action")
                    )
                }
            }
        }

        // ---- Live Play Bersama Banner (Only shown when actively joined in a room) ----
        if (activeRoomCode != null) {
            item {
                Surface(
                    onClick = onOpenListenTogether,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .testTag("library_play_together_card"),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Play Bersama Aktif • $activeRoomCode",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Ketuk untuk kembali ke ruang dengar bersama",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.QrCode2,
                            contentDescription = "Buka Play Bersama",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        // ---- Smart Bento Grid (Liked, Offline Downloads, History, Local, Stats) ----
        item {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SmartLibraryTile(
                        title = "Lagu Disukai",
                        subtitle = "$likedCount lagu",
                        icon = Icons.Rounded.Favorite,
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = { onOpenCollection(CollectionDetailType.LikedSongs) },
                        modifier = Modifier.weight(1f).testTag("tile_liked_songs")
                    )
                    SmartLibraryTile(
                        title = "Unduhan Offline",
                        subtitle = "$downloadedCount lagu siap",
                        icon = Icons.Rounded.DownloadDone,
                        accent = Color(0xFF00C2FF),
                        onClick = { onOpenCollection(CollectionDetailType.Downloads) },
                        modifier = Modifier.weight(1f).testTag("tile_downloads")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SmartLibraryTile(
                        title = "Riwayat Putar",
                        subtitle = "$historyCount diputar",
                        icon = Icons.Rounded.History,
                        accent = MaterialTheme.colorScheme.secondary,
                        onClick = { onOpenCollection(CollectionDetailType.History) },
                        modifier = Modifier.weight(1f).testTag("tile_history")
                    )
                    SmartLibraryTile(
                        title = "Audio Lokal",
                        subtitle = "$localCount file perangkat",
                        icon = Icons.Rounded.AudioFile,
                        accent = MaterialTheme.colorScheme.tertiary,
                        onClick = { onOpenCollection(CollectionDetailType.LocalAudio) },
                        modifier = Modifier.weight(1f).testTag("tile_local_audio")
                    )
                }

                // Full-width Stats banner tile
                Surface(
                    onClick = onOpenStats,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, Color(0xFFFF6B7A).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().testTag("tile_stats")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.horizontalGradient(listOf(Color(0xFFFF6B7A).copy(alpha = 0.16f), Color.Transparent)))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFF6B7A).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.AutoGraph, contentDescription = null, tint = Color(0xFFFF6B7A))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Statistik Mendengar", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text("$totalPlays total pemutaran • Grafik 7 hari & artis teratas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // ---- Zero-Permission SAF Local Audio Importer Card ----
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Impor File MP3 / FLAC Lokal",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Putar audio perangkat tanpa izin penyimpanan penuh",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    GlassActionPill(
                        label = "Impor",
                        icon = Icons.Rounded.Add,
                        highlighted = false,
                        onClick = onImportLocalAudio,
                        modifier = Modifier.testTag("import_local_audio_button")
                    )
                }
            }
        }

        // ---- Smart Mix Otomatis Carousel ----
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(
                    title = "Smart Mix Otomatis",
                    subtitle = "Kurasi instan dari kebiasaan dengarmu",
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                if (smartMixes.isEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Putar, sukai, atau unduh beberapa lagu agar Smart Mix Otomatis langsung terbentuk di sini.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(smartMixes, key = { "smix_${it.id}" }) { mix ->
                            SmartMixCard(
                                mix = mix,
                                onClick = { onPlaySmartMix(mix.tracks, mix.shuffle) }
                            )
                        }
                    }
                }
            }
        }

        // ---- Custom Playlists Section ----
        item {
            SectionHeader(
                title = "Playlist Kamu",
                subtitle = "${playlists.size} playlist tersimpan",
                actionLabel = "+ Baru",
                onActionClick = onCreatePlaylistClick,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        if (playlists.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Belum Ada Playlist",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Susun playlist untuk setiap suasana hati atau sinkron dari YouTube Music",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        GlassActionPill(
                            label = "Buat Playlist Pertama",
                            icon = Icons.Rounded.Add,
                            highlighted = true,
                            onClick = onCreatePlaylistClick
                        )
                    }
                }
            }
        } else {
            items(playlists, key = { "pl_${it.id}" }) { playlist ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onOpenCollection(CollectionDetailType.CustomPlaylist(playlist.id)) },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!playlist.coverUrl.isNullOrBlank()) {
                            AlbumArtImage(
                                url = playlist.coverUrl,
                                contentDescription = playlist.name,
                                modifier = Modifier.size(56.dp),
                                shape = RoundedCornerShape(14.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${playlist.trackCount} lagu" +
                                    if (playlist.description.isNotBlank()) " • ${playlist.description}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onDeletePlaylist(playlist.id) }) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Hapus playlist",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartLibraryTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(104.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.16f), Color.Transparent)
                    )
                )
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
        }
    }
}

@Composable
private fun SmartMixCard(
    mix: SmartMixItem,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, mix.startColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .width(172.dp)
            .height(142.dp)
            .testTag("smart_mix_${mix.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(mix.startColor, mix.endColor)))
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val coverUrl = mix.tracks.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
                    if (coverUrl != null) {
                        AlbumArtImage(
                            url = coverUrl,
                            contentDescription = mix.title,
                            modifier = Modifier.size(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = mix.icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.34f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Putar ${mix.title}",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = mix.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = mix.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.86f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
