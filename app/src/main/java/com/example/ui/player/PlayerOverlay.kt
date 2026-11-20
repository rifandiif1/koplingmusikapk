package com.example.ui.player

import android.view.TextureView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bedtime
import android.content.Intent
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.listentogether.ListenRoom
import com.example.model.AudioSettings
import com.example.model.EqPreset
import com.example.model.PlayerArtStyle
import com.example.model.PlayerBackgroundStyle
import com.example.model.RepeatMode as KoplingfyRepeatMode
import com.example.model.Track
import com.example.model.VideoCandidate
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import com.example.ui.components.AlbumArtImage
import com.example.ui.components.AudioVisualizerBars
import com.example.ui.components.FloatingReactionsOverlay
import com.example.ui.components.LyricCardGenerator
import com.example.ui.components.formatDuration
import com.example.ui.screens.AddToSharedQueueSearchSheet
import com.example.ui.theme.rememberDynamicArtworkPalette
import com.example.viewmodel.PlayerUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun FloatingMiniPlayer(
    playerState: PlayerUiState,
    dynamicColorEnabled: Boolean = true,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val track = playerState.currentTrack ?: return
    val progress = if (playerState.durationMs > 0L) {
        (playerState.positionMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val dynamicPalette = rememberDynamicArtworkPalette(
        artworkUrl = track.artworkUrl,
        enabled = dynamicColorEnabled
    )
    val accent = dynamicPalette.primaryAccent

    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = dragX.coerceIn(-90f, 90f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "mini_drag_x"
    )
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnPrev by rememberUpdatedState(onPrevious)
    val currentOnExpand by rememberUpdatedState(onExpand)

    val miniSurfaceColor = remember(dynamicPalette.deepAmbient, MaterialTheme.colorScheme.surfaceContainerHigh) {
        lerp(Color(0xFF18181E), dynamicPalette.deepAmbient, 0.38f)
    }

    Surface(
        onClick = onExpand,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .graphicsLayer { translationX = animatedOffsetX * 0.45f }
            .pointerInput(track.id) {
                detectDragGestures(
                    onDragStart = {
                        dragX = 0f
                        dragY = 0f
                    },
                    onDrag = { _, dragAmount ->
                        dragX += dragAmount.x
                        dragY += dragAmount.y
                    },
                    onDragEnd = {
                        when {
                            dragY < -75f && abs(dragY) > abs(dragX) -> currentOnExpand()
                            dragX < -85f && abs(dragX) > abs(dragY) -> currentOnNext()
                            dragX > 85f && abs(dragX) > abs(dragY) -> currentOnPrev()
                        }
                        dragX = 0f
                        dragY = 0f
                    },
                    onDragCancel = {
                        dragX = 0f
                        dragY = 0f
                    }
                )
            }
            .testTag("mini_player_bar"),
        shape = RoundedCornerShape(14.dp),
        color = miniSurfaceColor,
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
        shadowElevation = 12.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(44.dp)) {
                    AlbumArtImage(
                        url = track.artworkUrl,
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    if (playerState.isPlaying) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xD90A0A0C))
                                .padding(horizontal = 3.dp, vertical = 2.dp)
                        ) {
                            AudioVisualizerBars(
                                isPlaying = true,
                                color = Color.White,
                                barWidth = 2.dp,
                                maxHeight = 9.dp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = when {
                            playerState.isBuffering -> "Memuat audio…"
                            playerState.isVideoMode -> "Video • ${track.artist}"
                            else -> track.artist
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.68f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(44.dp).testTag("mini_play_pause")
                ) {
                    if (playerState.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.cd_play_pause),
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(44.dp).testTag("mini_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.cd_next_track),
                        tint = Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = Color.White.copy(alpha = 0.92f),
                trackColor = Color.White.copy(alpha = 0.14f)
            )
        }
    }
}

@Composable
fun FullScreenPlayerModal(
    playerState: PlayerUiState,
    settings: AudioSettings,
    isLiked: Boolean,
    isDownloaded: Boolean,
    downloadProgress: Float?,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleLike: () -> Unit,
    onDownloadClick: () -> Unit,
    onStartRadio: () -> Unit,
    onShareTrack: () -> Unit,
    onShareLyrics: (String) -> Unit,
    onOpenYouTubeLogin: () -> Unit,
    onOpenListenTogether: () -> Unit,
    activeRoomCode: String? = null,
    activeRoom: ListenRoom? = null,
    currentUserId: String? = null,
    onQuickSearchTracks: (suspend (String) -> List<Track>)? = null,
    onAddTrackToSharedQueue: ((Track, Boolean) -> Unit)? = null,
    onUpvoteSharedQueueItem: ((Int) -> Unit)? = null,
    onMoveSharedQueueItem: ((Int, Int) -> Unit)? = null,
    onRemoveSharedQueueItem: ((Int) -> Unit)? = null,
    onPlaySharedQueueItem: ((Int) -> Unit)? = null,
    onAddToPlaylist: () -> Unit,
    onTrackOptions: () -> Unit,
    onSelectQueueItem: (Track) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onClearUpcomingQueue: () -> Unit,
    onSaveQueueAsPlaylist: () -> Unit,
    onToggleAutoplay: (Boolean) -> Unit,
    onLyricsFontScaleChange: (Float) -> Unit,
    onSleepMinutes: (Int) -> Unit,
    onSleepEndOfTrack: () -> Unit,
    onEqPreset: (EqPreset) -> Unit,
    onCustomBand: (Int, Float) -> Unit,
    onBassBoostChange: (Float) -> Unit,
    onNormalizeAudioChange: (Boolean) -> Unit,
    onSkipSilenceChange: (Boolean) -> Unit = {},
    onReverbChange: (Boolean) -> Unit = {},
    onApplyVibePreset: (String) -> Unit = {},
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onCrossfadeChange: (Int) -> Unit = {},
    onToggleDrivingMode: (Boolean) -> Unit = {},
    onNudgeLyricsOffset: (Long) -> Unit = {},
    onResetLyricsOffset: () -> Unit = {},
    onSetAbPointA: () -> Unit = {},
    onSetAbPointB: () -> Unit = {},
    onClearAbLoop: () -> Unit = {},
    onSetVideoMode: (Boolean) -> Unit = {},
    onSelectVideoCandidate: (VideoCandidate) -> Unit = {},
    onAttachVideoTextureView: (TextureView?) -> Unit = {},
    onClearVideoTextureView: (TextureView?) -> Unit = {},
    isVideoFullscreen: Boolean = false,
    onToggleVideoFullscreen: (Boolean) -> Unit = {},
    onEnterPipMode: () -> Unit = {},
    onToggleLyricsTranslation: () -> Unit = {},
    onToggleLyricsRomanization: () -> Unit = {},
    onShowSnackbar: (String) -> Unit = {}
) {
    val track = playerState.currentTrack ?: return
    // 0 = Artwork, 1 = Lyrics, 2 = Queue
    var activeTab by remember { mutableIntStateOf(0) }
    var isLyricsImmersive by remember { mutableStateOf(false) }
    var showSleepSheet by remember { mutableStateOf(false) }
    var showAudioFxSheet by remember { mutableStateOf(false) }
    var showSharedQueueSearchSheet by remember { mutableStateOf(false) }

    BackHandler(onBack = {
        when {
            isVideoFullscreen -> onToggleVideoFullscreen(false)
            isLyricsImmersive -> isLyricsImmersive = false
            else -> onCollapse()
        }
    })

    // Auto-exit immersive lyrics if switching away from Lyrics tab
    LaunchedEffect(activeTab) {
        if (activeTab != 1 && isLyricsImmersive) {
            isLyricsImmersive = false
        }
    }

    // Keep screen on while reading lyrics or watching video
    val context = LocalContext.current
    DisposableEffect(activeTab, settings.keepScreenOnLyrics, playerState.isVideoMode) {
        val window = (context as? android.app.Activity)?.window
        if ((activeTab == 1 && settings.keepScreenOnLyrics) || playerState.isVideoMode) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val dynamicPalette = rememberDynamicArtworkPalette(
        artworkUrl = track.artworkUrl,
        enabled = settings.dynamicArtworkColor
    )
    // Smooth crossfading ambient colors between songs
    val accentPrimary by animateColorAsState(
        targetValue = dynamicPalette.primaryAccent,
        animationSpec = tween(durationMillis = 550),
        label = "ambient_primary"
    )
    val accentGlow by animateColorAsState(
        targetValue = dynamicPalette.glowHighlight,
        animationSpec = tween(durationMillis = 550),
        label = "ambient_glow"
    )
    val accentAmbient by animateColorAsState(
        targetValue = dynamicPalette.deepAmbient,
        animationSpec = tween(durationMillis = 550),
        label = "ambient_deep"
    )
    val bg = MaterialTheme.colorScheme.background

    val likeScale by animateFloatAsState(
        targetValue = if (isLiked) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "like_scale"
    )

    // Rubber-band vertical swipe-down gesture to collapse Full-Screen Player
    var dismissDragY by remember { mutableFloatStateOf(0f) }
    val animatedDismissY by animateFloatAsState(
        targetValue = dismissDragY.coerceAtLeast(0f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "player_dismiss_y"
    )
    val currentOnCollapse by rememberUpdatedState(onCollapse)

    // 100% OPAQUE solid base background + touch blocker so menu behind NEVER bleeds through!
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = animatedDismissY
                val shrink = (1f - (animatedDismissY / 2400f)).coerceIn(0.92f, 1f)
                scaleX = shrink
                scaleY = shrink
            }
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .testTag("fullscreen_player_container")
    ) {
        // Optional ambient backdrop layer rendered STRICTLY ON TOP OF solid opaque `bg`
        when (settings.playerBackgroundStyle) {
            PlayerBackgroundStyle.BLUR_ARTWORK -> {
                if (!track.artworkUrl.isNullOrBlank()) {
                    AlbumArtImage(
                        url = track.artworkUrl,
                        contentDescription = null,
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(24.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    accentAmbient.copy(alpha = 0.42f),
                                    bg.copy(alpha = 0.86f),
                                    bg.copy(alpha = 0.98f)
                                )
                            )
                        )
                )
            }
            PlayerBackgroundStyle.GRADIENT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    accentPrimary.copy(alpha = 0.28f),
                                    accentAmbient.copy(alpha = 0.14f),
                                    bg
                                )
                            )
                        )
                )
            }
            PlayerBackgroundStyle.SOLID -> Unit
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .widthIn(max = 600.dp)
                .align(Alignment.Center)
                .padding(horizontal = 22.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- Subtle Swipe-Down Drag Pill + Top Bar ----
            if (!isLyricsImmersive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (dismissDragY > 140f) {
                                        dismissDragY = 0f
                                        currentOnCollapse()
                                    } else {
                                        dismissDragY = 0f
                                    }
                                },
                                onDragCancel = { dismissDragY = 0f },
                                onVerticalDrag = { _, dragAmount ->
                                    dismissDragY = (dismissDragY + dragAmount).coerceIn(0f, 520f)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.22f))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = onCollapse,
                                modifier = Modifier.size(48.dp).testTag("player_collapse_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Tutup pemutar",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                // Metrolist / YouTube Music style "Lagu | Video" toggle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!track.isLocal) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.08f)
                        ) {
                            Row(
                                modifier = Modifier.padding(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val songSelected = !playerState.isVideoMode
                                Surface(
                                    onClick = {
                                        activeTab = 0
                                        onSetVideoMode(false)
                                    },
                                    shape = RoundedCornerShape(50),
                                    color = if (songSelected) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                                    modifier = Modifier.testTag("player_mode_song")
                                ) {
                                    Text(
                                        text = "Lagu",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (songSelected) FontWeight.SemiBold else FontWeight.Medium
                                        ),
                                        color = if (songSelected) Color.White else Color.White.copy(alpha = 0.62f),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                                    )
                                }
                                Surface(
                                    onClick = {
                                        activeTab = 0
                                        onSetVideoMode(true)
                                    },
                                    shape = RoundedCornerShape(50),
                                    color = if (playerState.isVideoMode) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                                    modifier = Modifier.testTag("player_mode_video")
                                ) {
                                    Text(
                                        text = "Video",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (playerState.isVideoMode) FontWeight.SemiBold else FontWeight.Medium
                                        ),
                                        color = if (playerState.isVideoMode) Color.White else Color.White.copy(alpha = 0.62f),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "AUDIO LOKAL",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = track.album.ifBlank { playerState.streamSourceLabel },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 180.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onToggleDrivingMode(true) },
                        modifier = Modifier.size(40.dp).testTag("player_driving_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DirectionsCar,
                            contentDescription = "Mode Berkendara",
                            tint = if (playerState.isDrivingMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenListenTogether,
                        modifier = Modifier.size(40.dp).testTag("player_listen_together_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = "Play Bersama",
                            tint = if (activeRoomCode != null) accentPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    IconButton(
                        onClick = onTrackOptions,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = "Opsi lainnya",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isLyricsImmersive) 2.dp else 8.dp))

            // ---- Center Stage: Artwork / Video (Lyric Video priority) / Synced Lyrics / Queue ----
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = Pair(activeTab, playerState.isVideoMode),
                    label = "player_stage"
                ) { (tab, videoMode) ->
                    when {
                        tab == 0 && videoMode && !isVideoFullscreen -> PlayerVideoStage(
                            playerState = playerState,
                            onSelectCandidate = onSelectVideoCandidate,
                            onRetryVideo = { onSetVideoMode(true) },
                            onBackToAudio = { onSetVideoMode(false) },
                            onAttachTextureView = onAttachVideoTextureView,
                            onClearTextureView = onClearVideoTextureView,
                            onEnterPipMode = onEnterPipMode,
                            onEnterFullscreen = { onToggleVideoFullscreen(true) }
                        )
                        tab == 0 && videoMode && isVideoFullscreen -> {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                        tab == 0 -> PlayerArtworkStage(
                            track = track,
                            isPlaying = playerState.isPlaying,
                            artStyle = settings.playerArtStyle,
                            accentColor = accentPrimary,
                            onSwipeNext = onNext,
                            onSwipePrev = onPrevious
                        )
                        tab == 1 -> SyncedLyricsStage(
                            track = track,
                            playerState = playerState,
                            fontScale = settings.lyricsFontScale,
                            accentPrimary = accentPrimary,
                            accentGlow = accentGlow,
                            isImmersive = isLyricsImmersive,
                            onToggleImmersive = { isLyricsImmersive = !isLyricsImmersive },
                            onFontScaleChange = onLyricsFontScaleChange,
                            onNudgeOffset = onNudgeLyricsOffset,
                            onResetOffset = onResetLyricsOffset,
                            onToggleTranslation = onToggleLyricsTranslation,
                            onToggleRomanization = onToggleLyricsRomanization,
                            onShareLyrics = onShareLyrics,
                            onShowSnackbar = onShowSnackbar,
                            onSeekTo = onSeek
                        )
                        else -> QueueStage(
                            queue = playerState.queue,
                            currentIndex = playerState.currentIndex,
                            isPlaying = playerState.isPlaying,
                            autoplayRadio = settings.autoplayRadio,
                            accentColor = accentPrimary,
                            activeRoom = activeRoom,
                            currentUserId = currentUserId,
                            onToggleAutoplay = onToggleAutoplay,
                            onSelect = onSelectQueueItem,
                            onMoveItem = onMoveQueueItem,
                            onRemove = onRemoveQueueItem,
                            onClearUpcoming = onClearUpcomingQueue,
                            onSaveQueue = onSaveQueueAsPlaylist,
                            onOpenAddSharedQueue = if (onQuickSearchTracks != null && onAddTrackToSharedQueue != null) {
                                { showSharedQueueSearchSheet = true }
                            } else null,
                            onUpvoteSharedQueue = onUpvoteSharedQueueItem,
                            onMoveSharedQueue = onMoveSharedQueueItem,
                            onRemoveSharedQueue = onRemoveSharedQueueItem,
                            onPlaySharedQueue = onPlaySharedQueueItem
                        )
                    }
                }
            }

            // ---- Error / Login Required Banner (ZERO Preview fallback!) ----
            if (playerState.audioError != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = playerState.audioError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        if (playerState.requiresYouTubeLogin) {
                            Button(
                                onClick = onOpenYouTubeLogin,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("player_login_ytm_button")
                            ) {
                                Icon(Icons.Rounded.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Login YouTube Music (Buka Full Audio)")
                            }
                        }
                    }
                }
            }

            // ---- Immersive Lyrics Compact Bottom Pill OR Full Playback Controls ----
            if (isLyricsImmersive && activeTab == 1) {
                Spacer(modifier = Modifier.height(8.dp))
                val immDuration = playerState.durationMs.coerceAtLeast(1L)
                val immProgress = (playerState.positionMs.toFloat() / immDuration.toFloat()).coerceIn(0f, 1f)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth().testTag("immersive_lyrics_controls")
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.68f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = onPrevious, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Sebelumnya", tint = Color.White)
                            }
                            Surface(
                                onClick = onTogglePlayPause,
                                shape = CircleShape,
                                color = Color.White,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = stringResource(R.string.cd_play_pause),
                                        tint = Color(0xFF0A0A0C),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Rounded.SkipNext, contentDescription = "Berikutnya", tint = Color.White)
                            }
                            IconButton(
                                onClick = { isLyricsImmersive = false },
                                modifier = Modifier.size(40.dp).testTag("exit_immersive_lyrics_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloseFullscreen,
                                    contentDescription = "Keluar layar penuh lirik",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { immProgress },
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.14f)
                        )
                    }
                }
            } else {
            // ---- Track Title & Metrolist Quick Action Row ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Offline Download button
                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier.size(44.dp).testTag("player_download_button")
                ) {
                    when {
                        downloadProgress != null -> {
                            CircularProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = accentPrimary
                            )
                        }
                        isDownloaded -> {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Tersimpan offline",
                                tint = accentPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = "Unduh offline",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onAddToPlaylist,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = "Simpan ke playlist",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onToggleLike,
                    modifier = Modifier
                        .size(44.dp)
                        .scale(likeScale)
                        .testTag("player_like_button")
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = stringResource(R.string.cd_like_track),
                        tint = if (isLiked) accentPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ---- Scrubber & Timestamps ----
            val duration = playerState.durationMs.coerceAtLeast(1L)
            var isDragging by remember { mutableStateOf(false) }
            var dragFraction by remember { mutableFloatStateOf(0f) }
            val currentFraction = if (isDragging) dragFraction else (playerState.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

            Slider(
                value = currentFraction,
                onValueChange = {
                    isDragging = true
                    dragFraction = it
                },
                onValueChangeFinished = {
                    onSeek((dragFraction * duration).toLong())
                    isDragging = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.20f)
                ),
                modifier = Modifier.fillMaxWidth().testTag("player_seek_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(if (isDragging) (dragFraction * duration).toLong() else playerState.positionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (playerState.abLoopStartMs != null) {
                    Surface(
                        onClick = onClearAbLoop,
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.14f),
                        modifier = Modifier.testTag("player_ab_loop_pill")
                    ) {
                        val bLabel = playerState.abLoopEndMs?.let { formatDuration(it) } ?: "…"
                        Text(
                            text = "A-B ${formatDuration(playerState.abLoopStartMs)} → $bLabel ✕",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ---- Clean Playback Controls ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.size(48.dp).testTag("player_shuffle_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Acak",
                        tint = if (playerState.isShuffleEnabled) accentPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(54.dp).testTag("player_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.cd_previous_track),
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Surface(
                    onClick = onTogglePlayPause,
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(68.dp).testTag("player_play_pause_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (playerState.isBuffering) {
                            CircularProgressIndicator(
                                color = Color(0xFF0A0A0C),
                                modifier = Modifier.size(26.dp),
                                strokeWidth = 2.8.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.cd_play_pause),
                                tint = Color(0xFF0A0A0C),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(54.dp).testTag("player_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.cd_next_track),
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = onCycleRepeat,
                    modifier = Modifier.size(48.dp).testTag("player_repeat_button")
                ) {
                    val icon = if (playerState.repeatMode == KoplingfyRepeatMode.ONE) {
                        Icons.Rounded.RepeatOne
                    } else Icons.Rounded.Repeat
                    Icon(
                        imageVector = icon,
                        contentDescription = "Ulangi",
                        tint = if (playerState.repeatMode != KoplingfyRepeatMode.OFF) {
                            accentPrimary
                        } else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ---- Clean Bottom Utility Bar (Lyrics, Queue, Radio, Timer, EQ, Share) ----
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.05f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    UtilityChip(
                        icon = Icons.Rounded.Lyrics,
                        label = "Lirik",
                        active = activeTab == 1,
                        accentColor = Color.White,
                        onClick = { activeTab = if (activeTab == 1) 0 else 1 },
                        modifier = Modifier.testTag("player_tab_lyrics")
                    )
                    UtilityChip(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        label = if (activeRoomCode != null) "Antrian+" else "Antrean",
                        active = activeTab == 2,
                        accentColor = accentPrimary,
                        onClick = { activeTab = if (activeTab == 2) 0 else 2 },
                        modifier = Modifier.testTag("player_tab_queue")
                    )
                    UtilityChip(
                        icon = Icons.Rounded.Radio,
                        label = if (playerState.isLoadingRadio) "…" else "Radio",
                        active = playerState.isLoadingRadio,
                        accentColor = accentPrimary,
                        onClick = onStartRadio,
                        modifier = Modifier.testTag("player_radio_button")
                    )
                    val timerActive = playerState.sleepTimerRemainingSec != null || playerState.sleepAfterCurrentTrack
                    val timerLabel = when {
                        playerState.sleepTimerRemainingSec != null -> {
                            val m = playerState.sleepTimerRemainingSec / 60
                            val s = playerState.sleepTimerRemainingSec % 60
                            "%d:%02d".format(m, s)
                        }
                        playerState.sleepAfterCurrentTrack -> "Akhir"
                        else -> "Timer"
                    }
                    UtilityChip(
                        icon = Icons.Rounded.Bedtime,
                        label = timerLabel,
                        active = timerActive,
                        accentColor = accentPrimary,
                        onClick = { showSleepSheet = true },
                        modifier = Modifier.testTag("player_sleep_timer_button")
                    )
                    UtilityChip(
                        icon = Icons.Rounded.Equalizer,
                        label = "EQ",
                        active = settings.eqPreset != EqPreset.FLAT ||
                            settings.playbackSpeed != 1.0f ||
                            settings.bassBoostStrength > 0.05f ||
                            settings.crossfadeSeconds > 0 ||
                            settings.skipSilence ||
                            settings.reverbEnabled ||
                            playerState.abLoopStartMs != null,
                        accentColor = accentPrimary,
                        onClick = { showAudioFxSheet = true },
                        modifier = Modifier.testTag("player_audio_fx_button")
                    )
                    UtilityChip(
                        icon = Icons.Rounded.Share,
                        label = "Bagikan",
                        active = false,
                        accentColor = accentPrimary,
                        onClick = onShareTrack,
                        modifier = Modifier.testTag("player_share_button")
                    )
                }
            }
            }
        }

        // Fullscreen Landscape Video Overlay (Edge-to-Edge)
        if (isVideoFullscreen && playerState.isVideoMode) {
            FullscreenVideoLandscapeOverlay(
                track = track,
                playerState = playerState,
                onTogglePlayPause = onTogglePlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onSeek = onSeek,
                onEnterPipMode = onEnterPipMode,
                onExitFullscreen = { onToggleVideoFullscreen(false) },
                onAttachTextureView = onAttachVideoTextureView,
                onClearTextureView = onClearVideoTextureView
            )
        }

        // Live Floating Emoji Reactions over Now Playing when in active Play Bersama room
        if (activeRoom != null && activeRoom.status == "active" && !isVideoFullscreen) {
            FloatingReactionsOverlay(
                triggerKey = "${activeRoom.updatedAt?.seconds}_${activeRoom.lastActionBy}",
                reactionText = activeRoom.lastActionBy,
                modifier = Modifier.fillMaxSize()
            )
        }

        // High-Contrast Driving Mode full overlay
        if (playerState.isDrivingMode) {
            DrivingModeOverlay(
                playerState = playerState,
                isLiked = isLiked,
                accentPrimary = accentPrimary,
                activeRoomCode = activeRoomCode,
                onTogglePlayPause = onTogglePlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onToggleLike = onToggleLike,
                onExitDrivingMode = { onToggleDrivingMode(false) }
            )
        }
    }

    if (showSleepSheet) {
        SleepTimerBottomSheet(
            remainingSec = playerState.sleepTimerRemainingSec,
            endOfTrack = playerState.sleepAfterCurrentTrack,
            onDismiss = { showSleepSheet = false },
            onSelectMinutes = {
                onSleepMinutes(it)
                showSleepSheet = false
            },
            onSelectEndOfTrack = {
                onSleepEndOfTrack()
                showSleepSheet = false
            }
        )
    }

    if (showAudioFxSheet) {
        AudioFxBottomSheet(
            settings = settings,
            onDismiss = { showAudioFxSheet = false },
            onEqPreset = onEqPreset,
            onCustomBand = onCustomBand,
            onBassBoostChange = onBassBoostChange,
            onNormalizeAudioChange = onNormalizeAudioChange,
            onSkipSilenceChange = onSkipSilenceChange,
            onReverbChange = onReverbChange,
            onApplyVibePreset = onApplyVibePreset,
            onSpeedChange = onSpeedChange,
            onPitchChange = onPitchChange,
            onCrossfadeChange = onCrossfadeChange,
            abLoopStartMs = playerState.abLoopStartMs,
            abLoopEndMs = playerState.abLoopEndMs,
            onSetAbPointA = onSetAbPointA,
            onSetAbPointB = onSetAbPointB,
            onClearAbLoop = onClearAbLoop
        )
    }

    if (showSharedQueueSearchSheet && onQuickSearchTracks != null && onAddTrackToSharedQueue != null) {
        AddToSharedQueueSearchSheet(
            onDismiss = { showSharedQueueSearchSheet = false },
            onSearch = onQuickSearchTracks,
            onAddTrack = { chosen, playNext ->
                onAddTrackToSharedQueue(chosen, playNext)
            }
        )
    }
}

@Composable
private fun PlayerVideoStage(
    playerState: PlayerUiState,
    onSelectCandidate: (VideoCandidate) -> Unit,
    onRetryVideo: () -> Unit,
    onBackToAudio: () -> Unit,
    onAttachTextureView: (TextureView?) -> Unit,
    onClearTextureView: (TextureView?) -> Unit,
    onEnterPipMode: () -> Unit = {},
    onEnterFullscreen: () -> Unit = {}
) {
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    val activeCandidate = remember(playerState.videoCandidates, playerState.selectedVideoId) {
        playerState.videoCandidates.firstOrNull { it.videoId == playerState.selectedVideoId }
            ?: playerState.videoCandidates.firstOrNull()
    }

    DisposableEffect(textureViewRef) {
        val tv = textureViewRef
        if (tv != null) {
            onAttachTextureView(tv)
        }
        onDispose {
            if (tv != null) {
                onClearTextureView(tv)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 16:9 Inline ExoPlayer Video Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .testTag("player_video_viewport"),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).also { tv ->
                        textureViewRef = tv
                    }
                },
                update = { tv ->
                    if (textureViewRef !== tv) {
                        textureViewRef = tv
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Active quality + Lyric Video pill overlay (top-left)
            if (!playerState.isLoadingVideo && playerState.videoError == null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeCandidate?.isLyricVideo == true) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xCC0A0A0C)
                        ) {
                            Text(
                                text = "LYRIC VIDEO",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (!playerState.videoQualityLabel.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xB30A0A0C)
                        ) {
                            Text(
                                text = playerState.videoQualityLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // PiP & Fullscreen Landscape buttons (bottom-right)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onEnterPipMode,
                        shape = CircleShape,
                        color = Color(0xB30A0A0C),
                        modifier = Modifier.size(36.dp).testTag("video_pip_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.PictureInPictureAlt,
                                contentDescription = "Mode Picture-in-Picture",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Surface(
                        onClick = onEnterFullscreen,
                        shape = CircleShape,
                        color = Color(0xB30A0A0C),
                        modifier = Modifier.size(36.dp).testTag("video_fullscreen_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Fullscreen,
                                contentDescription = "Video layar penuh lanskap",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Loading overlay
            if (playerState.isLoadingVideo || (playerState.isBuffering && playerState.videoError == null)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = if (playerState.isLoadingVideo) "Mencari Lyric Video terbaik…" else "Memuat video…",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // Error overlay with Retry & Fallback to Audio
            if (playerState.videoError != null && !playerState.isLoadingVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xE60A0A0C))
                        .padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OndemandVideo,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = playerState.videoError,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                onClick = onRetryVideo,
                                shape = RoundedCornerShape(50),
                                color = Color.White,
                                modifier = Modifier.testTag("video_retry_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        tint = Color(0xFF0A0A0C),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Coba Lagi",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFF0A0A0C)
                                    )
                                }
                            }
                            Surface(
                                onClick = onBackToAudio,
                                shape = RoundedCornerShape(50),
                                color = Color.White.copy(alpha = 0.14f),
                                modifier = Modifier.testTag("video_back_to_audio_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Mode Lagu",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Candidate Picker: Prioritizes Lyric Videos first, lets user switch to other Lyric/Music Videos
        if (playerState.videoCandidates.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Pilihan Video (Prioritas Lyric Video)",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.65f)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("video_candidates_row")
                ) {
                    items(playerState.videoCandidates, key = { it.videoId }) { candidate ->
                        val selected = candidate.videoId == playerState.selectedVideoId
                        Surface(
                            onClick = { onSelectCandidate(candidate) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
                            border = if (selected) BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)) else null,
                            modifier = Modifier
                                .width(204.dp)
                                .testTag("video_candidate_${candidate.videoId}")
                        ) {
                            Row(
                                modifier = Modifier.padding(7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AlbumArtImage(
                                    url = candidate.thumbnailUrl,
                                    contentDescription = candidate.title,
                                    modifier = Modifier
                                        .width(56.dp)
                                        .height(36.dp),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (candidate.isLyricVideo) {
                                            Text(
                                                text = "LYRIC",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = Color(0xFF1ED760)
                                            )
                                        }
                                        Text(
                                            text = candidate.title,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                            ),
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = if (candidate.durationLabel.isNotBlank()) {
                                            "${candidate.channel} • ${candidate.durationLabel}"
                                        } else candidate.channel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.58f),
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
    }
}

@Composable
private fun FullscreenVideoLandscapeOverlay(
    track: Track,
    playerState: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onEnterPipMode: () -> Unit,
    onExitFullscreen: () -> Unit,
    onAttachTextureView: (TextureView?) -> Unit,
    onClearTextureView: (TextureView?) -> Unit
) {
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var showHud by remember { mutableStateOf(true) }

    DisposableEffect(textureViewRef) {
        val tv = textureViewRef
        if (tv != null) onAttachTextureView(tv)
        onDispose { if (tv != null) onClearTextureView(tv) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { showHud = !showHud }
            )
            .testTag("fullscreen_video_landscape_overlay"),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx -> TextureView(ctx).also { textureViewRef = it } },
            update = { tv -> if (textureViewRef !== tv) textureViewRef = tv },
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
        )

        if (showHud) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                // Top Row: Title + PiP + Exit Fullscreen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${track.artist} • ${playerState.videoQualityLabel.ifBlank { "Video" }}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onEnterPipMode, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = "Picture-in-Picture", tint = Color.White)
                        }
                        IconButton(
                            onClick = onExitFullscreen,
                            modifier = Modifier.size(44.dp).testTag("exit_video_fullscreen_button")
                        ) {
                            Icon(Icons.Rounded.FullscreenExit, contentDescription = "Keluar layar penuh", tint = Color.White)
                        }
                    }
                }

                // Center Play/Pause/Prev/Next
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(54.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Sebelumnya", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    Surface(
                        onClick = onTogglePlayPause,
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.cd_play_pause),
                                tint = Color(0xFF0A0A0C),
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(54.dp)) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Berikutnya", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }

                // Bottom Seekbar
                val dur = playerState.durationMs.coerceAtLeast(1L)
                var dragPos by remember { mutableFloatStateOf(0f) }
                var isDragging by remember { mutableStateOf(false) }
                val frac = if (isDragging) dragPos else (playerState.positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    Slider(
                        value = frac,
                        onValueChange = { isDragging = true; dragPos = it },
                        onValueChangeFinished = {
                            onSeek((dragPos * dur).toLong())
                            isDragging = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatDuration(playerState.positionMs), style = MaterialTheme.typography.labelSmall, color = Color.White)
                        Text(formatDuration(dur), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.78f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerArtworkStage(
    track: Track,
    isPlaying: Boolean,
    artStyle: PlayerArtStyle,
    accentColor: Color,
    onSwipeNext: () -> Unit,
    onSwipePrev: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinyl_angle"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "art_scale"
    )

    var totalDrag by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .aspectRatio(1f)
            .scale(scale)
            .pointerInput(track.id) {
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                    onDragEnd = {
                        when {
                            totalDrag < -90f -> onSwipeNext()
                            totalDrag > 90f -> onSwipePrev()
                        }
                        totalDrag = 0f
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (artStyle == PlayerArtStyle.VINYL) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(if (isPlaying) rotation else 0f),
                shape = CircleShape,
                color = Color(0xFF101014),
                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.14f)),
                shadowElevation = 20.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AlbumArtImage(
                        url = track.artworkUrl,
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxSize(0.76f),
                        shape = CircleShape
                    )
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.85f))
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF141418),
                shadowElevation = 20.dp
            ) {
                AlbumArtImage(
                    url = track.artworkUrl,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

@Composable
private fun SyncedLyricsStage(
    track: Track,
    playerState: PlayerUiState,
    fontScale: Float,
    accentPrimary: Color,
    accentGlow: Color,
    isImmersive: Boolean = false,
    onToggleImmersive: () -> Unit = {},
    onFontScaleChange: (Float) -> Unit,
    onNudgeOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onToggleTranslation: () -> Unit,
    onToggleRomanization: () -> Unit,
    onShareLyrics: (String) -> Unit,
    onShowSnackbar: (String) -> Unit,
    onSeekTo: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lyrics = playerState.lyricsState
    val listState = rememberLazyListState()

    var isSelectingCard by remember(track.id) { mutableStateOf(false) }
    val selectedLineIndices = remember(track.id) { mutableStateListOf<Int>() }
    var isSavingCard by remember { mutableStateOf(false) }

    val adjustedPosMs = (playerState.positionMs + playerState.lyricsOffsetMs).coerceAtLeast(0L)
    val activeLineIndex = remember(lyrics.syncedLines, adjustedPosMs) {
        lyrics.syncedLines.indexOfLast { it.timestampMs <= adjustedPosMs + 250L }
    }

    val plainLines = remember(lyrics.plainLyrics) {
        lyrics.plainLyrics.orEmpty()
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
    val hasSelectableLines = lyrics.syncedLines.isNotEmpty() || plainLines.isNotEmpty()

    LaunchedEffect(activeLineIndex, isSelectingCard) {
        if (activeLineIndex >= 0 && !isSelectingCard) {
            runCatching { listState.animateScrollToItem((activeLineIndex - 1).coerceAtLeast(0)) }
        }
    }

    val toggleLineSelection: (Int) -> Unit = { idx ->
        if (idx in selectedLineIndices) {
            selectedLineIndices.remove(idx)
        } else if (selectedLineIndices.size < 4) {
            selectedLineIndices.add(idx)
            selectedLineIndices.sort()
        } else {
            onShowSnackbar("Maksimal 4 baris lirik untuk 1 Kartu Lirik")
        }
    }

    val saveOrShareCard: (Boolean) -> Unit = { shareImmediately ->
        if (!isSavingCard && selectedLineIndices.isNotEmpty()) {
            val chosenLines = if (lyrics.syncedLines.isNotEmpty()) {
                selectedLineIndices.mapNotNull { lyrics.syncedLines.getOrNull(it)?.text }
            } else {
                selectedLineIndices.mapNotNull { plainLines.getOrNull(it) }
            }
            if (chosenLines.isNotEmpty()) {
                isSavingCard = true
                scope.launch {
                    val res = withContext(Dispatchers.IO) {
                        LyricCardGenerator.saveLyricCardToGallery(
                            context = context,
                            track = track,
                            selectedLines = chosenLines,
                            accentColorInt = accentPrimary.toArgb()
                        )
                    }
                    isSavingCard = false
                    res.onSuccess { uri ->
                        isSelectingCard = false
                        selectedLineIndices.clear()
                        if (shareImmediately) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(intent, "Bagikan Kartu Lirik"))
                            }
                        } else {
                            onShowSnackbar("Kartu Lirik disimpan ke Galeri!")
                        }
                    }.onFailure { err ->
                        onShowSnackbar("Gagal membuat Kartu Lirik: ${err.message}")
                    }
                }
            }
        }
    }

    val spotifyCardBase = remember(accentPrimary) {
        lerp(Color(0xFF141418), accentPrimary, 0.20f)
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = spotifyCardBase
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact Spotify-inspired Lyrics Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Lirik",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White
                    )
                    if (lyrics.providerName.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(alpha = 0.24f)
                        ) {
                            Text(
                                text = lyrics.providerName,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White.copy(alpha = 0.82f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onFontScaleChange((fontScale - 0.1f).coerceAtLeast(0.8f)) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Rounded.TextDecrease,
                            contentDescription = "Perkecil huruf",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { onFontScaleChange((fontScale + 0.1f).coerceAtMost(1.4f)) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Rounded.TextIncrease,
                            contentDescription = "Perbesar huruf",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    val shareableText = when {
                        lyrics.syncedLines.isNotEmpty() -> lyrics.syncedLines.joinToString("\n") { it.text }
                        !lyrics.plainLyrics.isNullOrBlank() -> lyrics.plainLyrics
                        else -> null
                    }
                    if (shareableText != null) {
                        IconButton(
                            onClick = { onShareLyrics(shareableText) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Share,
                                contentDescription = "Bagikan lirik",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onToggleImmersive,
                        modifier = Modifier.size(34.dp).testTag("toggle_immersive_lyrics")
                    ) {
                        Icon(
                            imageVector = if (isImmersive) Icons.Rounded.CloseFullscreen else Icons.Rounded.OpenInFull,
                            contentDescription = if (isImmersive) "Perkecil tampilan lirik" else "Lirik layar penuh",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Second compact toolbar: Terjemahan ID + Sync Offset + Kartu Lirik
            if (hasSelectableLines && !lyrics.isLoading && !lyrics.isInstrumental) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            Surface(
                                onClick = onToggleTranslation,
                                shape = RoundedCornerShape(50),
                                color = if (lyrics.showTranslation) Color.White else Color.Black.copy(alpha = 0.28f),
                                modifier = Modifier.testTag("toggle_lyrics_translation")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    if (lyrics.isTranslating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(13.dp),
                                            strokeWidth = 1.8.dp,
                                            color = if (lyrics.showTranslation) Color(0xFF0A0A0C) else Color.White
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Translate,
                                            contentDescription = "Terjemahan Bahasa Indonesia",
                                            tint = if (lyrics.showTranslation) Color(0xFF0A0A0C) else Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = when {
                                            lyrics.isTranslating -> "Menerjemahkan…"
                                            lyrics.showTranslation -> "ID Aktif"
                                            else -> "Terjemahan ID"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (lyrics.showTranslation) Color(0xFF0A0A0C) else Color.White
                                    )
                                }
                            }
                        }

                        item {
                            Surface(
                                onClick = onToggleRomanization,
                                shape = RoundedCornerShape(50),
                                color = if (lyrics.showRomanization) Color.White else Color.Black.copy(alpha = 0.28f),
                                modifier = Modifier.testTag("toggle_lyrics_romanization")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    if (lyrics.isRomanizing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(13.dp),
                                            strokeWidth = 1.8.dp,
                                            color = if (lyrics.showRomanization) Color(0xFF0A0A0C) else Color.White
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Spellcheck,
                                            contentDescription = "Romanisasi Romaji Hangul Pinyin",
                                            tint = if (lyrics.showRomanization) Color(0xFF0A0A0C) else Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    val activeRomLabel = lyrics.romanizationScriptLabel
                                        .takeIf { it.isNotBlank() && !it.equals("Latin", ignoreCase = true) }
                                        ?: "Romaji"
                                    Text(
                                        text = when {
                                            lyrics.isRomanizing -> "Romanisasi…"
                                            lyrics.showRomanization -> "$activeRomLabel Aktif"
                                            else -> "Romaji / Baca"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (lyrics.showRomanization) Color(0xFF0A0A0C) else Color.White
                                    )
                                }
                            }
                        }

                        if (lyrics.syncedLines.isNotEmpty()) {
                            item {
                                Surface(
                                    onClick = { onNudgeOffset(-500L) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.24f),
                                    modifier = Modifier.testTag("lyrics_offset_minus")
                                ) {
                                    Text(
                                        text = "-0.5s",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                            item {
                                Surface(
                                    onClick = onResetOffset,
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (playerState.lyricsOffsetMs != 0L) Color.White.copy(alpha = 0.22f)
                                    else Color.Black.copy(alpha = 0.18f),
                                    modifier = Modifier.testTag("lyrics_offset_reset")
                                ) {
                                    val sec = playerState.lyricsOffsetMs / 1000f
                                    Text(
                                        text = if (playerState.lyricsOffsetMs == 0L) "Sync 0s" else "%+.1fs".format(sec),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                            item {
                                Surface(
                                    onClick = { onNudgeOffset(500L) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.24f),
                                    modifier = Modifier.testTag("lyrics_offset_plus")
                                ) {
                                    Text(
                                        text = "+0.5s",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        item {
                            Surface(
                                onClick = {
                                    isSelectingCard = !isSelectingCard
                                    if (!isSelectingCard) selectedLineIndices.clear()
                                },
                                shape = RoundedCornerShape(50),
                                color = if (isSelectingCard) Color.White else Color.Black.copy(alpha = 0.26f),
                                modifier = Modifier.testTag("toggle_lyric_card_mode")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = if (isSelectingCard) Color(0xFF0A0A0C) else Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isSelectingCard) "Pilih (1–4)" else "Kartu Lirik",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isSelectingCard) Color(0xFF0A0A0C) else Color.White
                                    )
                                }
                            }
                        }
                    }

                    val activeLyricsError = lyrics.translationError ?: lyrics.romanizationError
                    if (activeLyricsError != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Black.copy(alpha = 0.36f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = activeLyricsError,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFB4AB),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    lyrics.isLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "Mencari lirik LRCLIB & YouTube Music…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                    lyrics.isInstrumental -> {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "♪ Lagu Instrumental ♪\nNikmati alunan musiknya.",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                color = Color.White
                            )
                        }
                    }
                    lyrics.syncedLines.isNotEmpty() -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 56.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            itemsIndexed(lyrics.syncedLines) { idx, line ->
                                val isActive = idx == activeLineIndex
                                val isPast = idx < activeLineIndex
                                val isSelected = idx in selectedLineIndices

                                if (isSelectingCard) {
                                    Surface(
                                        onClick = { toggleLineSelection(idx) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isSelected) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.20f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) Color.White else Color.White.copy(alpha = 0.16f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = line.text,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontSize = (18f * fontScale).sp,
                                                    lineHeight = (25f * fontScale).sp,
                                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                                                ),
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.72f),
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = "Dipilih",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // Authentic Spotify-style line-by-line synced lyrics:
                                    // Pure crisp white for the active line, translucent white for past & upcoming lines.
                                    val targetColor = when {
                                        isActive -> Color.White
                                        isPast -> Color.White.copy(alpha = 0.42f)
                                        else -> Color.White.copy(alpha = 0.58f)
                                    }
                                    val animatedColor by animateColorAsState(
                                        targetValue = targetColor,
                                        animationSpec = tween(durationMillis = 240),
                                        label = "spotify_lyric_color"
                                    )
                                    val romanizedLine = if (lyrics.showRomanization) {
                                        lyrics.romanizedSyncedLines.getOrNull(idx)
                                            ?.takeIf { it.isNotBlank() && !it.equals(line.text, ignoreCase = true) }
                                    } else null
                                    val translatedLine = if (lyrics.showTranslation) {
                                        lyrics.translatedSyncedLines.getOrNull(idx)
                                            ?.takeIf { it.isNotBlank() && !it.equals(line.text, ignoreCase = true) }
                                    } else null
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                onSeekTo((line.timestampMs - playerState.lyricsOffsetMs).coerceAtLeast(0L))
                                            }
                                            .padding(vertical = 2.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = line.text,
                                            style = MaterialTheme.typography.headlineSmall.copy(
                                                fontSize = ((if (isActive) 22f else 19f) * fontScale).sp,
                                                lineHeight = ((if (isActive) 30f else 27f) * fontScale).sp,
                                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold
                                            ),
                                            color = animatedColor
                                        )
                                        if (romanizedLine != null) {
                                            Text(
                                                text = romanizedLine,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = ((if (isActive) 14.5f else 13f) * fontScale).sp,
                                                    lineHeight = ((if (isActive) 20f else 18f) * fontScale).sp,
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                color = Color(0xFFB5F2CE).copy(alpha = if (isActive) 0.95f else 0.62f)
                                            )
                                        }
                                        if (translatedLine != null) {
                                            Text(
                                                text = translatedLine,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = ((if (isActive) 15f else 13.5f) * fontScale).sp,
                                                    lineHeight = ((if (isActive) 21f else 19f) * fontScale).sp,
                                                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
                                                ),
                                                color = animatedColor.copy(alpha = if (isActive) 0.85f else 0.65f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    plainLines.isNotEmpty() -> {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 48.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(plainLines) { idx, textLine ->
                                val isSelected = idx in selectedLineIndices
                                val romanizedPlain = if (lyrics.showRomanization) {
                                    lyrics.romanizedPlainLines.getOrNull(idx)
                                        ?.takeIf { it.isNotBlank() && !it.equals(textLine, ignoreCase = true) }
                                } else null
                                val translatedPlain = if (lyrics.showTranslation) {
                                    lyrics.translatedPlainLines.getOrNull(idx)
                                        ?.takeIf { it.isNotBlank() && !it.equals(textLine, ignoreCase = true) }
                                } else null
                                if (isSelectingCard) {
                                    Surface(
                                        onClick = { toggleLineSelection(idx) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isSelected) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.20f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) Color.White else Color.White.copy(alpha = 0.16f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = textLine,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontSize = (18f * fontScale).sp,
                                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                                                ),
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.72f),
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isSelected) {
                                                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            text = textLine,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontSize = (18.5f * fontScale).sp,
                                                lineHeight = (26f * fontScale).sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = Color.White.copy(alpha = 0.92f)
                                        )
                                        if (romanizedPlain != null) {
                                            Text(
                                                text = romanizedPlain,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = (13.5f * fontScale).sp,
                                                    lineHeight = (19f * fontScale).sp,
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                color = Color(0xFFB5F2CE).copy(alpha = 0.85f)
                                            )
                                        }
                                        if (translatedPlain != null) {
                                            Text(
                                                text = translatedPlain,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = (14f * fontScale).sp,
                                                    lineHeight = (20f * fontScale).sp,
                                                    fontWeight = FontWeight.Normal
                                                ),
                                                color = Color.White.copy(alpha = 0.68f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = lyrics.error ?: "Lirik belum tersedia untuk lagu ini.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.78f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Bottom Action Bar when creating a Lyric Quote Card
            if (isSelectingCard) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(0.8.dp, accentPrimary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { saveOrShareCard(false) },
                            enabled = selectedLineIndices.isNotEmpty() && !isSavingCard,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f).testTag("save_lyric_card_button")
                        ) {
                            if (isSavingCard) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Simpan (${selectedLineIndices.size})", maxLines = 1)
                            }
                        }
                        Button(
                            onClick = { saveOrShareCard(true) },
                            enabled = selectedLineIndices.isNotEmpty() && !isSavingCard,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f).testTag("share_lyric_card_button")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bagikan", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueStage(
    queue: List<Track>,
    currentIndex: Int,
    isPlaying: Boolean,
    autoplayRadio: Boolean,
    accentColor: Color,
    activeRoom: ListenRoom?,
    currentUserId: String?,
    onToggleAutoplay: (Boolean) -> Unit,
    onSelect: (Track) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClearUpcoming: () -> Unit,
    onSaveQueue: () -> Unit,
    onOpenAddSharedQueue: (() -> Unit)?,
    onUpvoteSharedQueue: ((Int) -> Unit)?,
    onMoveSharedQueue: ((Int, Int) -> Unit)?,
    onRemoveSharedQueue: ((Int) -> Unit)?,
    onPlaySharedQueue: ((Int) -> Unit)?
) {
    val hasActiveRoom = activeRoom != null && activeRoom.status == "active"
    var showSharedTab by remember(hasActiveRoom) { mutableStateOf(hasActiveRoom) }
    val sharedQueue = remember(activeRoom?.queueTracks) { activeRoom?.parsedQueue().orEmpty() }
    val isHost = activeRoom != null && currentUserId != null && activeRoom.hostUid == currentUserId
    val isJoined = hasActiveRoom && currentUserId != null && currentUserId in activeRoom!!.memberUids
    val canControlRoom = isJoined && (isHost || activeRoom!!.guestsCanControl)

    val rowStepPx = with(LocalDensity.current) { 58.dp.toPx() }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            if (hasActiveRoom) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            onClick = { showSharedTab = false },
                            shape = RoundedCornerShape(10.dp),
                            color = if (!showSharedTab) accentColor else Color.Transparent,
                            modifier = Modifier.weight(1f).testTag("queue_tab_local")
                        ) {
                            Text(
                                text = "Antrean Lokal (${queue.size})",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (!showSharedTab) Color(0xFF0A0B0F) else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        Surface(
                            onClick = { showSharedTab = true },
                            shape = RoundedCornerShape(10.dp),
                            color = if (showSharedTab) accentColor else Color.Transparent,
                            modifier = Modifier.weight(1f).testTag("queue_tab_shared")
                        ) {
                            Text(
                                text = "Antrian Bersama (${sharedQueue.size})",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (showSharedTab) Color(0xFF0A0B0F) else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            if (hasActiveRoom && showSharedTab) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE • ${activeRoom!!.roomCode}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    if (isJoined && onOpenAddSharedQueue != null) {
                        Surface(
                            onClick = onOpenAddSharedQueue,
                            shape = RoundedCornerShape(50),
                            color = accentColor.copy(alpha = 0.18f),
                            modifier = Modifier.testTag("player_add_shared_queue_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Tambah Lagu",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = accentColor
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (sharedQueue.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Antrian bersama masih kosong.\nKetuk \"Tambah Lagu\" untuk menambahkan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(sharedQueue, key = { idx, item -> "sq_${idx}_${item.trackId}" }) { idx, qTrack ->
                            val isUpvoted = currentUserId != null && currentUserId in qTrack.upvoterUids
                            val canRemove = canControlRoom || (isJoined && qTrack.addedByUid == currentUserId)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .clickable(enabled = canControlRoom && onPlaySharedQueue != null) {
                                        onPlaySharedQueue?.invoke(idx)
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AlbumArtImage(
                                    url = qTrack.artworkUrl,
                                    contentDescription = qTrack.title,
                                    modifier = Modifier.size(42.dp),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = qTrack.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${qTrack.artist} • ${qTrack.addedByName.ifBlank { "Peserta" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isJoined && onUpvoteSharedQueue != null) {
                                    Surface(
                                        onClick = { onUpvoteSharedQueue(idx) },
                                        shape = RoundedCornerShape(50),
                                        color = if (isUpvoted) accentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.LocalFireDepartment,
                                                contentDescription = "Vote",
                                                tint = if (isUpvoted) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "${qTrack.upvoterUids.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isUpvoted) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                if (isJoined && onMoveSharedQueue != null && idx > 0) {
                                    IconButton(onClick = { onMoveSharedQueue(idx, idx - 1) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Naik", modifier = Modifier.size(15.dp))
                                    }
                                }
                                if (canRemove && onRemoveSharedQueue != null) {
                                    IconButton(onClick = { onRemoveSharedQueue(idx) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Hapus", modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Local Queue with Drag-to-Reorder handle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Antrean (${queue.size}) • Tahan & seret",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onSaveQueue, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Save, contentDescription = "Simpan antrean ke playlist", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onClearUpcoming, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Bersihkan antrean", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Metrolist Autoplay Radio Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Putar Otomatis Radio Serupa",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(checked = autoplayRadio, onCheckedChange = onToggleAutoplay)
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(queue, key = { idx, t -> "q_${idx}_${t.id}" }) { idx, item ->
                        val isCurrent = idx == currentIndex
                        var dragAccumulatedY by remember(item.id, idx) { mutableFloatStateOf(0f) }
                        var isBeingDragged by remember(item.id, idx) { mutableStateOf(false) }
                        val latestIdx by rememberUpdatedState(idx)
                        val latestLastIdx by rememberUpdatedState(queue.lastIndex)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    translationY = if (isBeingDragged) dragAccumulatedY.coerceIn(-48f, 48f) else 0f
                                }
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    when {
                                        isBeingDragged -> accentColor.copy(alpha = 0.22f)
                                        isCurrent -> accentColor.copy(alpha = 0.14f)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { onSelect(item) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isCurrent) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .pointerInput(item.id) {
                                            detectVerticalDragGestures(
                                                onDragStart = {
                                                    isBeingDragged = true
                                                    dragAccumulatedY = 0f
                                                },
                                                onVerticalDrag = { _, dragAmount ->
                                                    dragAccumulatedY += dragAmount
                                                    if (dragAccumulatedY <= -rowStepPx && latestIdx > 0) {
                                                        onMoveItem(latestIdx, latestIdx - 1)
                                                        dragAccumulatedY += rowStepPx
                                                    } else if (dragAccumulatedY >= rowStepPx && latestIdx < latestLastIdx) {
                                                        onMoveItem(latestIdx, latestIdx + 1)
                                                        dragAccumulatedY -= rowStepPx
                                                    }
                                                },
                                                onDragEnd = {
                                                    isBeingDragged = false
                                                    dragAccumulatedY = 0f
                                                },
                                                onDragCancel = {
                                                    isBeingDragged = false
                                                    dragAccumulatedY = 0f
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DragHandle,
                                        contentDescription = "Seret untuk ubah urutan",
                                        tint = if (isBeingDragged) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            AlbumArtImage(
                                url = item.artworkUrl,
                                contentDescription = item.title,
                                modifier = Modifier.size(42.dp),
                                shape = RoundedCornerShape(10.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isCurrent) accentColor else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isCurrent) {
                                AudioVisualizerBars(isPlaying = isPlaying, color = accentColor, maxHeight = 14.dp)
                            } else {
                                if (idx > 0) {
                                    IconButton(onClick = { onMoveItem(idx, idx - 1) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Naik", modifier = Modifier.size(15.dp))
                                    }
                                }
                                if (idx < queue.lastIndex) {
                                    IconButton(onClick = { onMoveItem(idx, idx + 1) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "Turun", modifier = Modifier.size(15.dp))
                                    }
                                }
                                IconButton(onClick = { onRemove(idx) }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Hapus dari antrean",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UtilityChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (active) accentColor.copy(alpha = 0.18f) else Color.Transparent,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
