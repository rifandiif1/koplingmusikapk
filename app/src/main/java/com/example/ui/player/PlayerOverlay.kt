package com.example.ui.player

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AudioSettings
import com.example.model.EqPreset
import com.example.model.PlayerArtStyle
import com.example.model.PlayerBackgroundStyle
import com.example.model.RepeatMode as KoplingfyRepeatMode
import com.example.model.Track
import com.example.ui.components.AlbumArtImage
import com.example.ui.components.AudioVisualizerBars
import com.example.ui.components.formatDuration
import com.example.viewmodel.PlayerUiState

@Composable
fun FloatingMiniPlayer(
    playerState: PlayerUiState,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playerState.currentTrack ?: return
    val progress = if (playerState.durationMs > 0L) {
        (playerState.positionMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        onClick = onExpand,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .testTag("mini_player_bar"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        shadowElevation = 16.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(46.dp)) {
                    AlbumArtImage(
                        url = track.artworkUrl,
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (playerState.isPlaying) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xD90A0B0F))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            AudioVisualizerBars(
                                isPlaying = true,
                                color = MaterialTheme.colorScheme.primary,
                                barWidth = 2.dp,
                                maxHeight = 10.dp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (playerState.isBuffering) "Memuat audio penuh…" else track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (playerState.isBuffering) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.cd_play_pause),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(40.dp).testTag("mini_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.cd_next_track),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant
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
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit
) {
    BackHandler(onBack = onCollapse)

    val track = playerState.currentTrack ?: return
    // 0 = Artwork, 1 = Lyrics, 2 = Queue
    var activeTab by remember { mutableIntStateOf(0) }
    var showSleepSheet by remember { mutableStateOf(false) }
    var showAudioFxSheet by remember { mutableStateOf(false) }

    // Keep screen on while reading lyrics (Metrolist feature)
    val context = LocalContext.current
    DisposableEffect(activeTab, settings.keepScreenOnLyrics) {
        val window = (context as? android.app.Activity)?.window
        if (activeTab == 1 && settings.keepScreenOnLyrics) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val bg = MaterialTheme.colorScheme.background

    val likeScale by animateFloatAsState(
        targetValue = if (isLiked) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "like_scale"
    )

    // CRITICAL FIX: 100% OPAQUE solid base background + touch blocker so menu behind NEVER bleeds through!
    Box(
        modifier = Modifier
            .fillMaxSize()
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
                                    bg.copy(alpha = 0.72f),
                                    bg.copy(alpha = 0.88f),
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
                                    primary.copy(alpha = 0.24f),
                                    secondary.copy(alpha = 0.10f),
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
            // ---- Top Bar ----
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

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = playerState.streamSourceLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.album,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenListenTogether,
                        modifier = Modifier.size(44.dp).testTag("player_listen_together_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = "Play Bersama",
                            tint = if (activeRoomCode != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(
                        onClick = onTrackOptions,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = "Opsi lainnya",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ---- Center Stage: Artwork (swipeable) / Lyrics / Queue ----
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(targetState = activeTab, label = "player_stage") { tab ->
                    when (tab) {
                        0 -> PlayerArtworkStage(
                            track = track,
                            isPlaying = playerState.isPlaying,
                            artStyle = settings.playerArtStyle,
                            onSwipeNext = onNext,
                            onSwipePrev = onPrevious
                        )
                        1 -> SyncedLyricsStage(
                            playerState = playerState,
                            fontScale = settings.lyricsFontScale,
                            onFontScaleChange = onLyricsFontScaleChange,
                            onShareLyrics = onShareLyrics,
                            onSeekTo = onSeek
                        )
                        else -> QueueStage(
                            queue = playerState.queue,
                            currentIndex = playerState.currentIndex,
                            isPlaying = playerState.isPlaying,
                            autoplayRadio = settings.autoplayRadio,
                            onToggleAutoplay = onToggleAutoplay,
                            onSelect = onSelectQueueItem,
                            onMoveItem = onMoveQueueItem,
                            onRemove = onRemoveQueueItem,
                            onClearUpcoming = onClearUpcomingQueue,
                            onSaveQueue = onSaveQueueAsPlaylist
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
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        isDownloaded -> {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Tersimpan offline",
                                tint = MaterialTheme.colorScheme.primary,
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
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth().testTag("player_seek_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(if (isDragging) (dragFraction * duration).toLong() else playerState.positionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ---- Expressive Playback Controls ----
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
                        tint = if (playerState.isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(54.dp).testTag("player_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.cd_previous_track),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Surface(
                    onClick = onTogglePlayPause,
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 12.dp,
                    modifier = Modifier.size(72.dp).testTag("player_play_pause_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (playerState.isBuffering) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.cd_play_pause),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(38.dp)
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
                        tint = MaterialTheme.colorScheme.onBackground,
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
                            MaterialTheme.colorScheme.primary
                        } else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ---- Bottom Utility Pill Bar (Lyrics, Queue, Radio, Timer, Audio FX, Share) ----
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    UtilityChip(
                        icon = Icons.Rounded.Lyrics,
                        label = "Lirik",
                        active = activeTab == 1,
                        onClick = { activeTab = if (activeTab == 1) 0 else 1 },
                        modifier = Modifier.testTag("player_tab_lyrics")
                    )
                    UtilityChip(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        label = "Antrean",
                        active = activeTab == 2,
                        onClick = { activeTab = if (activeTab == 2) 0 else 2 },
                        modifier = Modifier.testTag("player_tab_queue")
                    )
                    UtilityChip(
                        icon = Icons.Rounded.Radio,
                        label = if (playerState.isLoadingRadio) "…" else "Radio",
                        active = playerState.isLoadingRadio,
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
                        onClick = { showSleepSheet = true },
                        modifier = Modifier.testTag("player_sleep_timer_button")
                    )
                    UtilityChip(
                        icon = Icons.Rounded.Equalizer,
                        label = "EQ",
                        active = settings.eqPreset != EqPreset.FLAT || settings.playbackSpeed != 1.0f || settings.bassBoostStrength > 0.05f,
                        onClick = { showAudioFxSheet = true },
                        modifier = Modifier.testTag("player_audio_fx_button")
                    )
                    UtilityChip(
                        icon = Icons.Rounded.Share,
                        label = "Bagikan",
                        active = false,
                        onClick = onShareTrack,
                        modifier = Modifier.testTag("player_share_button")
                    )
                }
            }
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
            onSpeedChange = onSpeedChange,
            onPitchChange = onPitchChange
        )
    }
}

@Composable
private fun PlayerArtworkStage(
    track: Track,
    isPlaying: Boolean,
    artStyle: PlayerArtStyle,
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
            .fillMaxWidth(0.84f)
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
                color = Color(0xFF0E1016),
                border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                shadowElevation = 24.dp
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
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(30.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)),
                shadowElevation = 22.dp
            ) {
                AlbumArtImage(
                    url = track.artworkUrl,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(30.dp)
                )
            }
        }
    }
}

@Composable
private fun SyncedLyricsStage(
    playerState: PlayerUiState,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    onShareLyrics: (String) -> Unit,
    onSeekTo: (Long) -> Unit
) {
    val lyrics = playerState.lyricsState
    val listState = rememberLazyListState()

    val activeLineIndex = remember(lyrics.syncedLines, playerState.positionMs) {
        lyrics.syncedLines.indexOfLast { it.timestampMs <= playerState.positionMs + 300L }
    }

    LaunchedEffect(activeLineIndex) {
        if (activeLineIndex >= 0) {
            runCatching { listState.animateScrollToItem((activeLineIndex - 1).coerceAtLeast(0)) }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Lyrics toolbar (Provider badge, font scale -/+, Share)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (lyrics.providerName.isNotBlank()) "Lirik • ${lyrics.providerName}" else "Lirik",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onFontScaleChange((fontScale - 0.1f).coerceAtLeast(0.8f)) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Rounded.TextDecrease, contentDescription = "Perkecil huruf", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { onFontScaleChange((fontScale + 0.1f).coerceAtMost(1.4f)) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Rounded.TextIncrease, contentDescription = "Perbesar huruf", modifier = Modifier.size(18.dp))
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
                            Icon(Icons.Rounded.Share, contentDescription = "Bagikan lirik", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    lyrics.isLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(10.dp))
                                Text("Mencari lirik LRCLIB & YouTube Music…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    lyrics.isInstrumental -> {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "♪ Lagu Instrumental ♪\nNikmati alunan musiknya.",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    lyrics.syncedLines.isNotEmpty() -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(lyrics.syncedLines) { idx, line ->
                                val isActive = idx == activeLineIndex
                                Text(
                                    text = line.text,
                                    style = if (isActive) {
                                        MaterialTheme.typography.headlineSmall.copy(
                                            fontSize = (21f * fontScale).sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontSize = (16f * fontScale).sp
                                        )
                                    },
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSeekTo(line.timestampMs) }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                    !lyrics.plainLyrics.isNullOrBlank() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = lyrics.plainLyrics,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = (16f * fontScale).sp,
                                    lineHeight = (26f * fontScale).sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    else -> {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = lyrics.error ?: "Lirik belum tersedia untuk lagu ini.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
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
    onToggleAutoplay: (Boolean) -> Unit,
    onSelect: (Track) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClearUpcoming: () -> Unit,
    onSaveQueue: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Antrean (${queue.size})",
                    style = MaterialTheme.typography.titleMedium,
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { onSelect(item) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
                            AudioVisualizerBars(isPlaying = isPlaying, maxHeight = 14.dp)
                        } else {
                            if (idx > 0) {
                                IconButton(onClick = { onMoveItem(idx, idx - 1) }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Naik", modifier = Modifier.size(16.dp))
                                }
                            }
                            if (idx < queue.lastIndex) {
                                IconButton(onClick = { onMoveItem(idx, idx + 1) }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "Turun", modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(onClick = { onRemove(idx) }, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Hapus dari antrean",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
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
private fun UtilityChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
