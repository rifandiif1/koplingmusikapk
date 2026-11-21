package com.example.ui.player

import android.view.TextureView
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
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
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.listentogether.ListenRoom
import com.example.model.AudioSettings
import com.example.model.EqPreset
import com.example.model.RepeatMode as KoplingfyRepeatMode
import com.example.model.Track
import com.example.model.VideoCandidate
import com.example.ui.components.AlbumArtImage
import com.example.ui.components.WaveformSeekbar
import com.example.ui.components.formatDuration
import com.example.viewmodel.PlayerUiState

/**
 * Apple Music / Tablet style Dual-Column Landscape player layout.
 * Left pane: Album art (or Video) + track title + WaveformSeekbar + playback controls + utilities.
 * Right pane: Generous full-height Synced Lyrics (with ID translation, Romanization & Bedah Makna)
 * or Queue switcher.
 */
@Composable
internal fun DualColumnLandscapePlayer(
    track: Track,
    playerState: PlayerUiState,
    settings: AudioSettings,
    availableHeight: Dp,
    isLiked: Boolean,
    likeScale: Float,
    isDownloaded: Boolean,
    downloadProgress: Float?,
    accentPrimary: Color,
    accentGlow: Color,
    activeRoomCode: String?,
    activeRoom: ListenRoom?,
    currentUserId: String?,
    unreadRoomChatCount: Int,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleLike: () -> Unit,
    onDownloadClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onTrackOptions: () -> Unit,
    onStartRadio: () -> Unit,
    onShareTrack: () -> Unit,
    onShareLyrics: (String) -> Unit,
    onOpenYouTubeLogin: () -> Unit,
    onOpenListenTogether: () -> Unit,
    onOpenRoomChatPopup: (() -> Unit)?,
    onOpenSleepSheet: () -> Unit,
    onOpenAudioFxSheet: () -> Unit,
    onOpenSharedQueueSearch: (() -> Unit)?,
    onToggleDrivingMode: (Boolean) -> Unit,
    onSetVideoMode: (Boolean) -> Unit,
    onSelectVideoCandidate: (VideoCandidate) -> Unit,
    onAttachVideoTextureView: (TextureView?) -> Unit,
    onClearVideoTextureView: (TextureView?) -> Unit,
    onEnterPipMode: () -> Unit,
    onEnterVideoFullscreen: () -> Unit,
    onLyricsFontScaleChange: (Float) -> Unit,
    onNudgeLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onToggleLyricsTranslation: () -> Unit,
    onToggleLyricsRomanization: () -> Unit,
    onOpenSongMeaning: () -> Unit,
    onClearAbLoop: () -> Unit,
    onToggleAutoplay: (Boolean) -> Unit,
    onSelectQueueItem: (Track) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onClearUpcomingQueue: () -> Unit,
    onSaveQueueAsPlaylist: () -> Unit,
    onUpvoteSharedQueueItem: ((Int) -> Unit)?,
    onMoveSharedQueueItem: ((Int, Int) -> Unit)?,
    onRemoveSharedQueueItem: ((Int) -> Unit)?,
    onPlaySharedQueueItem: ((Int) -> Unit)?,
    onShowSnackbar: (String) -> Unit
) {
    // Right column defaults to Lyrics (0 = Lyrics, 1 = Queue)
    var rightTab by rememberSaveable { mutableIntStateOf(0) }
    val isCompactHeight = availableHeight < 480.dp

    Row(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag("dual_column_landscape_player"),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ---- LEFT COLUMN: Artwork / Video + Track Info + Waveform + Transport ----
        Column(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Compact Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(44.dp).testTag("player_collapse_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Tutup pemutar",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(28.dp)
                    )
                }

                if (!track.isLocal) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier.padding(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val songSelected = !playerState.isVideoMode
                            Surface(
                                onClick = { onSetVideoMode(false) },
                                shape = RoundedCornerShape(50),
                                color = if (songSelected) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                                modifier = Modifier.testTag("player_mode_song")
                            ) {
                                Text(
                                    text = "Lagu",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (songSelected) FontWeight.SemiBold else FontWeight.Medium
                                    ),
                                    color = if (songSelected) Color.White else Color.White.copy(alpha = 0.62f),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                onClick = { onSetVideoMode(true) },
                                shape = RoundedCornerShape(50),
                                color = if (playerState.isVideoMode) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                                modifier = Modifier.testTag("player_mode_video")
                            ) {
                                Text(
                                    text = "Video",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (playerState.isVideoMode) FontWeight.SemiBold else FontWeight.Medium
                                    ),
                                    color = if (playerState.isVideoMode) Color.White else Color.White.copy(alpha = 0.62f),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onOpenRoomChatPopup != null) {
                        IconButton(
                            onClick = onOpenRoomChatPopup,
                            modifier = Modifier.size(40.dp).testTag("player_room_chat_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadRoomChatCount > 0) {
                                        Badge(containerColor = accentPrimary) {
                                            Text(
                                                text = unreadRoomChatCount.coerceAtMost(9).toString(),
                                                color = Color(0xFF0A0A0C)
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ChatBubble,
                                    contentDescription = "Buka obrolan room",
                                    tint = accentPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { onToggleDrivingMode(true) },
                        modifier = Modifier.size(38.dp).testTag("player_driving_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DirectionsCar,
                            contentDescription = "Mode Berkendara",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenListenTogether,
                        modifier = Modifier.size(38.dp).testTag("player_listen_together_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = "Play Bersama",
                            tint = if (activeRoomCode != null) accentPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onTrackOptions,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = "Opsi lainnya",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Artwork / Video + Title Section (adapts cleanly to compact phone landscape vs tablet)
            if (isCompactHeight && !playerState.isVideoMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AlbumArtImage(
                        url = track.artworkUrl,
                        contentDescription = stringResource(R.string.cd_album_art),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(112.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = onToggleLike,
                                modifier = Modifier
                                    .size(40.dp)
                                    .scale(likeScale)
                                    .testTag("player_like_button")
                            ) {
                                Icon(
                                    imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = stringResource(R.string.cd_like_track),
                                    tint = if (isLiked) accentPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(
                                onClick = onDownloadClick,
                                modifier = Modifier.size(40.dp).testTag("player_download_button")
                            ) {
                                when {
                                    downloadProgress != null -> CircularProgressIndicator(
                                        progress = { downloadProgress },
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = accentPrimary
                                    )
                                    isDownloaded -> Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Tersimpan offline",
                                        tint = accentPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    else -> Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = "Unduh offline",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = onAddToPlaylist,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                    contentDescription = "Simpan ke playlist",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // Tall Tablet Landscape or Video Mode
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (playerState.isVideoMode) {
                        PlayerVideoStage(
                            playerState = playerState,
                            onSelectCandidate = onSelectVideoCandidate,
                            onRetryVideo = { onSetVideoMode(true) },
                            onBackToAudio = { onSetVideoMode(false) },
                            onAttachTextureView = onAttachVideoTextureView,
                            onClearTextureView = onClearVideoTextureView,
                            onEnterPipMode = onEnterPipMode,
                            onEnterFullscreen = onEnterVideoFullscreen
                        )
                    } else {
                        PlayerArtworkStage(
                            track = track,
                            isPlaying = playerState.isPlaying,
                            artStyle = settings.playerArtStyle,
                            accentColor = accentPrimary,
                            onSwipeNext = onNext,
                            onSwipePrev = onPrevious
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDownloadClick,
                        modifier = Modifier.size(42.dp).testTag("player_download_button")
                    ) {
                        when {
                            downloadProgress != null -> CircularProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = accentPrimary
                            )
                            isDownloaded -> Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Tersimpan offline",
                                tint = accentPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            else -> Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = "Unduh offline",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(42.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = "Simpan ke playlist",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onToggleLike,
                        modifier = Modifier
                            .size(42.dp)
                            .scale(likeScale)
                            .testTag("player_like_button")
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = stringResource(R.string.cd_like_track),
                            tint = if (isLiked) accentPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Optional Audio Error Banner
            if (playerState.audioError != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = playerState.audioError,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (playerState.requiresYouTubeLogin) {
                            Button(
                                onClick = onOpenYouTubeLogin,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("player_login_ytm_button")
                            ) {
                                Icon(Icons.Rounded.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Login", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Waveform Seekbar + Timestamps
            val duration = playerState.durationMs.coerceAtLeast(1L)
            var isDragging by remember { mutableStateOf(false) }
            var dragFraction by remember { mutableFloatStateOf(0f) }
            val currentFraction = (playerState.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

            WaveformSeekbar(
                trackId = track.id,
                progress = currentFraction,
                isPlaying = playerState.isPlaying,
                activeColor = accentPrimary,
                abLoopStartFraction = playerState.abLoopStartMs?.let { (it.toFloat() / duration.toFloat()).coerceIn(0f, 1f) },
                abLoopEndFraction = playerState.abLoopEndMs?.let { (it.toFloat() / duration.toFloat()).coerceIn(0f, 1f) },
                onSeekProgress = { fraction -> onSeek((fraction * duration).toLong()) },
                onDragStateChange = { dragging, fraction ->
                    isDragging = dragging
                    dragFraction = fraction
                },
                modifier = Modifier.height(if (isCompactHeight) 40.dp else 48.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(if (isDragging) (dragFraction * duration).toLong() else playerState.positionMs),
                    style = MaterialTheme.typography.labelSmall,
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
                            text = "A-B ${formatDuration(playerState.abLoopStartMs)}→$bLabel ✕",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 8.dp))

            // Transport Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.size(44.dp).testTag("player_shuffle_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Acak",
                        tint = if (playerState.isShuffleEnabled) accentPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(48.dp).testTag("player_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.cd_previous_track),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Surface(
                    onClick = onTogglePlayPause,
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .size(if (isCompactHeight) 54.dp else 64.dp)
                        .testTag("player_play_pause_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (playerState.isBuffering) {
                            CircularProgressIndicator(
                                color = Color(0xFF0A0A0C),
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.6.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.cd_play_pause),
                                tint = Color(0xFF0A0A0C),
                                modifier = Modifier.size(if (isCompactHeight) 30.dp else 34.dp)
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(48.dp).testTag("player_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.cd_next_track),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(
                    onClick = onCycleRepeat,
                    modifier = Modifier.size(44.dp).testTag("player_repeat_button")
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

            Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 10.dp))

            // Compact Utility Row (Radio, Timer, EQ, Share)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.05f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
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
                        onClick = onOpenSleepSheet,
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
                        onClick = onOpenAudioFxSheet,
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

        // ---- RIGHT COLUMN: Full-Height Synced Lyrics + Translation OR Queue ----
        Column(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Segmented Switcher (Lirik | Antrean)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val lyricsSelected = rightTab == 0
                        Surface(
                            onClick = { rightTab = 0 },
                            shape = RoundedCornerShape(50),
                            color = if (lyricsSelected) Color.White.copy(alpha = 0.20f) else Color.Transparent,
                            modifier = Modifier.testTag("landscape_tab_lyrics")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lyrics,
                                    contentDescription = null,
                                    tint = if (lyricsSelected) Color.White else Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Lirik",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (lyricsSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (lyricsSelected) Color.White else Color.White.copy(alpha = 0.65f)
                                )
                            }
                        }

                        val queueSelected = rightTab == 1
                        Surface(
                            onClick = { rightTab = 1 },
                            shape = RoundedCornerShape(50),
                            color = if (queueSelected) Color.White.copy(alpha = 0.20f) else Color.Transparent,
                            modifier = Modifier.testTag("landscape_tab_queue")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = null,
                                    tint = if (queueSelected) accentPrimary else Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (activeRoomCode != null) "Antrian Room" else "Antrean",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (queueSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (queueSelected) Color.White else Color.White.copy(alpha = 0.65f)
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AnimatedContent(
                    targetState = rightTab,
                    label = "landscape_right_pane"
                ) { pane ->
                    if (pane == 0) {
                        SyncedLyricsStage(
                            track = track,
                            playerState = playerState,
                            fontScale = settings.lyricsFontScale,
                            accentPrimary = accentPrimary,
                            accentGlow = accentGlow,
                            isImmersive = false,
                            onToggleImmersive = {},
                            onFontScaleChange = onLyricsFontScaleChange,
                            onNudgeOffset = onNudgeLyricsOffset,
                            onResetOffset = onResetLyricsOffset,
                            onToggleTranslation = onToggleLyricsTranslation,
                            onToggleRomanization = onToggleLyricsRomanization,
                            onOpenSongMeaning = onOpenSongMeaning,
                            onShareLyrics = onShareLyrics,
                            onShowSnackbar = onShowSnackbar,
                            onSeekTo = onSeek
                        )
                    } else {
                        QueueStage(
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
                            onOpenAddSharedQueue = onOpenSharedQueueSearch,
                            onUpvoteSharedQueue = onUpvoteSharedQueueItem,
                            onMoveSharedQueue = onMoveSharedQueueItem,
                            onRemoveSharedQueue = onRemoveSharedQueueItem,
                            onPlaySharedQueue = onPlaySharedQueueItem
                        )
                    }
                }
            }
        }
    }
}
