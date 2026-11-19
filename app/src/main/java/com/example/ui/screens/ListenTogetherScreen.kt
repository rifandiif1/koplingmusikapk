package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.listentogether.ListenRoom
import com.example.listentogether.ListenSessionUiState
import com.example.listentogether.ListenTogetherViewModel
import com.example.listentogether.ListenUiState
import com.example.listentogether.RecentRoomEntry
import com.example.listentogether.attemptAutoSignIn
import com.example.listentogether.onGoogleSignInClicked
import com.example.listentogether.signOutGoogle
import com.example.model.Track
import com.example.viewmodel.PlayerUiState
import com.google.firebase.auth.FirebaseUser

/**
 * Auth-gated Play Bersama screen.
 * Feature UI and Firestore ViewModels are ONLY composed when currentUser != null.
 */
@Composable
fun ListenTogetherScreenHost(
    currentUser: FirebaseUser?,
    listenViewModel: ListenTogetherViewModel?,
    sessionState: ListenSessionUiState,
    myActiveRoomsState: ListenUiState<List<ListenRoom>>,
    recentRoomsState: ListenUiState<List<RecentRoomEntry>>,
    playerState: PlayerUiState,
    bottomContentPadding: Dp,
    onBack: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    onSyncPlayerTogglePlayPause: () -> Unit,
    onSyncPlayerNext: () -> Unit,
    onForceResyncFromRoom: (ListenRoom) -> Unit,
    onPlayRoomTrackLocally: (Track, List<Track>) -> Unit
) {
    BackHandler(onBack = onBack)

    val bgColor = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        if (currentUser == null || listenViewModel == null) {
            ListenTogetherAuthScreen(
                onBack = onBack,
                onAuthSuccess = { onShowSnackbar("Berhasil masuk dengan Google untuk Play Bersama!") }
            )
        } else {
            key(currentUser.uid) {
                ListenTogetherSignedInScreen(
                    currentUser = currentUser,
                    listenViewModel = listenViewModel,
                    sessionState = sessionState,
                    myActiveRoomsState = myActiveRoomsState,
                    recentRoomsState = recentRoomsState,
                    playerState = playerState,
                    bottomContentPadding = bottomContentPadding,
                    onBack = onBack,
                    onShowSnackbar = onShowSnackbar,
                    onSyncPlayerTogglePlayPause = onSyncPlayerTogglePlayPause,
                    onSyncPlayerNext = onSyncPlayerNext,
                    onForceResyncFromRoom = onForceResyncFromRoom,
                    onPlayRoomTrackLocally = onPlayRoomTrackLocally
                )
            }
        }
    }
}

