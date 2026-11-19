package com.example.ui.player

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
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)),
        shadowElevation = 14.dp
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
                                .background(Color(0xCC0A0B0F))
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
                        text = if (playerState.isBuffering) "Memuat audio…" else track.artist,
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
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onTrackOptions: () -> Unit,
    onSelectQueueItem: (Track) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onSleepMinutes: (Int) -> Unit,
    onSleepEndOfTrack: () -> Unit,
    onEqPreset: (EqPreset) -> Unit,
    onCustomBand: (Int, Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit
) {
    BackHandler(onBack = onCollapse)

    val track = playerState.currentTrack ?: return
    // 0 = Artwork, 1 = Lyrics, 2 = Queue
    var activeTab by remember { mutableIntStateOf(0) }
    var showSleepSheet by remember { mutableStateOf(false) }
    var showAudioFxSheet by remember { mutableStateOf(false) }

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val bg = MaterialTheme.colorScheme.background

    val likeScale by animateFloatAsState(
        targetValue = if (isLiked) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "like_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        primary.copy(alpha = 0.28f),
                        secondary.copy(alpha = 0.12f),
                        bg,
                        bg
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.Center)
                .padding(horizontal = 24.dp, vertical = 12.dp),
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
                    Text(
                        text = if (track.isLocal) "KOPLINGFY LOKAL AUDIO" else "KOPLINGFY HI-FI",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = track.album,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp)
                    )
                }

                IconButton(
                    onClick = onTrackOptions,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = "Opsi lainnya",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ---- Center Stage: Artwork / Lyrics / Queue ----
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
                            artStyle = settings.playerArtStyle
                        )
                        1 -> SyncedLyricsStage(
                            playerState = playerState,
                            onSeekTo = onSeek
                        )
                        else -> QueueStage(
                            queue = playerState.queue,
                            currentIndex = playerState.currentIndex,
                            isPlaying = playerState.isPlaying,
                            onSelect = onSelectQueueItem,
                            onRemove = onRemoveQueueItem
                        )
                    }
                }
            }

            // ---- Error notice if stream failed ----
            if (playerState.audioError != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = playerState.audioError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // ---- Track Title & Quick Like/Playlist Actions ----
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

                IconButton(
                    onClick = onAddToPlaylist,
                    modifier = Modifier.size(48.dp)
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
                        .size(48.dp)
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

            Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(10.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Bottom Utility Pill Bar (Lyrics, Queue, Sleep Timer, Audio FX) ----
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
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
                        label = "Audio FX",
                        active = settings.eqPreset != EqPreset.FLAT || settings.playbackSpeed != 1.0f,
                        onClick = { showAudioFxSheet = true },
                        modifier = Modifier.testTag("player_audio_fx_button")
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
            onSpeedChange = onSpeedChange,
            onPitchChange = onPitchChange
        )
    }
}

@Composable
private fun PlayerArtworkStage(
    track: Track,
    isPlaying: Boolean,
    artStyle: PlayerArtStyle
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

    Box(
        modifier = Modifier
            .fillMaxWidth(0.84f)
            .aspectRatio(1f)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        if (artStyle == PlayerArtStyle.VINYL) {
            // Spinning luxury vinyl disc
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
                        modifier = Modifier
                            .fillMaxSize(0.76f),
                        shape = CircleShape
                    )
                    // Center spindle hole
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
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
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
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        when {
            lyrics.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp))
                        Text("Mencari lirik sinkron…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(lyrics.syncedLines) { idx, line ->
                        val isActive = idx == activeLineIndex
                        Text(
                            text = line.text,
                            style = if (isActive) {
                                MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            } else {
                                MaterialTheme.typography.titleMedium
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
                        .padding(22.dp)
                ) {
                    Text(
                        text = lyrics.plainLyrics,
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
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

@Composable
private fun QueueStage(
    queue: List<Track>,
    currentIndex: Int,
    isPlaying: Boolean,
    onSelect: (Track) -> Unit,
    onRemove: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Text(
                text = "Antrean Putar (${queue.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
            )
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
                            modifier = Modifier.size(44.dp),
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
                            IconButton(onClick = { onRemove(idx) }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Hapus dari antrean",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun UtilityChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerBottomSheet(
    remainingSec: Int?,
    endOfTrack: Boolean,
    onDismiss: () -> Unit,
    onSelectMinutes: (Int) -> Unit,
    onSelectEndOfTrack: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Timer Tidur", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = when {
                    remainingSec != null -> "Aktif: sisa ${remainingSec / 60} menit ${remainingSec % 60} detik"
                    endOfTrack -> "Aktif: berhenti setelah lagu ini selesai"
                    else -> "Otomatis menjeda pemutaran saat kamu beristirahat"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            listOf(5, 15, 30, 45, 60).forEach { mins ->
                Surface(
                    onClick = { onSelectMinutes(mins) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "$mins Menit",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }
            }
            Surface(
                onClick = onSelectEndOfTrack,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Akhir Lagu Ini",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
            if (remainingSec != null || endOfTrack) {
                TextButton(
                    onClick = { onSelectMinutes(0) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Matikan Timer Tidur", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioFxBottomSheet(
    settings: AudioSettings,
    onDismiss: () -> Unit,
    onEqPreset: (EqPreset) -> Unit,
    onCustomBand: (Int, Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit
) {
    val freqLabels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Audio Engine & 5-Band EQ", style = MaterialTheme.typography.headlineSmall)
                    Text("Penyesuaian akustik real-time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = {
                    onEqPreset(EqPreset.FLAT)
                    onSpeedChange(1.0f)
                    onPitchChange(1.0f)
                }) {
                    Text("Reset", color = MaterialTheme.colorScheme.primary)
                }
            }

            // Preset chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EqPreset.entries.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            val selected = settings.eqPreset == preset
                            Surface(
                                onClick = { onEqPreset(preset) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = preset.label.substringBefore(" "),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 5-Band EQ Sliders
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    settings.customBandsDb.forEachIndexed { idx, db ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = freqLabels.getOrElse(idx) { "" },
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(52.dp)
                            )
                            Slider(
                                value = db,
                                onValueChange = { onCustomBand(idx, it) },
                                valueRange = -6f..6f,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "%+.0f dB".format(db),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(46.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }

            // Speed & Pitch controls
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kecepatan Putar (Tempo)", style = MaterialTheme.typography.titleSmall)
                    Text("%.2fx".format(settings.playbackSpeed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = settings.playbackSpeed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.5f..2.0f
                )
            }

            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Nada (Pitch)", style = MaterialTheme.typography.titleSmall)
                    Text("%.2fx".format(settings.playbackPitch), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                }
                Slider(
                    value = settings.playbackPitch,
                    onValueChange = onPitchChange,
                    valueRange = 0.75f..1.5f
                )
            }
        }
    }
}
