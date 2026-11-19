package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AlbumSummary
import com.example.model.ArtistSummary
import com.example.model.Track
import com.example.ui.components.AlbumArtImage
import com.example.ui.components.SectionHeader
import com.example.ui.components.TrackRowItem
import com.example.viewmodel.SearchUiState

private val trendingSuggestions = listOf(
    "Tulus", "Nadin Amizah", "The Weeknd", "Hindia", "Billie Eilish",
    "Sheila On 7", "Bruno Mars", "Coldplay", "Lofi Beats", "Jazz Piano"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    searchState: SearchUiState,
    searchHistory: List<String>,
    currentTrackId: String?,
    isPlaying: Boolean,
    likedIds: Set<String>,
    bottomContentPadding: Dp,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onFilterChange: (Int) -> Unit,
    onDeleteHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleLike: (Track) -> Unit,
    onTrackOptions: (Track) -> Unit,
    onAlbumClick: (AlbumSummary) -> Unit,
    onArtistClick: (ArtistSummary) -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val filters = listOf("Semua", "Lagu", "Album", "Artis")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // ---- Header & Search Field ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Cari Musik",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            OutlinedTextField(
                value = searchState.query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (searchState.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Hapus pencarian",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboard?.hide()
                        onSubmitSearch(searchState.query)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_input_field")
            )

            if (searchState.query.isNotBlank()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filters.indices.toList()) { idx ->
                        val selected = searchState.selectedFilter == idx
                        Surface(
                            onClick = { onFilterChange(idx) },
                            shape = RoundedCornerShape(50),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.testTag("search_filter_$idx")
                        ) {
                            Text(
                                text = filters[idx],
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // ---- Body ----
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (searchState.query.isBlank()) {
                // Recent Searches
                if (searchHistory.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Pencarian Terakhir",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                TextButton(onClick = onClearHistory) {
                                    Text("Hapus Semua", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            searchHistory.forEach { historyQuery ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            keyboard?.hide()
                                            onSubmitSearch(historyQuery)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = historyQuery,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(
                                        onClick = { onDeleteHistoryItem(historyQuery) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Hapus riwayat",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Trending Keywords
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Whatshot,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Populer Dicari",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            trendingSuggestions.forEach { term ->
                                Surface(
                                    onClick = {
                                        keyboard?.hide()
                                        onSubmitSearch(term)
                                    },
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(
                                        text = term,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (searchState.isSearching) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else if (searchState.errorMessage != null) {
                item {
                    Text(
                        text = searchState.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
                    )
                }
            } else {
                val showArtists = (searchState.selectedFilter == 0 || searchState.selectedFilter == 3) && searchState.artists.isNotEmpty()
                val showAlbums = (searchState.selectedFilter == 0 || searchState.selectedFilter == 2) && searchState.albums.isNotEmpty()
                val showTracks = (searchState.selectedFilter == 0 || searchState.selectedFilter == 1) && searchState.tracks.isNotEmpty()

                if (showArtists) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionHeader(title = "Artis", modifier = Modifier.padding(horizontal = 20.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(searchState.artists, key = { it.id }) { artist ->
                                    Column(
                                        modifier = Modifier
                                            .width(86.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { onArtistClick(artist) },
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        AlbumArtImage(
                                            url = artist.artworkUrl,
                                            contentDescription = artist.name,
                                            modifier = Modifier.size(76.dp),
                                            shape = CircleShape
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = artist.name,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (showAlbums) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionHeader(title = "Album", modifier = Modifier.padding(horizontal = 20.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(searchState.albums, key = { it.id }) { album ->
                                    Surface(
                                        onClick = { onAlbumClick(album) },
                                        modifier = Modifier.width(142.dp),
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            AlbumArtImage(
                                                url = album.artworkUrl,
                                                contentDescription = album.title,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(124.dp),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = album.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
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
                }

                if (showTracks) {
                    item {
                        SectionHeader(
                            title = "Lagu (${searchState.tracks.size})",
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    items(searchState.tracks, key = { "sr_${it.id}" }) { track ->
                        TrackRowItem(
                            track = track,
                            isCurrent = track.id == currentTrackId,
                            isPlaying = isPlaying && track.id == currentTrackId,
                            isLiked = track.id in likedIds,
                            onClick = { onPlayTrack(track, searchState.tracks) },
                            onLikeClick = { onToggleLike(track) },
                            onMoreClick = { onTrackOptions(track) },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }

                if (!showTracks && !showAlbums && !showArtists) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp, horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Tidak ada hasil untuk \"${searchState.query}\"",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Coba kata kunci lagu atau artis lainnya",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
