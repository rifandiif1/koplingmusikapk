package com.example

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.credentials.CredentialManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.configureAppCheck
import com.example.listentogether.ListenRoom
import com.example.listentogether.ListenSessionUiState
import com.example.listentogether.ListenTogetherRepository
import com.example.listentogether.ListenTogetherViewModel
import com.example.listentogether.ListenUiState
import com.example.listentogether.RecentRoomEntry
import com.example.listentogether.signOutGoogle
import com.example.model.ArtistSummary
import com.example.model.CollectionDetailType
import com.example.model.Track
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.CreatePlaylistDialog
import com.example.ui.components.DynamicIslandLyricsOverlay
import com.example.ui.components.FloatingRoomChatOverlay
import com.example.ui.components.LyricsTextSearchSheet
import com.example.ui.components.RoomQuickChatBottomSheet
import com.example.ui.components.MusicStoryStudioSheet
import com.example.ui.components.ShazamAudioSearchSheet
import com.example.ui.components.TrackOptionsBottomSheet
import com.example.ui.player.FloatingMiniPlayer
import com.example.ui.player.FullScreenPlayerModal
import com.example.ui.player.StandByVisualizerOverlay
import com.example.ui.player.StreamTechStatsDialog
import com.example.ui.screens.CollectionDetailScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ListenTogetherScreenHost
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.screens.YouTubeLoginScreen
import com.example.ui.theme.KoplingfyTheme
import com.example.viewmodel.KoplingfyViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

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
    NavTab("listen_together", "Bersama", Icons.Rounded.Groups, Icons.Outlined.Groups),
    NavTab("library", "Koleksi", Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic)
)

private enum class OverlayPage {
    NONE, COLLECTION_DETAIL, SETTINGS, STATS, YOUTUBE_LOGIN
}