@Composable
private fun ListenTogetherAuthScreen(
    onBack: () -> Unit,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = { /* Keep visible interactive Google Sign-In button */ },
            scope = scope
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("listen_auth_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Kembali"
                )
            }
            Text(
                text = "Play Bersama",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Dengarkan Musik Bersama\nSecara Real-Time",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Buat ruang dengar, bagikan Kode Ruang atau QR Code seperti QRIS, lalu nikmati lagu & antrean yang tersinkronisasi langsung dengan teman.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeatureHighlightRow(Icons.Rounded.QrCode2, "Gabung cepat lewat Kode 6 Karakter atau Scan QR")
                    FeatureHighlightRow(Icons.Rounded.GraphicEq, "Sinkronisasi lagu, play/pause, & durasi real-time")
                    FeatureHighlightRow(Icons.Rounded.Radio, "Antrean bersama & kontrol izin peserta oleh Host")
                }
            }

            if (!authError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = authError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    authError = null
                    isLoading = true
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { msg ->
                            isLoading = false
                            authError = msg
                        },
                        scope = scope,
                        onAuthCancelled = {
                            isLoading = false
                        }
                    )
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Login,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Masuk dengan Google",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureHighlightRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ListenTogetherSignedInScreen(
    currentUser: FirebaseUser,
    listenViewModel: ListenTogetherViewModel,
    sessionState: ListenSessionUiState,
    myActiveRoomsState: ListenUiState<List<ListenRoom>>,
    recentRoomsState: ListenUiState<List<RecentRoomEntry>>,
    playerState: PlayerUiState,
    bottomContentPadding: Dp,
    onBack: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    onSyncPlayerTogglePlayPause: () -> Unit,
    onSyncPlayerNext: () -> Unit,
    onForceResyncFromRoom: (ListenRoom) -> Unit,
    onPlayRoomTrackLocally: (Track, List<Track>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var showQrScanner by remember { mutableStateOf(false) }

    LaunchedEffect(sessionState.statusMessage, sessionState.errorMessage) {
        val msg = sessionState.errorMessage ?: sessionState.statusMessage
        if (!msg.isNullOrBlank()) {
            onShowSnackbar(msg)
            listenViewModel.clearMessages()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val activeRoom = sessionState.activeRoom
        if (activeRoom != null) {
            ListenTogetherActiveRoomContent(
                room = activeRoom,
                currentUserId = currentUser.uid,
                currentUserName = listenViewModel.currentUserName,
                playerState = playerState,
                isBusy = sessionState.isBusy,
                showQrModal = sessionState.showQrCardModal,
                bottomContentPadding = bottomContentPadding,
                onBack = onBack,
                onShowQrModal = { listenViewModel.setShowQrModal(it) },
                onToggleGuestsControl = { listenViewModel.toggleGuestsCanControl(it) },
                onTogglePlayPause = onSyncPlayerTogglePlayPause,
                onSkipNext = onSyncPlayerNext,
                onForceResync = { onForceResyncFromRoom(activeRoom) },
                onAddCurrentTrackToRoomQueue = { track -> listenViewModel.addTrackToRoomQueue(track) },
                onPlaySharedQueueIndex = { index ->
                    val queue = activeRoom.parsedQueue()
                    val chosen = queue.getOrNull(index)
                    if (chosen != null) {
                        listenViewModel.playRoomQueueTrack(index)
                        onPlayRoomTrackLocally(chosen.toTrack(), queue.map { it.toTrack() })
                    }
                },
                onSendReaction = { emoji -> listenViewModel.sendReaction(emoji) },
                onLeaveOrEnd = { listenViewModel.leaveOrEndCurrentRoom() },
                onShowSnackbar = onShowSnackbar
            )
        } else {
            ListenTogetherLobbyContent(
                userName = listenViewModel.currentUserName,
                userEmail = currentUser.email.orEmpty(),
                isBusy = sessionState.isBusy,
                currentTrack = playerState.currentTrack,
                isPlaying = playerState.isPlaying,
                positionMs = playerState.positionMs,
                currentQueue = playerState.queue,
                myActiveRoomsState = myActiveRoomsState,
                recentRoomsState = recentRoomsState,
                bottomContentPadding = bottomContentPadding,
                onBack = onBack,
                onCreateRoom = { roomName, guestsCanControl ->
                    listenViewModel.createRoom(
                        roomName = roomName,
                        guestsCanControl = guestsCanControl,
                        currentTrack = playerState.currentTrack,
                        isPlaying = playerState.isPlaying,
                        positionMs = playerState.positionMs,
                        currentQueue = playerState.queue
                    )
                },
                onJoinByCode = { code -> listenViewModel.joinRoomByCodeOrQr(code) },
                onOpenQrScanner = { showQrScanner = true },
                onSignOut = {
                    signOutGoogle(
                        credentialManager = credentialManager,
                        scope = scope,
                        onBeforeSignOut = { listenViewModel.detachBeforeSignOut() },
                        onSignOutComplete = { onShowSnackbar("Berhasil keluar dari akun Google Play Bersama") }
                    )
                }
            )
        }

        if (showQrScanner) {
            QrCodeScannerOverlay(
                onQrCodeDetected = { parsedCode ->
                    showQrScanner = false
                    listenViewModel.joinRoomByCodeOrQr(parsedCode)
                },
                onDismiss = { showQrScanner = false }
            )
        }
    }
}

@Composable
private fun ListenTogetherLobbyContent(
    userName: String,
    userEmail: String,
    isBusy: Boolean,
    currentTrack: Track?,
    isPlaying: Boolean,
    positionMs: Long,
    currentQueue: List<Track>,
    myActiveRoomsState: ListenUiState<List<ListenRoom>>,
    recentRoomsState: ListenUiState<List<RecentRoomEntry>>,
    bottomContentPadding: Dp,
    onBack: () -> Unit,
    onCreateRoom: (roomName: String, guestsCanControl: Boolean) -> Unit,
    onJoinByCode: (String) -> Unit,
    onOpenQrScanner: () -> Unit,
    onSignOut: () -> Unit
) {
    var roomNameInput by rememberSaveable { mutableStateOf("Ruang ${userName.substringBefore(" ")}") }
    var guestsCanControl by rememberSaveable { mutableStateOf(true) }
    var joinCodeInput by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 12.dp,
            bottom = bottomContentPadding + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("listen_lobby_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                    Column {
                        Text(
                            text = "Play Bersama",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = userName.ifBlank { userEmail },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onSignOut,
                    modifier = Modifier.testTag("listen_sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = "Keluar Google",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ---- Card 1: Join via Room Code OR QR Code (QRIS-style) ----
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.QrCodeScanner,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Gabung Ruang Teman",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Masukkan Kode 6 karakter atau Scan QR Code",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Big Primary Scan QR Button
                    Button(
                        onClick = onOpenQrScanner,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("scan_qr_room_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Scan QR Code (Kamera / Galeri)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Manual 6-char code input row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = joinCodeInput,
                            onValueChange = { raw ->
                                joinCodeInput = raw.uppercase().filter { it.isLetterOrDigit() }.take(6)
                            },
                            label = { Text("Kode Ruang (6 huruf/angka)") },
                            placeholder = { Text("KPL74M") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = { if (joinCodeInput.length == 6 && !isBusy) onJoinByCode(joinCodeInput) }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("join_room_code_input")
                        )

                        Button(
                            onClick = { onJoinByCode(joinCodeInput) },
                            enabled = joinCodeInput.length == 6 && !isBusy,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("join_room_submit_button")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Gabung", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ---- Card 2: Create New Room ----
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.AddCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Buat Ruang Baru",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (currentTrack != null && !currentTrack.isLocal) {
                                    "Otomatis menyinkronkan \"${currentTrack.title}\""
                                } else {
                                    "Dapatkan Kode Ruang & QR Code instan"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OutlinedTextField(
                        value = roomNameInput,
                        onValueChange = { roomNameInput = it.take(50) },
                        label = { Text("Nama Ruang") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_room_name_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Izinkan peserta ikut kontrol",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Teman bisa tambah lagu & kontrol play/pause",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = guestsCanControl,
                            onCheckedChange = { guestsCanControl = it },
                            modifier = Modifier.testTag("create_room_guests_control_switch")
                        )
                    }

                    OutlinedButton(
                        onClick = { onCreateRoom(roomNameInput, guestsCanControl) },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("create_room_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCode2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Buat Ruang & Tampilkan QR",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // ---- My Active Rooms ----
        val activeRooms = (myActiveRoomsState as? ListenUiState.Success)?.data.orEmpty()
        if (activeRooms.isNotEmpty()) {
            item {
                Text(
                    text = "Ruang Aktif Anda",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(activeRooms, key = { "active_${it.roomCode}" }) { room ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onJoinByCode(room.roomCode) }
                        .testTag("active_room_card_${room.roomCode}")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "LIVE • ${room.roomCode}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Text(
                                    text = "${room.memberUids.size} pendengar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = room.roomName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = room.currentTrackTitle.ifBlank { "Menunggu lagu diputar..." },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Masuk",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // ---- Recent Rooms History ----
        val recents = (recentRoomsState as? ListenUiState.Success)?.data.orEmpty()
        if (recents.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Riwayat Ruang Terakhir",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(recents.take(6), key = { "recent_${it.roomCode}" }) { item ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onJoinByCode(item.roomCode) }
                        .testTag("recent_room_card_${item.roomCode}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.roomName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Kode ${item.roomCode} • Host: ${item.hostName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = item.roomCode,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
