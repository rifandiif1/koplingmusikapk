package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.CollectionDetailType
import com.example.model.Track
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.CreatePlaylistDialog
import com.example.ui.components.TrackOptionsBottomSheet
import com.example.ui.player.FloatingMiniPlayer
import com.example.ui.player.FullScreenPlayerModal
import com.example.ui.screens.CollectionDetailScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.theme.KoplingfyTheme
import com.example.viewmodel.KoplingfyViewModel

private data class NavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val koplingfyTabs = listOf(
    NavTab("home", "Beranda", Icons.Rounded.Home, Icons.Outlined.Home),
    NavTab("explore", "Jelajah", Icons.Rounded.Explore, Icons.Outlined.Explore),
    NavTab("search", "Cari", Icons.Rounded.Search, Icons.Outlined.Search),
    NavTab("library", "Koleksi", Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic)
)

private enum class OverlayPage {
    NONE, COLLECTION_DETAIL, SETTINGS, STATS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: KoplingfyViewModel = viewModel()
            KoplingfyAppRoot(viewModel = viewModel)
        }
    }
}

@Composable
fun KoplingfyAppRoot(viewModel: KoplingfyViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()

    val likedTracks by viewModel.likedTracks.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedTrackIds.collectAsStateWithLifecycle()
    val localTracks by viewModel.localTracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playHistory by viewModel.playHistory.collectAsStateWithLifecycle()
    val listeningStats by viewModel.listeningStats.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var overlayPage by rememberSaveable { mutableStateOf(OverlayPage.NONE) }
    var optionsTrack by remember { mutableStateOf<Track?>(null) }
    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMsg) {
        val msg = snackbarMsg
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeMessage()
        }
    }

    // Zero-permission Android Storage Access Framework launcher for local MP3/M4A/FLAC audio files
    val localAudioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importLocalAudio(uris)
        }
    }

    val openCollection: (CollectionDetailType) -> Unit = { type ->
        viewModel.openCollectionDetail(type)
        overlayPage = OverlayPage.COLLECTION_DETAIL
    }

    KoplingfyTheme(
        themeMode = settings.themeMode,
        accentPreset = settings.accentPreset
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpandedWidth = maxWidth >= 600.dp
            val hasActiveTrack = playerState.currentTrack != null
            val bottomListPadding = when {
                isExpandedWidth && hasActiveTrack -> 112.dp
                isExpandedWidth -> 36.dp
                hasActiveTrack -> 178.dp
                else -> 104.dp
            }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(bottom = if (hasActiveTrack) 140.dp else 72.dp)
                    ) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Adaptive NavigationRail on tablets/foldables
                        if (isExpandedWidth) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Spacer(modifier = Modifier.height(24.dp))
                                koplingfyTabs.forEachIndexed { index, tab ->
                                    val selected = selectedTab == index && overlayPage == OverlayPage.NONE
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = {
                                            selectedTab = index
                                            overlayPage = OverlayPage.NONE
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label) },
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.testTag("nav_rail_${tab.route}")
                                    )
                                }
                            }
                        }

                        // Main screen area
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            when (overlayPage) {
                                OverlayPage.COLLECTION_DETAIL -> {
                                    CollectionDetailScreen(
                                        detailState = detailState,
                                        currentTrackId = playerState.currentTrack?.id,
                                        isPlaying = playerState.isPlaying,
                                        likedIds = likedIds,
                                        bottomContentPadding = bottomListPadding,
                                        onBack = {
                                            viewModel.closeCollectionDetail()
                                            overlayPage = OverlayPage.NONE
                                        },
                                        onPlayTrack = { track, list -> viewModel.playTrack(track, list) },
                                        onPlayAll = { list, shuffle -> viewModel.playAll(list, shuffle) },
                                        onToggleLike = viewModel::toggleLike,
                                        onTrackOptions = { optionsTrack = it },
                                        onImportLocalAudio = { localAudioPicker.launch(arrayOf("audio/*")) },
                                        onClearHistory = viewModel::clearPlayHistory
                                    )
                                }
                                OverlayPage.SETTINGS -> {
                                    SettingsScreen(
                                        settings = settings,
                                        bottomContentPadding = bottomListPadding,
                                        onBack = { overlayPage = OverlayPage.NONE },
                                        onThemeModeChange = viewModel::setThemeMode,
                                        onAccentChange = viewModel::setAccentPreset,
                                        onArtStyleChange = viewModel::setPlayerArtStyle,
                                        onEqPresetChange = viewModel::setEqPreset,
                                        onHighQualityChange = viewModel::setHighQualityStream,
                                        onGaplessChange = viewModel::setGaplessPlayback
                                    )
                                }
                                OverlayPage.STATS -> {
                                    StatsScreen(
                                        stats = listeningStats,
                                        bottomContentPadding = bottomListPadding,
                                        onBack = { overlayPage = OverlayPage.NONE },
                                        onPlayTrack = { track, list -> viewModel.playTrack(track, list) }
                                    )
                                }
                                OverlayPage.NONE -> {
                                    when (selectedTab) {
                                        0 -> HomeScreen(
                                            homeState = homeState,
                                            recentTracks = playHistory,
                                            currentTrackId = playerState.currentTrack?.id,
                                            isPlaying = playerState.isPlaying,
                                            likedIds = likedIds,
                                            bottomContentPadding = bottomListPadding,
                                            onSelectMood = { viewModel.loadHomeFeed(it) },
                                            onRetry = { viewModel.loadHomeFeed() },
                                            onPlayTrack = { track, list -> viewModel.playTrack(track, list) },
                                            onPlayAll = { list, shuffle -> viewModel.playAll(list, shuffle) },
                                            onToggleLike = viewModel::toggleLike,
                                            onTrackOptions = { optionsTrack = it },
                                            onAlbumClick = { openCollection(CollectionDetailType.AlbumCollection(it)) },
                                            onArtistClick = { openCollection(CollectionDetailType.ArtistCollection(it)) },
                                            onOpenSettings = { overlayPage = OverlayPage.SETTINGS },
                                            onOpenStats = { overlayPage = OverlayPage.STATS }
                                        )
                                        1 -> ExploreScreen(
                                            homeState = homeState,
                                            currentTrackId = playerState.currentTrack?.id,
                                            isPlaying = playerState.isPlaying,
                                            likedIds = likedIds,
                                            bottomContentPadding = bottomListPadding,
                                            onMoodCardClick = { openCollection(CollectionDetailType.MoodCollection(it)) },
                                            onAlbumClick = { openCollection(CollectionDetailType.AlbumCollection(it)) },
                                            onPlayTrack = { track, list -> viewModel.playTrack(track, list) },
                                            onPlayAll = { list, shuffle -> viewModel.playAll(list, shuffle) },
                                            onToggleLike = viewModel::toggleLike,
                                            onTrackOptions = { optionsTrack = it }
                                        )
                                        2 -> SearchScreen(
                                            searchState = searchState,
                                            searchHistory = searchHistory,
                                            currentTrackId = playerState.currentTrack?.id,
                                            isPlaying = playerState.isPlaying,
                                            likedIds = likedIds,
                                            bottomContentPadding = bottomListPadding,
                                            onQueryChange = viewModel::onSearchQueryChange,
                                            onSubmitSearch = viewModel::submitSearch,
                                            onFilterChange = viewModel::setSearchFilter,
                                            onDeleteHistoryItem = viewModel::deleteSearchHistoryItem,
                                            onClearHistory = viewModel::clearSearchHistory,
                                            onPlayTrack = { track, list -> viewModel.playTrack(track, list) },
                                            onToggleLike = viewModel::toggleLike,
                                            onTrackOptions = { optionsTrack = it },
                                            onAlbumClick = { openCollection(CollectionDetailType.AlbumCollection(it)) },
                                            onArtistClick = { openCollection(CollectionDetailType.ArtistCollection(it)) }
                                        )
                                        else -> LibraryScreen(
                                            likedCount = likedTracks.size,
                                            historyCount = playHistory.size,
                                            localCount = localTracks.size,
                                            totalPlays = listeningStats.totalPlays,
                                            playlists = playlists,
                                            bottomContentPadding = bottomListPadding,
                                            onOpenCollection = openCollection,
                                            onOpenStats = { overlayPage = OverlayPage.STATS },
                                            onImportLocalAudio = { localAudioPicker.launch(arrayOf("audio/*")) },
                                            onCreatePlaylistClick = { showCreatePlaylistDialog = true },
                                            onDeletePlaylist = viewModel::deletePlaylist
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ---- Docked Floating MiniPlayer + Bottom Navigation Bar ----
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    ) {
                        AnimatedVisibility(
                            visible = hasActiveTrack && !playerState.isPlayerExpanded,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut()
                        ) {
                            FloatingMiniPlayer(
                                playerState = playerState,
                                onExpand = { viewModel.setPlayerExpanded(true) },
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onNext = viewModel::skipNext,
                                modifier = Modifier
                                    .padding(
                                        start = if (isExpandedWidth) 84.dp else 0.dp,
                                        bottom = if (isExpandedWidth) 16.dp else 8.dp
                                    )
                                    .let { if (isExpandedWidth) it.navigationBarsPadding() else it }
                            )
                        }

                        if (!isExpandedWidth) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                shadowElevation = 12.dp
                            ) {
                                NavigationBar(
                                    containerColor = Color.Transparent,
                                    tonalElevation = 0.dp
                                ) {
                                    koplingfyTabs.forEachIndexed { index, tab ->
                                        val selected = selectedTab == index && overlayPage == OverlayPage.NONE
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = {
                                                selectedTab = index
                                                overlayPage = OverlayPage.NONE
                                            },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tab.label
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = tab.label,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                indicatorColor = MaterialTheme.colorScheme.primary,
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier.testTag("nav_tab_${tab.route}")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ---- Full-Screen Player Overlay ----
                    AnimatedVisibility(
                        visible = playerState.isPlayerExpanded && playerState.currentTrack != null,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        val currentTrack = playerState.currentTrack
                        if (currentTrack != null) {
                            FullScreenPlayerModal(
                                playerState = playerState,
                                settings = settings,
                                isLiked = currentTrack.id in likedIds,
                                onCollapse = { viewModel.setPlayerExpanded(false) },
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onSeek = viewModel::seekTo,
                                onNext = viewModel::skipNext,
                                onPrevious = viewModel::skipPrevious,
                                onToggleShuffle = viewModel::toggleShuffle,
                                onCycleRepeat = viewModel::cycleRepeatMode,
                                onToggleLike = { viewModel.toggleLike(currentTrack) },
                                onAddToPlaylist = { addToPlaylistTrack = currentTrack },
                                onTrackOptions = { optionsTrack = currentTrack },
                                onSelectQueueItem = { item -> viewModel.playTrack(item, playerState.queue, expandPlayer = true) },
                                onRemoveQueueItem = viewModel::removeFromQueue,
                                onSleepMinutes = viewModel::setSleepTimerMinutes,
                                onSleepEndOfTrack = viewModel::setSleepAfterCurrentTrack,
                                onEqPreset = viewModel::setEqPreset,
                                onCustomBand = viewModel::setCustomBand,
                                onSpeedChange = viewModel::setPlaybackSpeed,
                                onPitchChange = viewModel::setPlaybackPitch
                            )
                        }
                    }
                }
            }
        }

        // ---- Track Options Bottom Sheet ----
        val activeOptionTrack = optionsTrack
        if (activeOptionTrack != null) {
            val detailType = detailState.type
            val removeLambda: (() -> Unit)? = when {
                overlayPage == OverlayPage.COLLECTION_DETAIL && detailType is CollectionDetailType.CustomPlaylist -> {
                    { viewModel.removeTrackFromPlaylist(detailType.playlistId, activeOptionTrack.id) }
                }
                activeOptionTrack.isLocal -> {
                    { viewModel.deleteLocalTrack(activeOptionTrack.id) }
                }
                else -> null
            }
            val removeLabel = if (detailType is CollectionDetailType.CustomPlaylist) {
                "Hapus dari Playlist Ini"
            } else "Hapus Audio Lokal"

            TrackOptionsBottomSheet(
                track = activeOptionTrack,
                isLiked = activeOptionTrack.id in likedIds,
                onDismiss = { optionsTrack = null },
                onPlayNow = { viewModel.playTrack(activeOptionTrack, listOf(activeOptionTrack)) },
                onPlayNext = { viewModel.addToQueueNext(activeOptionTrack) },
                onAddToQueue = { viewModel.addToQueueEnd(activeOptionTrack) },
                onToggleLike = { viewModel.toggleLike(activeOptionTrack) },
                onAddToPlaylist = { addToPlaylistTrack = activeOptionTrack },
                onRemoveAction = removeLambda,
                removeActionLabel = removeLabel
            )
        }

        // ---- Add To Playlist Dialog ----
        val targetTrack = addToPlaylistTrack
        if (targetTrack != null) {
            AddToPlaylistDialog(
                track = targetTrack,
                playlists = playlists,
                onDismiss = { addToPlaylistTrack = null },
                onSelectPlaylist = { playlist ->
                    viewModel.addTrackToPlaylist(playlist.id, playlist.name, targetTrack)
                },
                onCreateNewClick = { showCreatePlaylistDialog = true }
            )
        }

        // ---- Create Playlist Dialog ----
        if (showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = { showCreatePlaylistDialog = false },
                onCreate = { name, desc -> viewModel.createPlaylist(name, desc) }
            )
        }
    }
}
