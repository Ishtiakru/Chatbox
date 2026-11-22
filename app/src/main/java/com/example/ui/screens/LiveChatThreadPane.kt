package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Conversation
import com.example.data.model.LiveMessage
import com.example.data.model.UserProfile
import com.example.ui.components.ChatboxNoticeBanner
import com.example.ui.components.MessengerAvatar
import com.example.ui.theme.ChatboxLiveIncomingBubble
import com.example.ui.theme.ChatboxLiveOutgoingBubble
import com.example.ui.viewmodel.LiveThreadState
import java.text.DateFormat
import java.util.Locale

internal fun resolvePeerDisplayName(peerUid: String, profile: UserProfile?): String {
    val name = profile?.displayName?.trim().orEmpty()
    if (name.isNotEmpty()) return name
    return if (peerUid.length > 10) "User ${peerUid.take(6)}…" else peerUid.ifEmpty { "Chatbox user" }
}

@Composable
internal fun LiveThreadPane(
    conversation: Conversation,
    currentUserId: String,
    peerProfile: UserProfile?,
    threadState: LiveThreadState,
    isBlocked: Boolean,
    profileReady: Boolean,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onOpenDetails: () -> Unit,
    onAcceptRequest: () -> Unit,
    onCloseOrDecline: () -> Unit,
    onLoadOlder: () -> Unit,
    onMessageDraftChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onUnblockPeer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val peerUid = conversation.peerUid(currentUserId)
    val peerName = resolvePeerDisplayName(peerUid, peerProfile)
    val busy = threadState.busy || !profileReady
    val composerEnabled = !busy && conversation.status == "active" && !isBlocked
    val displayMessages = threadState.allDisplayMessages
    val listState = rememberLazyListState()

    LaunchedEffect(conversation.id, displayMessages.lastOrNull()?.id) {
        if (displayMessages.isNotEmpty() && !threadState.loadingOlder) {
            listState.animateScrollToItem(displayMessages.lastIndex)
        }
    }

    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("live_back_to_list_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(id = R.string.cd_back)
                    )
                }
            }
            MessengerAvatar(label = peerName, size = 38.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peerName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when (conversation.status) {
                        "active" -> "Direct conversation"
                        "pending" -> "Message request"
                        else -> "Closed conversation"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onOpenDetails,
                modifier = Modifier.testTag("live_details_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(id = R.string.cd_conversation_details)
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (conversation.status != "active") {
            ConversationLifecycleBar(
                conversation = conversation,
                currentUserId = currentUserId,
                busy = busy,
                canInteract = !isBlocked,
                onAccept = onAcceptRequest,
                onCloseOrDecline = onCloseOrDecline,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }

        if (isBlocked) {
            ChatboxNoticeBanner(
                text = "You blocked this account. Unblock to allow new conversation requests.",
                actionLabel = "Unblock",
                onAction = onUnblockPeer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (threadState.messagesFromCache) {
                item(key = "cache_hint") {
                    Text(
                        text = "Messages may be incomplete or out of date while awaiting a server update.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (threadState.hasOlder) {
                item(key = "load_older") {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        OutlinedButton(
                            onClick = onLoadOlder,
                            enabled = !threadState.loadingOlder,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("live_load_older_button")
                        ) {
                            if (threadState.loadingOlder) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Loading…")
                            } else {
                                Text("Load earlier messages")
                            }
                        }
                    }
                }
            }

            if (displayMessages.isEmpty()) {
                item(key = "empty_messages") {
                    Text(
                        text = when {
                            threadState.messagesFromCache -> "Waiting for a server update."
                            conversation.status == "active" -> "No messages yet. Say hello!"
                            else -> "No messages to show."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(displayMessages, key = { it.id }) { message ->
                    LiveMessageBubble(
                        message = message,
                        isOwn = message.senderId == currentUserId
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = threadState.messageDraft,
                onValueChange = onMessageDraftChange,
                enabled = composerEnabled,
                placeholder = { Text("Message") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (composerEnabled) onSendMessage() }),
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("live_message_input")
            )
            val canSend = composerEnabled && threadState.messageDraft.trim().isNotEmpty()
            Surface(
                onClick = onSendMessage,
                enabled = canSend,
                shape = CircleShape,
                color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("live_send_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = stringResource(id = R.string.cd_send),
                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun LiveDetailsPane(
    conversation: Conversation,
    currentUserId: String,
    peerProfile: UserProfile?,
    isBlocked: Boolean,
    busy: Boolean,
    onBackToThread: () -> Unit,
    onAcceptRequest: () -> Unit,
    onCloseOrDecline: () -> Unit,
    onBlockPeer: () -> Unit,
    onUnblockPeer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val peerUid = conversation.peerUid(currentUserId)
    val peerName = resolvePeerDisplayName(peerUid, peerProfile)
    val context = LocalContext.current
    var showBlockConfirm by remember { mutableStateOf(false) }

    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = { Text("Block $peerName?") },
            text = {
                Text(
                    "Blocking stops new messages and closes this conversation. You can unblock later from conversation details."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockConfirm = false
                        onBlockPeer()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("live_confirm_block_button")
                ) {
                    Text("Block")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBackToThread,
                modifier = Modifier.testTag("live_back_to_thread_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(id = R.string.cd_back)
                )
            }
            Text(
                text = "Conversation details",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MessengerAvatar(label = peerName, size = 72.dp)
            Text(
                text = peerName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Display names are not verified",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Chatbox ID",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = peerUid,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = {
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm?.setPrimaryClip(ClipData.newPlainText("Peer Chatbox ID", peerUid))
                    },
                    modifier = Modifier.testTag("live_copy_peer_id_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(id = R.string.cd_copy_chatbox_id),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Conversation",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            ConversationLifecycleBar(
                conversation = conversation,
                currentUserId = currentUserId,
                busy = busy,
                canInteract = !isBlocked,
                onAccept = onAcceptRequest,
                onCloseOrDecline = onCloseOrDecline
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Privacy & support",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Messages are not end-to-end encrypted. Closing a conversation does not block future requests.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isBlocked) {
                OutlinedButton(
                    onClick = onUnblockPeer,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("live_unblock_button")
                ) {
                    Text("Unblock account")
                }
            } else {
                OutlinedButton(
                    onClick = { showBlockConfirm = true },
                    enabled = !busy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("live_block_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Block account")
                }
            }
        }
    }
}

@Composable
private fun ConversationLifecycleBar(
    conversation: Conversation,
    currentUserId: String,
    busy: Boolean,
    canInteract: Boolean,
    onAccept: () -> Unit,
    onCloseOrDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            conversation.status == "active" -> {
                OutlinedButton(
                    onClick = onCloseOrDecline,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("live_close_conversation_button")
                ) {
                    Text("Close conversation")
                }
            }
            conversation.status == "pending" && conversation.createdBy == currentUserId -> {
                ChatboxNoticeBanner(
                    text = "Waiting for the other person to accept. Messaging is unavailable until then."
                )
                OutlinedButton(
                    onClick = onCloseOrDecline,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("live_cancel_request_button")
                ) {
                    Text("Cancel request")
                }
            }
            conversation.status == "pending" -> {
                ChatboxNoticeBanner(
                    text = "This person requested a conversation. Accept only if you trust them."
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onAccept,
                        enabled = !busy && canInteract,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("live_accept_request_button")
                    ) {
                        Text("Accept request")
                    }
                    OutlinedButton(
                        onClick = onCloseOrDecline,
                        enabled = !busy,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("live_decline_request_button")
                    ) {
                        Text("Decline")
                    }
                }
            }
            else -> {
                ChatboxNoticeBanner(
                    text = "This conversation is closed or unavailable. Previous messages remain visible."
                )
            }
        }
    }
}

@Composable
private fun LiveMessageBubble(
    message: LiveMessage,
    isOwn: Boolean
) {
    val timeText = remember(message.sentAt, message.pendingWrites) {
        if (message.pendingWrites || message.sentAt == null) {
            "Sending…"
        } else {
            DateFormat.getTimeInstance(DateFormat.SHORT, Locale.ENGLISH).format(message.sentAt.toDate())
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isOwn) 16.dp else 4.dp,
                    bottomEnd = if (isOwn) 4.dp else 16.dp
                ),
                color = if (isOwn) ChatboxLiveOutgoingBubble else ChatboxLiveIncomingBubble
            ) {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOwn) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