class MainActivity : ComponentActivity() {
    private var isInPipModeState by mutableStateOf(false)
    private var isVideoFullscreenState by mutableStateOf(false)
    private var canAutoEnterPip: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure Firebase App Check before UI, ViewModels, or any Firebase call
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            val viewModel: KoplingfyViewModel = viewModel()
            KoplingfyAuthScopedRoot(
                viewModel = viewModel,
                isInPipMode = isInPipModeState,
                isVideoFullscreen = isVideoFullscreenState,
                onVideoPipEligibilityChange = { eligible -> canAutoEnterPip = eligible },
                onEnterPipMode = { enterVideoPipMode() },
                onToggleVideoFullscreen = { enabled -> setLandscapeVideoFullscreen(enabled) }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("FIREBASE_APPCHECK_DEBUG_TOKEN")) {
            configureAppCheck(this, intent)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeState = isInPictureInPictureMode
        if (isInPictureInPictureMode && isVideoFullscreenState) {
            setLandscapeVideoFullscreen(false)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (canAutoEnterPip && !isInPipModeState) {
            enterVideoPipMode()
        }
    }

    fun enterVideoPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            runCatching {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    fun setLandscapeVideoFullscreen(enabled: Boolean) {
        isVideoFullscreenState = enabled
        requestedOrientation = if (enabled) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (enabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/**
 * Auth-gated wrapper: Firestore ListenTogetherViewModel is ONLY instantiated when
 * Firebase.auth.currentUser != null and is keyed by uid so account switches or sign-out
 * cleanly tear down all Firestore listeners.
 */
@Composable
private fun KoplingfyAuthScopedRoot(
    viewModel: KoplingfyViewModel,
    isInPipMode: Boolean,
    isVideoFullscreen: Boolean,
    onVideoPipEligibilityChange: (Boolean) -> Unit,
    onEnterPipMode: () -> Unit,
    onToggleVideoFullscreen: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val isFirebaseReady = remember(context) { FirebaseApp.getApps(context).isNotEmpty() }
    // Hoisted above auth branches so signing in or out never resets the active tab (e.g. Bersama tab!)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var overlayPage by rememberSaveable { mutableStateOf(OverlayPage.NONE) }

    if (!isFirebaseReady) {
        KoplingfyAppRoot(
            viewModel = viewModel,
            currentGoogleUser = null,
            listenViewModel = null,
            listenSessionState = ListenSessionUiState(),
            myActiveRoomsState = ListenUiState.Success(emptyList()),
            recentRoomsState = ListenUiState.Success(emptyList()),
            selectedTab = selectedTab,
            onSelectedTabChange = { selectedTab = it },
            overlayPage = overlayPage,
            onOverlayPageChange = { overlayPage = it },
            onAuthStateChanged = {},
            isInPipMode = isInPipMode,
            isVideoFullscreen = isVideoFullscreen,
            onVideoPipEligibilityChange = onVideoPipEligibilityChange,
            onEnterPipMode = onEnterPipMode,
            onToggleVideoFullscreen = onToggleVideoFullscreen
        )
        return
    }

    val auth = remember { Firebase.auth }
    var currentGoogleUser by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentGoogleUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val user = currentGoogleUser
    if (user == null) {
        KoplingfyAppRoot(
            viewModel = viewModel,
            currentGoogleUser = null,
            listenViewModel = null,
            listenSessionState = ListenSessionUiState(),
            myActiveRoomsState = ListenUiState.Success(emptyList()),
            recentRoomsState = ListenUiState.Success(emptyList()),
            selectedTab = selectedTab,
            onSelectedTabChange = { selectedTab = it },
            overlayPage = overlayPage,
            onOverlayPageChange = { overlayPage = it },
            onAuthStateChanged = { currentGoogleUser = auth.currentUser },
            isInPipMode = isInPipMode,
            isVideoFullscreen = isVideoFullscreen,
            onVideoPipEligibilityChange = onVideoPipEligibilityChange,
            onEnterPipMode = onEnterPipMode,
            onToggleVideoFullscreen = onToggleVideoFullscreen
        )
    } else {
        key(user.uid) {
            val displayName = user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                ?: "Pendengar"

            val listenViewModel: ListenTogetherViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        ListenTogetherViewModel(
                            repository = ListenTogetherRepository(db),
                            currentUserId = user.uid,
                            currentUserName = displayName
                        )
                    }
                }
            )

            val sessionState by listenViewModel.sessionState.collectAsStateWithLifecycle()
            val myActiveRoomsState by listenViewModel.myActiveRoomsState.collectAsStateWithLifecycle()
            val recentRoomsState by listenViewModel.recentRoomsState.collectAsStateWithLifecycle()

            // Inbound Real-Time Playback Sync: Firestore Room -> Local Audio Engine
            val activeRoom = sessionState.activeRoom
            LaunchedEffect(activeRoom?.roomCode, activeRoom?.syncSequence, activeRoom?.status) {
                val room = activeRoom ?: return@LaunchedEffect
                if (listenViewModel.shouldApplyInboundSync(room)) {
                    listenViewModel.markInboundSynced(room.syncSequence)
                    val track = room.currentTrackOrNull()
                    val queue = room.parsedQueue().map { it.toTrack() }
                    if (track != null) {
                        viewModel.applyRemoteRoomSync(
                            track = track,
                            shouldPlay = room.playing,
                            targetPositionMs = room.effectivePositionMs(),
                            roomQueue = queue
                        )
                    }
                }
            }

            // Inbound Collaborative Queue Sync (updates upcoming queue WITHOUT resetting current song position)
            LaunchedEffect(activeRoom?.roomCode, activeRoom?.queueTracks) {
                val room = activeRoom ?: return@LaunchedEffect
                if (room.status == "active") {
                    viewModel.syncRoomUpcomingQueue(room.parsedQueue().map { it.toTrack() })
                }
            }

            KoplingfyAppRoot(
                viewModel = viewModel,
                currentGoogleUser = user,
                listenViewModel = listenViewModel,
                listenSessionState = sessionState,
                myActiveRoomsState = myActiveRoomsState,
                recentRoomsState = recentRoomsState,
                selectedTab = selectedTab,
                onSelectedTabChange = { selectedTab = it },
                overlayPage = overlayPage,
                onOverlayPageChange = { overlayPage = it },
                onAuthStateChanged = { currentGoogleUser = auth.currentUser },
                isInPipMode = isInPipMode,
                isVideoFullscreen = isVideoFullscreen,
                onVideoPipEligibilityChange = onVideoPipEligibilityChange,
                onEnterPipMode = onEnterPipMode,
                onToggleVideoFullscreen = onToggleVideoFullscreen
            )
        }
    }
}

@Composable
private fun KoplingfyAppRoot(
    viewModel: KoplingfyViewModel,
    currentGoogleUser: FirebaseUser?,
    listenViewModel: ListenTogetherViewModel?,
    listenSessionState: ListenSessionUiState,
    myActiveRoomsState: ListenUiState<List<ListenRoom>>,
    recentRoomsState: ListenUiState<List<RecentRoomEntry>>,
    selectedTab: Int,
    onSelectedTabChange: (Int) -> Unit,
    overlayPage: OverlayPage,
    onOverlayPageChange: (OverlayPage) -> Unit,
    onAuthStateChanged: () -> Unit,
    isInPipMode: Boolean,
    isVideoFullscreen: Boolean,
    onVideoPipEligibilityChange: (Boolean) -> Unit,
    onEnterPipMode: () -> Unit,
    onToggleVideoFullscreen: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val isFirebaseReady = remember(context) { FirebaseApp.getApps(context).isNotEmpty() }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val youtubeAccount by viewModel.youtubeAccount.collectAsStateWithLifecycle()
    val isSyncingAccount by viewModel.isSyncingAccount.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val cloudSyncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()

    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()

    val likedTracks by viewModel.likedTracks.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedTrackIds.collectAsStateWithLifecycle()
    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val downloadedIds by viewModel.downloadedTrackIds.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val localTracks by viewModel.localTracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playHistory by viewModel.playHistory.collectAsStateWithLifecycle()
    val listeningStats by viewModel.listeningStats.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    var optionsTrack by remember { mutableStateOf<Track?>(null) }
    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var savingQueueAsPlaylist by remember { mutableStateOf(false) }
    var showTechStatsTrack by remember { mutableStateOf<Track?>(null) }
    var storyStudioRequest by remember { mutableStateOf<StoryStudioRequest?>(null) }
    var showShazamSearchSheet by remember { mutableStateOf(false) }
    var showLyricsSearchSheet by remember { mutableStateOf(false) }
    var showStandByVisualizer by remember { mutableStateOf(false) }

    // Keep MainActivity informed whether auto-PiP on Home press should trigger
    LaunchedEffect(playerState.isVideoMode, playerState.isPlaying, playerState.isPlayerExpanded) {
        onVideoPipEligibilityChange(playerState.isVideoMode && playerState.isPlaying && playerState.isPlayerExpanded)
    }

    // Auto-exit landscape video fullscreen if user switches back to Audio mode or collapses player
    LaunchedEffect(playerState.isVideoMode, playerState.isPlayerExpanded) {
        if (isVideoFullscreen && (!playerState.isVideoMode || !playerState.isPlayerExpanded)) {
            onToggleVideoFullscreen(false)
        }
    }

    // Dedicated clean Video-only Viewport when Android Picture-in-Picture is active
    if (isInPipMode) {
        var pipTextureRef by remember { mutableStateOf<TextureView?>(null) }
        DisposableEffect(pipTextureRef) {
            val tv = pipTextureRef
            if (tv != null) viewModel.attachVideoTextureView(tv)
            onDispose { if (tv != null) viewModel.clearVideoTextureView(tv) }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx -> TextureView(ctx).also { pipTextureRef = it } },
                update = { tv -> if (pipTextureRef !== tv) pipTextureRef = tv },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val activeRoom = listenSessionState.activeRoom?.takeIf { it.status == "active" }
    val activeRoomCode = activeRoom?.roomCode
    val isActiveRoomMember = currentGoogleUser != null &&
        listenViewModel != null &&
        activeRoom != null &&
        currentGoogleUser.uid in activeRoom.memberUids
    val canControlActiveRoom = currentGoogleUser != null &&
        listenViewModel != null &&
        listenSessionState.canControl(currentGoogleUser.uid)

    var askedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Media playback continues seamlessly even if notification permission is declined */ }

    LaunchedEffect(playerState.isPlaying) {
        if (playerState.isPlaying && !askedNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askedNotificationPermission = true
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(snackbarMsg) {
        val msg = snackbarMsg
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeMessage()
        }
    }

    // Outbound playback wrappers that also push to active Play Bersama room when permitted
    val playTrackWithRoomSync: (Track, List<Track>, Boolean) -> Unit = { track, queue, expand ->
        viewModel.playTrack(track, queue, expandPlayer = expand)
        if (canControlActiveRoom && !track.isLocal) {
            listenViewModel?.pushPlaybackSync(
                track = track,
                playing = true,
                positionMs = 0L,
                actionLabel = "${listenViewModel.currentUserName} memutar \"${track.title}\""
            )
        }
    }

    val togglePlayPauseWithRoomSync: () -> Unit = {
        val current = playerState.currentTrack
        val nextPlaying = !playerState.isPlaying
        viewModel.togglePlayPause()
        if (canControlActiveRoom && current != null && !current.isLocal) {
            listenViewModel?.pushPlaybackSync(
                track = current,
                playing = nextPlaying,
                positionMs = playerState.positionMs,
                actionLabel = if (nextPlaying) {
                    "${listenViewModel.currentUserName} melanjutkan pemutaran"
                } else {
                    "${listenViewModel.currentUserName} menjeda pemutaran"
                }
            )
        }
    }

    val seekWithRoomSync: (Long) -> Unit = { targetMs ->
        viewModel.seekTo(targetMs)
        val current = playerState.currentTrack
        if (canControlActiveRoom && current != null && !current.isLocal) {
            listenViewModel?.pushPlaybackSync(
                track = current,
                playing = playerState.isPlaying,
                positionMs = targetMs,
                actionLabel = "${listenViewModel.currentUserName} menggeser posisi lagu"
            )
        }
    }

    val skipNextWithRoomSync: () -> Unit = {
        val room = listenSessionState.activeRoom
        val sharedQueue = room?.parsedQueue().orEmpty()
        if (canControlActiveRoom && sharedQueue.isNotEmpty()) {
            val nextTrack = sharedQueue.first().toTrack()
            listenViewModel?.playRoomQueueTrack(0)
            viewModel.playTrack(nextTrack, sharedQueue.map { it.toTrack() })
        } else {
            val nextIdx = if (playerState.currentIndex + 1 in playerState.queue.indices) {
                playerState.currentIndex + 1
            } else -1
            val nextCandidate = if (nextIdx >= 0) playerState.queue[nextIdx] else null
            viewModel.skipNext()
            if (canControlActiveRoom && nextCandidate != null && !nextCandidate.isLocal) {
                listenViewModel?.pushPlaybackSync(
                    track = nextCandidate,
                    playing = true,
                    positionMs = 0L,
                    actionLabel = "${listenViewModel.currentUserName} memutar \"${nextCandidate.title}\""
                )
            }
        }
    }

    val skipPreviousWithRoomSync: () -> Unit = {
        val prevIdx = if (playerState.positionMs <= 3_500L && playerState.currentIndex - 1 in playerState.queue.indices) {
            playerState.currentIndex - 1
        } else playerState.currentIndex
        val targetTrack = playerState.queue.getOrNull(prevIdx)
        viewModel.skipPrevious()
        if (canControlActiveRoom && targetTrack != null && !targetTrack.isLocal) {
            listenViewModel?.pushPlaybackSync(
                track = targetTrack,
                playing = true,
                positionMs = 0L,
                actionLabel = "${listenViewModel.currentUserName} memutar \"${targetTrack.title}\""
            )
        }
    }

    val playAllWithRoomSync: (List<Track>, Boolean) -> Unit = { list, shuffle ->
        val ordered = if (shuffle) list.shuffled() else list
        viewModel.playAll(ordered, shuffle = false)
        val first = ordered.firstOrNull { !it.isLocal }
        if (canControlActiveRoom && first != null && listenViewModel != null) {
            val uid = listenViewModel.currentUserId
            val actor = listenViewModel.currentUserName
            val upcoming = ordered.filter { !it.isLocal && it.id != first.id }.take(25).map { t ->
                com.example.listentogether.RoomQueueTrack(
                    trackId = t.id,
                    title = t.title,
                    artist = t.artist,
                    album = t.album,
                    artworkUrl = t.artworkUrl.orEmpty(),
                    durationMs = t.durationMs,
                    addedByName = actor,
                    addedByUid = uid,
                    upvoterUids = listOf(uid)
                )
            }
            listenViewModel.pushPlaybackSync(
                track = first,
                playing = true,
                positionMs = 0L,
                actionLabel = "$actor memutar \"${first.title}\"",
                updatedQueue = upcoming
            )
        }
    }

    // Auto-advance synchronized room track when current song finishes on Host/Controller device
    DisposableEffect(canControlActiveRoom, activeRoom?.roomCode, skipNextWithRoomSync) {
        viewModel.onAutoAdvanceOverride = if (canControlActiveRoom) {
            {
                skipNextWithRoomSync()
                true
            }
        } else {
            null
        }
        onDispose {
            viewModel.onAutoAdvanceOverride = null
        }
    }

    val swipePlayNext: (Track) -> Unit = { track ->
        if (isActiveRoomMember && !track.isLocal) {
            listenViewModel?.addTrackToRoomQueue(track, playNext = true)
        } else {
            viewModel.addToQueueNext(track)
        }
    }

    val swipeAddToQueue: (Track) -> Unit = { track ->
        if (isActiveRoomMember && !track.isLocal) {
            listenViewModel?.addTrackToRoomQueue(track, playNext = false)
        } else {
            viewModel.addToQueueEnd(track)
        }
    }
    val swipeQueueLabel = if (isActiveRoomMember) "Antrian+" else "Antrean"

    // Zero-permission SAF launchers
    val localAudioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importLocalAudio(uris)
    }

    val backupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }

    val backupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importBackup(uri)
    }

