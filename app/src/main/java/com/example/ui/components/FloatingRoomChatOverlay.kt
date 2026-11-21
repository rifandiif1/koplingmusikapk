package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.listentogether.ListenRoom
import com.example.listentogether.RoomChatMessage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val quickChatReactions = listOf("🔥", "❤️", "🎵", "👏", "🤘")

private data class FloatingChatToastItem(
    val key: String,
    val message: RoomChatMessage
)

/**
 * Floating live chat overlay that displays incoming Listen Together room messages
 * as sleek clickable toast pills anywhere in the app (just like floating emoji reactions).
 * Tapping any floating message immediately opens the interactive Quick Chat popup.
 */
@Composable
fun FloatingRoomChatOverlay(
    room: ListenRoom?,
    currentUserId: String?,
    isChatPopupOpen: Boolean,
    onOpenQuickChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeRoom = room?.takeIf { it.status == "active" }
    val roomId = activeRoom?.roomCode.orEmpty()
    val parsedMessages = remember(activeRoom?.chatMessages) {
        activeRoom?.parsedChatMessages().orEmpty()
    }

    val floatingItems = remember(roomId) { mutableStateListOf<FloatingChatToastItem>() }
    val seenIds = remember(roomId) { mutableSetOf<String>() }
    var initializedForRoom by remember(roomId) { mutableStateOf(false) }

    LaunchedEffect(roomId, parsedMessages, isChatPopupOpen) {
        if (roomId.isBlank()) {
            floatingItems.clear()
            return@LaunchedEffect
        }
        if (!initializedForRoom) {
            // Seed existing history so joining a room doesn't flood old messages
            parsedMessages.forEach { seenIds.add(it.id) }
            initializedForRoom = true
            return@LaunchedEffect
        }

        val incoming = parsedMessages.filter { it.id !in seenIds }
        if (incoming.isNotEmpty()) {
            incoming.forEach { seenIds.add(it.id) }
            if (!isChatPopupOpen) {
                incoming.takeLast(3).forEach { msg ->
                    // Show messages from friends, and also own message if sent elsewhere
                    val itemKey = "${msg.id}_${System.nanoTime()}"
                    floatingItems.add(FloatingChatToastItem(key = itemKey, message = msg))
                    if (floatingItems.size > 3) {
                        floatingItems.removeAt(0)
                    }
                }
            }
        }
    }

    // Clear floating pills once user opens the chat popup
    LaunchedEffect(isChatPopupOpen) {
        if (isChatPopupOpen && floatingItems.isNotEmpty()) {
            floatingItems.clear()
        }
    }

    if (floatingItems.isEmpty() || activeRoom == null) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 58.dp, start = 14.dp, end = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        floatingItems.toList().forEach { item ->
            FloatingChatBubbleToast(
                item = item,
                isFromMe = item.message.uid == currentUserId,
                onClick = {
                    floatingItems.removeAll { it.key == item.key }
                    onOpenQuickChat()
                },
                onExpired = {
                    floatingItems.removeAll { it.key == item.key }
                }
            )
        }
    }
}

@Composable
private fun FloatingChatBubbleToast(
    item: FloatingChatToastItem,
    isFromMe: Boolean,
    onClick: () -> Unit,
    onExpired: () -> Unit
) {
    var visible by remember(item.key) { mutableStateOf(false) }

    LaunchedEffect(item.key) {
        visible = true
        delay(4_600L)
        visible = false
        delay(280L)
        onExpired()
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
        ) { -it } + fadeIn(),
        exit = slideOutVertically { -it / 2 } + fadeOut()
    ) {
        val accent = MaterialTheme.colorScheme.primary
        val initials = item.message.senderName
            .trim()
            .take(1)
            .uppercase(Locale.getDefault())
            .ifBlank { "?" }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xEE14161D),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
            shadowElevation = 10.dp,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .testTag("floating_room_chat_toast")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isFromMe) accent else accent.copy(alpha = 0.24f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (isFromMe) Color(0xFF0A0A0C) else Color.White
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isFromMe) "Kamu" else item.message.senderName,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "• Ketuk untuk balas",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f),
                            maxLines = 1
                        )
                    }
                    Text(
                        text = item.message.text,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = Color.White.copy(alpha = 0.94f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.ChatBubble,
                    contentDescription = "Buka obrolan room",
                    tint = accent.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Interactive popup bottom sheet for chatting and sending emoji reactions in the active
 * Listen Together room without leaving the player or browsing screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomQuickChatBottomSheet(
    room: ListenRoom,
    currentUserId: String?,
    onSendMessage: (String) -> Unit,
    onSendReaction: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val messages = remember(room.chatMessages) { room.parsedChatMessages() }
    val listState = rememberLazyListState()
    var draft by rememberSaveable(room.roomCode) { mutableStateOf("") }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val accent = MaterialTheme.colorScheme.primary

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(messages.lastIndex) }
        }
    }

    val sendCurrentDraft = {
        val trimmed = draft.trim()
        if (trimmed.isNotEmpty()) {
            onSendMessage(trimmed)
            draft = ""
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF11131A),
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.24f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Forum,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Obrolan Room ${room.roomCode}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "${room.memberUids.size} pendengar aktif • ${messages.size} pesan",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("room_quick_chat_close")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Tutup popup obrolan",
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Floating Emoji Reaction Bar
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(quickChatReactions) { emoji ->
                    Surface(
                        onClick = { onSendReaction(emoji) },
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                        modifier = Modifier.testTag("quick_chat_reaction_$emoji")
                    ) {
                        Text(
                            text = emoji,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Live Chat List
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF181B24),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 320.dp)
            ) {
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada obrolan di room ini.\nKirim pesan pertama — pesanmu akan melayang di layar semua teman!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.62f)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            val isMine = msg.uid == currentUserId
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isMine) 16.dp else 4.dp,
                                        bottomEnd = if (isMine) 4.dp else 16.dp
                                    ),
                                    color = if (isMine) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isMine) accent.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.08f)
                                    ),
                                    modifier = Modifier.widthIn(max = 280.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isMine) "Kamu" else msg.senderName,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (isMine) accent else Color.White.copy(alpha = 0.78f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (msg.sentAtMs > 0L) {
                                                Text(
                                                    text = timeFormat.format(Date(msg.sentAtMs)),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = Color.White.copy(alpha = 0.45f)
                                                )
                                            }
                                        }
                                        Text(
                                            text = msg.text,
                                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                                            color = Color.White.copy(alpha = 0.94f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Composer Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(240) },
                    placeholder = {
                        Text(
                            text = "Tulis pesan ke room…",
                            color = Color.White.copy(alpha = 0.48f)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendCurrentDraft() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                        focusedContainerColor = Color(0xFF181B24),
                        unfocusedContainerColor = Color(0xFF181B24)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("room_quick_chat_input")
                )

                Surface(
                    onClick = sendCurrentDraft,
                    enabled = draft.isNotBlank(),
                    shape = CircleShape,
                    color = if (draft.isNotBlank()) accent else Color.White.copy(alpha = 0.10f),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("room_quick_chat_send")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Kirim pesan",
                            tint = if (draft.isNotBlank()) Color(0xFF0A0A0C) else Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