    val shareTrack: (Track) -> Unit = { track ->
        val ytLink = if (track.id.startsWith("yt_")) {
            "\nhttps://music.youtube.com/watch?v=${track.id.removePrefix("yt_")}"
        } else ""
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${track.title} — ${track.artist}")
            putExtra(Intent.EXTRA_TEXT, "Mendengarkan \"${track.title}\" oleh ${track.artist} di Koplingfy$ytLink")
        }
        runCatching { context.startActivity(Intent.createChooser(shareIntent, "Bagikan Lagu")) }
    }

    val openSingleStoryStudio: (Track) -> Unit = { track ->
        val pool = buildList {
            add(track)
            addAll(playerState.queue)
            addAll(likedTracks.take(12))
            addAll(playHistory.take(12))
        }.distinctBy { it.id }
        storyStudioRequest = StoryStudioRequest(
            seedTrack = track,
            candidateTracks = pool,
            initialMultiMode = false,
            initialSelectedTracks = pool.take(4),
            defaultMixTitle = "Lagi Dengerin Ini"
        )
    }

    val openMultiStoryStudio: (String, List<Track>) -> Unit = { title, tracks ->
        val pool = buildList {
            addAll(tracks)
            addAll(playerState.queue)
            addAll(likedTracks.take(10))
        }.distinctBy { it.id }
        storyStudioRequest = StoryStudioRequest(
            seedTrack = tracks.firstOrNull(),
            candidateTracks = pool,
            initialMultiMode = true,
            initialSelectedTracks = tracks.take(5),
            defaultMixTitle = title.ifBlank { "Lagi Dengerin Ini" }
        )
    }

    val shareLyricsText: (Track, String) -> Unit = { track, text ->
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Lirik: ${track.title}")
            putExtra(Intent.EXTRA_TEXT, "${track.title} — ${track.artist}\n\n$text\n\n— Koplingfy")
        }
        runCatching { context.startActivity(Intent.createChooser(shareIntent, "Bagikan Lirik")) }
    }

    val openCollection: (CollectionDetailType) -> Unit = { type ->
        viewModel.openCollectionDetail(type)
        onOverlayPageChange(OverlayPage.COLLECTION_DETAIL)
    }

    val openYouTubeLogin: () -> Unit = {
        viewModel.setPlayerExpanded(false)
        onOverlayPageChange(OverlayPage.YOUTUBE_LOGIN)
    }

    val openListenTogether: () -> Unit = {
        viewModel.setPlayerExpanded(false)
        onOverlayPageChange(OverlayPage.NONE)
        onSelectedTabChange(3)
    }

    var showRoomChatPopup by remember(activeRoom?.roomCode) { mutableStateOf(false) }
    val activeRoomChatMessages = remember(activeRoom?.chatMessages) {
        activeRoom?.parsedChatMessages().orEmpty()
    }
    var lastReadRoomChatCount by remember(activeRoom?.roomCode) {
        mutableIntStateOf(activeRoomChatMessages.size)
    }
    LaunchedEffect(showRoomChatPopup, activeRoomChatMessages.size) {
        if (showRoomChatPopup) {
            lastReadRoomChatCount = activeRoomChatMessages.size
        }
    }
    val unreadRoomChatCount = if (isActiveRoomMember) {
        (activeRoomChatMessages.size - lastReadRoomChatCount).coerceAtLeast(0)
    } else 0
    val openRoomChatPopupLambda: (() -> Unit)? = if (isActiveRoomMember && listenViewModel != null) {
        { showRoomChatPopup = true }
    } else null

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
                                            onSelectedTabChange(index)
                                            onOverlayPageChange(OverlayPage.NONE)
                                        },
                                        icon = {
                                            if (tab.route == "listen_together" && activeRoomCode != null) {
                                                BadgedBox(
                                                    badge = {
                                                        Badge(containerColor = MaterialTheme.colorScheme.primary)
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                        contentDescription = tab.label
                                                    )
                                                }
                                            } else {
                                                Icon(
                                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tab.label
                                                )
                                            }
                                        },
                                        label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.onBackground,
                                            selectedTextColor = MaterialTheme.colorScheme.onBackground,
                                            indicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.testTag("nav_rail_${tab.route}")
                                    )
                                }
                            }
                        }

                        // Main screen area
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            when (overlayPage) {
                                OverlayPage.YOUTUBE_LOGIN -> {
                                    YouTubeLoginScreen(
                                        isVerifying = isSyncingAccount,
                                        currentGoogleUser = currentGoogleUser,
                                        isFirebaseReady = isFirebaseReady,
                                        onBack = { onOverlayPageChange(OverlayPage.NONE) },
                                        onGoogleAccountConnected = { name, email, avatarUrl ->
                                            viewModel.completeGoogleYouTubeLogin(name, email, avatarUrl) {
                                                onOverlayPageChange(OverlayPage.NONE)
                                            }
                                        },
                                        onAuthStateChanged = onAuthStateChanged,
                                        onShowSnackbar = viewModel::showMessage,
                                        onCookieCaptured = { cookie ->
                                            viewModel.completeYouTubeLogin(cookie) {
                                                onOverlayPageChange(OverlayPage.NONE)
                                            }
                                        }
                                    )
                                }
                                OverlayPage.COLLECTION_DETAIL -> {
                                    CollectionDetailScreen(
                                        detailState = detailState,
                                        currentTrackId = playerState.currentTrack?.id,
                                        isPlaying = playerState.isPlaying,
                                        likedIds = likedIds,
                                        bottomContentPadding = bottomListPadding,
                                        onBack = {
                                            viewModel.closeCollectionDetail()
                                            onOverlayPageChange(OverlayPage.NONE)
                                        },
                                        onPlayTrack = { track, list -> playTrackWithRoomSync(track, list, false) },
                                        onPlayAll = playAllWithRoomSync,
                                        onToggleLike = viewModel::toggleLike,
                                        onTrackOptions = { optionsTrack = it },
                                        onImportLocalAudio = { localAudioPicker.launch(arrayOf("audio/*")) },
                                        onClearHistory = viewModel::clearPlayHistory,
                                        onStartArtistRadio = viewModel::startArtistRadio,
                                        onPlayNext = swipePlayNext,
                                        onAddToQueue = swipeAddToQueue,
                                        swipeQueueLabel = swipeQueueLabel,
                                        onShareStory = { title, tracks -> openMultiStoryStudio(title, tracks) }
                                    )
                                }
                                OverlayPage.SETTINGS -> {
                                    LaunchedEffect(currentGoogleUser?.uid) {
                                        viewModel.refreshStorageStats()
                                        if (isFirebaseReady && currentGoogleUser != null) {
                                            viewModel.refreshCloudSyncMeta()
                                        }
                                    }
                                    SettingsScreen(
                                        settings = settings,
                                        youtubeAccount = youtubeAccount,
                                        isSyncingAccount = isSyncingAccount,
                                        storageStats = storageStats,
                                        bottomContentPadding = bottomListPadding,
                                        onBack = { onOverlayPageChange(OverlayPage.NONE) },
                                        onOpenYouTubeLogin = openYouTubeLogin,
                                        onSyncYouTubeLibrary = viewModel::syncYouTubeAccountLibrary,
                                        onLogoutYouTube = viewModel::logoutYouTubeAccount,
                                        onThemeModeChange = viewModel::setThemeMode,
                                        onAccentChange = viewModel::setAccentPreset,
                                        onArtStyleChange = viewModel::setPlayerArtStyle,
                                        onBgStyleChange = viewModel::setPlayerBackgroundStyle,
                                        onAudioQualityChange = viewModel::setAudioQuality,
                                        onEqPresetChange = viewModel::setEqPreset,
                                        onNormalizeAudioChange = viewModel::setNormalizeAudio,
                                        onSkipSilenceChange = viewModel::setSkipSilence,
                                        onAutoplayRadioChange = viewModel::setAutoplayRadio,
                                        onKeepScreenOnLyricsChange = viewModel::setKeepScreenOnLyrics,
                                        onGaplessChange = viewModel::setGaplessPlayback,
                                        onDynamicArtworkColorChange = viewModel::setDynamicArtworkColor,
                                        onDynamicIslandLyricsChange = viewModel::setDynamicIslandLyrics,
                                        onNotificationBarLyricsChange = viewModel::setNotificationBarLyrics,
                                        onCrossfadeSecondsChange = viewModel::setCrossfadeSeconds,
                                        onDataSaverCellularChange = viewModel::setDataSaverCellular,
                                        onDownloadWifiOnlyChange = viewModel::setDownloadWifiOnly,
                                        onClearCache = viewModel::clearAppCache,
                                        onClearLyricsAiCache = viewModel::clearLyricsAiCache,
                                        onClearDownloads = viewModel::clearAllDownloads,
                                        onExportBackup = { backupExportLauncher.launch("koplingfy_backup.json") },
                                        onImportBackup = { backupImportLauncher.launch(arrayOf("application/json", "*/*")) },
                                        cloudSyncState = cloudSyncState,
                                        onUploadLibraryToCloud = viewModel::uploadLibraryToCloud,
                                        onRestoreLibraryFromCloud = viewModel::restoreLibraryFromCloud,
                                        onImportYouTubePlaylistUrl = viewModel::importYouTubePlaylistUrl,
                                        isFirebaseReady = isFirebaseReady,
                                        firebaseUserName = currentGoogleUser?.displayName?.takeIf { it.isNotBlank() }
                                            ?: currentGoogleUser?.email?.substringBefore("@"),
                                        firebaseUserEmail = currentGoogleUser?.email,
                                        activeRoomCode = activeRoomCode,
                                        onOpenListenTogether = openListenTogether,
                                        onSignOutFirebase = {
                                            signOutGoogle(
                                                credentialManager = credentialManager,
                                                scope = scope,
                                                onBeforeSignOut = { listenViewModel?.detachBeforeSignOut() },
                                                onSignOutComplete = {
                                                    onAuthStateChanged()
                                                    viewModel.showMessage("Keluar dari akun Google Play Bersama")
                                                }
                                            )
                                        }
                                    )
                                }
                                OverlayPage.STATS -> {
                                    StatsScreen(
                                        stats = listeningStats,
                                        bottomContentPadding = bottomListPadding,
                                        onBack = { onOverlayPageChange(OverlayPage.NONE) },
                                        onPlayTrack = { track, list -> playTrackWithRoomSync(track, list, false) },
                                        onShowSnackbar = viewModel::showMessage
                                    )
                                }
                                OverlayPage.NONE -> {
                                    when (selectedTab) {
                                        0 -> HomeScreen(
                                            homeState = homeState,
                                            recentTracks = playHistory,
                                            youtubeAccount = youtubeAccount,
                                            currentTrackId = playerState.currentTrack?.id,
                                            isPlaying = playerState.isPlaying,
                                            likedIds = likedIds,
                                            bottomContentPadding = bottomListPadding,
                                            onSelectMood = { viewModel.loadHomeFeed(it) },
                                            onRetry = { viewModel.loadHomeFeed() },
                                            onPlayTrack = { track, list -> playTrackWithRoomSync(track, list, false) },
                                            onPlayAll = playAllWithRoomSync,
                                            onToggleLike = viewModel::toggleLike,
                                            onTrackOptions = { optionsTrack = it },
                                            onAlbumClick = { openCollection(CollectionDetailType.AlbumCollection(it)) },
                                            onArtistClick = { openCollection(CollectionDetailType.ArtistCollection(it)) },
                                            onRemotePlaylistClick = { openCollection(CollectionDetailType.RemotePlaylistCollection(it)) },
                                            onOpenYouTubeLogin = openYouTubeLogin,
                                            onOpenListenTogether = openListenTogether,
                                            activeRoomCode = activeRoomCode,
                                            onOpenSettings = { onOverlayPageChange(OverlayPage.SETTINGS) },
                                            onOpenStats = { onOverlayPageChange(OverlayPage.STATS) },
                                            onPlayNext = swipePlayNext,
                                            onAddToQueue = swipeAddToQueue,
                                            swipeQueueLabel = swipeQueueLabel
                                        )
                                        1 -> ExploreScreen(
                                            homeState = homeState,
                                            currentTrackId = playerState.currentTrack?.id,
                                            isPlaying = playerState.isPlaying,
                                            likedIds = likedIds,
                                            bottomContentPadding = bottomListPadding,
                                            onMoodCardClick = { openCollection(CollectionDetailType.MoodCollection(it)) },
                                            onAlbumClick = { openCollection(CollectionDetailType.AlbumCollection(it)) },
                                            onPlayTrack = { track, list -> playTrackWithRoomSync(track, list, false) },
                                            onPlayAll = playAllWithRoomSync,
                                            onToggleLike = viewModel::toggleLike,
                                            onTrackOptions = { optionsTrack = it },
                                            onPlayNext = swipePlayNext,
                                            onAddToQueue = swipeAddToQueue,
                                            swipeQueueLabel = swipeQueueLabel
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
                                            onToggleScope = viewModel::toggleSearchScope,
                                            onFilterChange = viewModel::setSearchFilter,
                                            onDeleteHistoryItem = viewModel::deleteSearchHistoryItem,
                                            onClearHistory = viewModel::clearSearchHistory,
                                            onPlayTrack = { track, list -> playTrackWithRoomSync(track, list, false) },
                                            onToggleLike = viewModel::toggleLike,
                                            onTrackOptions = { optionsTrack = it },
                                            onAlbumClick = { openCollection(CollectionDetailType.AlbumCollection(it)) },
                                            onArtistClick = { openCollection(CollectionDetailType.ArtistCollection(it)) },
                                            onRemotePlaylistClick = { openCollection(CollectionDetailType.RemotePlaylistCollection(it)) },
                                            onPlayNext = swipePlayNext,
                                            onAddToQueue = swipeAddToQueue,
                                            swipeQueueLabel = swipeQueueLabel,
                                            onShowSnackbar = viewModel::showMessage,
                                            onOpenShazamSearch = { showShazamSearchSheet = true },
                                            onOpenLyricsSearch = { showLyricsSearchSheet = true }
                                        )
                                        3 -> ListenTogetherScreenHost(
                                            currentUser = currentGoogleUser,
                                            listenViewModel = listenViewModel,
                                            sessionState = listenSessionState,
                                            myActiveRoomsState = myActiveRoomsState,
                                            recentRoomsState = recentRoomsState,
                                            playerState = playerState,
                                            bottomContentPadding = bottomListPadding,
                                            onBack = { onSelectedTabChange(0) },
                                            onShowSnackbar = viewModel::showMessage,
                                            onSyncPlayerTogglePlayPause = togglePlayPauseWithRoomSync,
                                            onSyncPlayerNext = skipNextWithRoomSync,
                                            onForceResyncFromRoom = { room ->
                                                val track = room.currentTrackOrNull()
                                                if (track != null) {
                                                    viewModel.applyRemoteRoomSync(
                                                        track = track,
                                                        shouldPlay = room.playing,
                                                        targetPositionMs = room.effectivePositionMs(),
                                                        roomQueue = room.parsedQueue().map { it.toTrack() }
                                                    )
                                                    viewModel.showMessage("Pemutaran disinkronkan dengan ruang ${room.roomCode}")
                                                }
                                            },
                                            onPlayRoomTrackLocally = { track, queue ->
                                                viewModel.playTrack(track, queue, expandPlayer = false)
                                            },
                                            onQuickSearchTracks = viewModel::quickSearchTracksForSharedQueue,
                                            onSaveRoomAsPlaylist = { name, tracks ->
                                                viewModel.saveTracksAsPlaylist(
                                                    name = name,
                                                    description = "Disimpan dari sesi Play Bersama",
                                                    tracks = tracks
                                                )
                                            },
                                            onAuthStateChanged = onAuthStateChanged
                                        )
                                        else -> LibraryScreen(
                                            likedCount = likedTracks.size,
                                            downloadedCount = downloadedTracks.size,
                                            historyCount = playHistory.size,
                                            localCount = localTracks.size,
                                            totalPlays = listeningStats.totalPlays,
                                            youtubeAccount = youtubeAccount,
                                            playlists = playlists,
                                            bottomContentPadding = bottomListPadding,
                                            onOpenCollection = openCollection,
                                            onOpenStats = { onOverlayPageChange(OverlayPage.STATS) },
                                            onOpenListenTogether = openListenTogether,
                                            activeRoomCode = activeRoomCode,
                                            onOpenYouTubeLogin = openYouTubeLogin,
                                            onSyncYouTubeLibrary = viewModel::syncYouTubeAccountLibrary,
                                            onImportLocalAudio = { localAudioPicker.launch(arrayOf("audio/*")) },
                                            onCreatePlaylistClick = {
                                                savingQueueAsPlaylist = false
                                                showCreatePlaylistDialog = true
                                            },
                                            onDeletePlaylist = viewModel::deletePlaylist,
                                            topPlayedTracks = listeningStats.topTracks.map { it.track },
                                            likedTracks = likedTracks,
                                            recentTracks = playHistory,
                                            downloadedTracks = downloadedTracks,
                                            onPlaySmartMix = playAllWithRoomSync
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ---- Docked Floating MiniPlayer + Bottom Navigation Bar ----
                    if (overlayPage != OverlayPage.YOUTUBE_LOGIN) {
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
                                    dynamicColorEnabled = settings.dynamicArtworkColor,
                                    onExpand = { viewModel.setPlayerExpanded(true) },
                                    onTogglePlayPause = togglePlayPauseWithRoomSync,
                                    onNext = skipNextWithRoomSync,
                                    onPrevious = skipPreviousWithRoomSync,
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
                                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                    shadowElevation = 0.dp
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
                                                    onSelectedTabChange(index)
                                                    onOverlayPageChange(OverlayPage.NONE)
                                                },
                                                icon = {
                                                    if (tab.route == "listen_together" && activeRoomCode != null) {
                                                        BadgedBox(
                                                            badge = {
                                                                Badge(containerColor = MaterialTheme.colorScheme.primary)
                                                            }
                                                        ) {
                                                            Icon(
                                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                                contentDescription = tab.label
                                                            )
                                                        }
                                                    } else {
                                                        Icon(
                                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                            contentDescription = tab.label
                                                        )
                                                    }
                                                },
                                                label = {
                                                    Text(
                                                        text = tab.label,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                },
                                                colors = NavigationBarItemDefaults.colors(
                                                    selectedIconColor = MaterialTheme.colorScheme.onBackground,
                                                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                                                    indicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
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
                    }

                    // ---- 100% Opaque Full-Screen Player Overlay ----
                    AnimatedVisibility(
                        visible = playerState.isPlayerExpanded && playerState.currentTrack != null,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        val currentTrack = playerState.currentTrack
                        if (currentTrack != null) {
                            val isDownloaded = currentTrack.id in downloadedIds || currentTrack.isDownloaded
                            FullScreenPlayerModal(
                                playerState = playerState,
                                settings = settings,
                                isLiked = currentTrack.id in likedIds,
                                isDownloaded = isDownloaded,
                                downloadProgress = activeDownloads[currentTrack.id],
                                onCollapse = { viewModel.setPlayerExpanded(false) },
                                onTogglePlayPause = togglePlayPauseWithRoomSync,
                                onSeek = seekWithRoomSync,
                                onNext = skipNextWithRoomSync,
                                onPrevious = skipPreviousWithRoomSync,
                                onToggleShuffle = viewModel::toggleShuffle,
                                onCycleRepeat = viewModel::cycleRepeatMode,
                                onToggleLike = { viewModel.toggleLike(currentTrack) },
                                onDownloadClick = {
                                    if (isDownloaded) viewModel.removeDownload(currentTrack)
                                    else viewModel.downloadTrack(currentTrack)
                                },
                                onStartRadio = { viewModel.startRadio(currentTrack) },
                                onShareTrack = { openSingleStoryStudio(currentTrack) },
                                onShareLyrics = { text -> shareLyricsText(currentTrack, text) },
                                onOpenYouTubeLogin = openYouTubeLogin,
                                onOpenListenTogether = openListenTogether,
                                activeRoomCode = activeRoomCode,
                                activeRoom = activeRoom,
                                currentUserId = currentGoogleUser?.uid,
                                onQuickSearchTracks = viewModel::quickSearchTracksForSharedQueue,
                                onAddTrackToSharedQueue = if (isActiveRoomMember && listenViewModel != null) {
                                    { chosen, playNext -> listenViewModel.addTrackToRoomQueue(chosen, playNext) }
                                } else null,
                                onUpvoteSharedQueueItem = if (isActiveRoomMember && listenViewModel != null) {
                                    { idx -> listenViewModel.upvoteRoomQueueTrack(idx) }
                                } else null,
                                onMoveSharedQueueItem = if (isActiveRoomMember && listenViewModel != null) {
                                    { from, to -> listenViewModel.moveRoomQueueTrack(from, to) }
                                } else null,
                                onRemoveSharedQueueItem = if (isActiveRoomMember && listenViewModel != null) {
                                    { idx -> listenViewModel.removeRoomQueueTrack(idx) }
                                } else null,
                                onPlaySharedQueueItem = if (canControlActiveRoom && listenViewModel != null && activeRoom != null) {
                                    { idx ->
                                        val q = activeRoom.parsedQueue()
                                        val chosen = q.getOrNull(idx)
                                        if (chosen != null) {
                                            listenViewModel.playRoomQueueTrack(idx)
                                            viewModel.playTrack(chosen.toTrack(), q.map { it.toTrack() }, expandPlayer = true)
                                        }
                                    }
                                } else null,
                                onAddToPlaylist = { addToPlaylistTrack = currentTrack },
                                onTrackOptions = { optionsTrack = currentTrack },
                                onSelectQueueItem = { item -> playTrackWithRoomSync(item, playerState.queue, true) },
                                onMoveQueueItem = viewModel::moveQueueItem,
                                onRemoveQueueItem = viewModel::removeFromQueue,
                                onClearUpcomingQueue = viewModel::clearUpcomingQueue,
                                onSaveQueueAsPlaylist = {
                                    savingQueueAsPlaylist = true
                                    showCreatePlaylistDialog = true
                                },
                                onToggleAutoplay = viewModel::setAutoplayRadio,
                                onLyricsFontScaleChange = viewModel::setLyricsFontScale,
                                onSleepMinutes = viewModel::setSleepTimerMinutes,
                                onSleepEndOfTrack = viewModel::setSleepAfterCurrentTrack,
                                onEqPreset = viewModel::setEqPreset,
                                onCustomBand = viewModel::setCustomBand,
                                onBassBoostChange = viewModel::setBassBoost,
                                onNormalizeAudioChange = viewModel::setNormalizeAudio,
                                onSkipSilenceChange = viewModel::setSkipSilence,
                                onReverbChange = viewModel::setReverbEnabled,
                                onApplyVibePreset = viewModel::applyAudioVibePreset,
                                onSpeedChange = viewModel::setPlaybackSpeed,
                                onPitchChange = viewModel::setPlaybackPitch,
                                onCrossfadeChange = viewModel::setCrossfadeSeconds,
                                onToggleDrivingMode = viewModel::setDrivingMode,
                                onNudgeLyricsOffset = viewModel::nudgeLyricsOffset,
                                onResetLyricsOffset = viewModel::resetLyricsOffset,
                                onSetAbPointA = viewModel::setAbLoopPointA,
                                onSetAbPointB = viewModel::setAbLoopPointB,
                                onClearAbLoop = viewModel::clearAbLoop,
                                onSetVideoMode = viewModel::setVideoMode,
                                onSelectVideoCandidate = viewModel::selectVideoCandidate,
                                onAttachVideoTextureView = viewModel::attachVideoTextureView,
                                onClearVideoTextureView = viewModel::clearVideoTextureView,
                                isVideoFullscreen = isVideoFullscreen,
                                onToggleVideoFullscreen = onToggleVideoFullscreen,
                                onEnterPipMode = onEnterPipMode,
                                onToggleLyricsTranslation = viewModel::toggleLyricsTranslation,
                                onToggleLyricsRomanization = viewModel::toggleLyricsRomanization,
                                onAnalyzeSongMeaning = viewModel::analyzeCurrentSongMeaning,
                                unreadRoomChatCount = unreadRoomChatCount,
                                onOpenRoomChatPopup = openRoomChatPopupLambda,
                                onOpenStandByMode = { showStandByVisualizer = true },
                                onShowSnackbar = viewModel::showMessage
                            )
                        }
                    }

                    // ---- Full-Screen Desk / Bedside StandBy Clock & Visualizer Mode ----
                    if (showStandByVisualizer && playerState.currentTrack != null) {
                        val standByTrack = playerState.currentTrack!!
                        StandByVisualizerOverlay(
                            playerState = playerState,
                            isLiked = standByTrack.id in likedIds,
                            accentPrimary = MaterialTheme.colorScheme.primary,
                            onTogglePlayPause = togglePlayPauseWithRoomSync,
                            onSeek = seekWithRoomSync,
                            onPrevious = skipPreviousWithRoomSync,
                            onNext = skipNextWithRoomSync,
                            onToggleLike = { viewModel.toggleLike(standByTrack) },
                            onExitStandBy = { showStandByVisualizer = false }
                        )
                    }

                    // ---- Clickable Floating Room Chat Pill when browsing outside Bersama & Full Player ----
                    if (isActiveRoomMember &&
                        openRoomChatPopupLambda != null &&
                        !playerState.isPlayerExpanded &&
                        selectedTab != 3 &&
                        overlayPage == OverlayPage.NONE
                    ) {
                        Surface(
                            onClick = openRoomChatPopupLambda,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .navigationBarsPadding()
                                .padding(
                                    end = 16.dp,
                                    bottom = if (hasActiveTrack) {
                                        if (isExpandedWidth) 104.dp else 168.dp
                                    } else {
                                        if (isExpandedWidth) 24.dp else 94.dp
                                    }
                                )
                                .size(52.dp)
                                .testTag("floating_room_chat_fab")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                BadgedBox(
                                    badge = {
                                        if (unreadRoomChatCount > 0) {
                                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                                Text(
                                                    text = unreadRoomChatCount.coerceAtMost(9).toString(),
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChatBubble,
                                        contentDescription = "Buka popup obrolan room",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    // ---- Floating Live Room Chat Toast Overlay (visible across entire app!) ----
                    if (isActiveRoomMember && openRoomChatPopupLambda != null) {
                        FloatingRoomChatOverlay(
                            room = activeRoom,
                            currentUserId = currentGoogleUser?.uid,
                            isChatPopupOpen = showRoomChatPopup,
                            onOpenQuickChat = openRoomChatPopupLambda,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }

                    // ---- Fixed Non-Clickable Dynamic Island Lyrics Pill (Below Status Bar) ----
                    DynamicIslandLyricsOverlay(
                        playerState = playerState,
                        accentColor = MaterialTheme.colorScheme.primary,
                        visible = settings.dynamicIslandLyrics &&
                            !showStandByVisualizer &&
                            !isVideoFullscreen &&
                            !isInPipMode &&
                            !playerState.isDrivingMode,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }

        // ---- Interactive Quick Chat Room Popup Sheet ----
        if (showRoomChatPopup && activeRoom != null && activeRoom.status == "active" && listenViewModel != null) {
            RoomQuickChatBottomSheet(
                room = activeRoom,
                currentUserId = currentGoogleUser?.uid,
                onSendMessage = listenViewModel::sendChatMessage,
                onSendReaction = listenViewModel::sendReaction,
                onDismiss = { showRoomChatPopup = false }
            )
        }

        // ---- Track Options Bottom Sheet ----
        val activeOptionTrack = optionsTrack
        if (activeOptionTrack != null) {
            val detailType = detailState.type
            val isDownloaded = activeOptionTrack.id in downloadedIds || activeOptionTrack.isDownloaded
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

            val addToListenRoomLambda: (() -> Unit)? = if (isActiveRoomMember && listenViewModel != null) {
                { listenViewModel.addTrackToRoomQueue(activeOptionTrack) }
            } else null

            TrackOptionsBottomSheet(
                track = activeOptionTrack,
                isLiked = activeOptionTrack.id in likedIds,
                isDownloaded = isDownloaded,
                onDismiss = { optionsTrack = null },
                onPlayNow = { playTrackWithRoomSync(activeOptionTrack, listOf(activeOptionTrack), false) },
                onStartRadio = { viewModel.startRadio(activeOptionTrack) },
                onPlayNext = { viewModel.addToQueueNext(activeOptionTrack) },
                onAddToQueue = { viewModel.addToQueueEnd(activeOptionTrack) },
                onToggleLike = { viewModel.toggleLike(activeOptionTrack) },
                onDownloadOrRemove = {
                    if (isDownloaded) viewModel.removeDownload(activeOptionTrack)
                    else viewModel.downloadTrack(activeOptionTrack)
                },
                onAddToPlaylist = { addToPlaylistTrack = activeOptionTrack },
                onOpenArtist = {
                    viewModel.setPlayerExpanded(false)
                    openCollection(
                        CollectionDetailType.ArtistCollection(
                            ArtistSummary(
                                id = activeOptionTrack.artistBrowseId?.let { "yt_artist_$it" } ?: "yt_artist_search_${activeOptionTrack.artist}",
                                name = activeOptionTrack.artist,
                                artworkUrl = activeOptionTrack.artworkUrl,
                                genre = "YouTube Music"
                            )
                        )
                    )
                },
                onShare = { openSingleStoryStudio(activeOptionTrack) },
                onShowTechStats = { showTechStatsTrack = activeOptionTrack },
                onAddToListenRoom = addToListenRoomLambda,
                onRemoveAction = removeLambda,
                removeActionLabel = removeLabel
            )
        }

        // ---- Aesthetic Music & Multi-Track Story Studio Sheet ----
        val activeStoryRequest = storyStudioRequest
        if (activeStoryRequest != null) {
            MusicStoryStudioSheet(
                seedTrack = activeStoryRequest.seedTrack,
                candidateTracks = activeStoryRequest.candidateTracks,
                initialMultiMode = activeStoryRequest.initialMultiMode,
                initialSelectedTracks = activeStoryRequest.initialSelectedTracks,
                defaultMixTitle = activeStoryRequest.defaultMixTitle,
                accentColor = MaterialTheme.colorScheme.primary,
                onDismiss = { storyStudioRequest = null },
                onShareTextFallback = shareTrack,
                onShowSnackbar = viewModel::showMessage
            )
        }

        // ---- Shazam-style Audio & Humming Song Recognition Sheet ----
        if (showShazamSearchSheet) {
            ShazamAudioSearchSheet(
                likedIds = likedIds,
                onPauseMusicIfPlaying = viewModel::pausePlaybackForMicRecognition,
                onRecognizeAudio = viewModel::recognizeSongFromAudio,
                onPlayTrack = { track, list -> playTrackWithRoomSync(track, list, false) },
                onToggleLike = viewModel::toggleLike,
                onTrackOptions = { optionsTrack = it },
                onFallbackSearchQuery = { query ->
                    onOverlayPageChange(OverlayPage.NONE)
                    onSelectedTabChange(2)
                    viewModel.submitSearch(query)
                },
                onDismiss = { showShazamSearchSheet = false }
            )
        }

        // ---- Lyrics-to-Song Smart Text Search Sheet ----
        if (showLyricsSearchSheet) {
            LyricsTextSearchSheet(
                likedIds = likedIds,
                onSearchByLyrics = viewModel::findSongsByLyricsText,
                onPlayTrack = { track, list -> playTrackWithRoomSync(track, list, false) },
                onToggleLike = viewModel::toggleLike,
                onTrackOptions = { optionsTrack = it },
                onFallbackSearchQuery = { query ->
                    onOverlayPageChange(OverlayPage.NONE)
                    onSelectedTabChange(2)
                    viewModel.submitSearch(query)
                },
                onDismiss = { showLyricsSearchSheet = false }
            )
        }

        // ---- Info Teknis Stream (Stats for Nerds) Dialog ----
        val techTrack = showTechStatsTrack
        if (techTrack != null) {
            StreamTechStatsDialog(
                track = techTrack,
                streamSourceLabel = if (techTrack.id == playerState.currentTrack?.id) {
                    playerState.streamSourceLabel
                } else if (techTrack.isLocal) "AUDIO LOKAL" else "YOUTUBE MUSIC",
                techStats = if (techTrack.id == playerState.currentTrack?.id) {
                    playerState.streamTechStats
                } else com.example.model.StreamTechStats(),
                settings = settings,
                isVideoMode = techTrack.id == playerState.currentTrack?.id && playerState.isVideoMode,
                onDismiss = { showTechStatsTrack = null }
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
                onCreateNewClick = {
                    savingQueueAsPlaylist = false
                    showCreatePlaylistDialog = true
                }
            )
        }

        // ---- Create Playlist / Save Queue Dialog ----
        if (showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = {
                    showCreatePlaylistDialog = false
                    savingQueueAsPlaylist = false
                },
                onCreate = { name, desc ->
                    if (savingQueueAsPlaylist) {
                        viewModel.saveQueueAsPlaylist(name)
                    } else {
                        viewModel.createPlaylist(name, desc)
                    }
                    savingQueueAsPlaylist = false
                }
            )
        }
    }
}

private data class StoryStudioRequest(
    val seedTrack: Track?,
    val candidateTracks: List<Track>,
    val initialMultiMode: Boolean,
    val initialSelectedTracks: List<Track>,
    val defaultMixTitle: String
)

