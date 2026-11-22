package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.notifications.NotificationHelper
import com.example.ui.components.ChatboxNoticeBanner
import com.example.ui.components.ConversationSearchBar
import com.example.ui.components.MessengerAvatar
import com.example.ui.components.SignInRequiredCard
import com.example.ui.components.highlightSearchMatch
import com.example.ui.viewmodel.LiveScreenMode
import com.example.ui.viewmodel.LiveThreadState

@Composable
fun LiveChatsSignInGate(
    authBusy: Boolean,
    onSignIn: () -> Unit,
    onExploreDemo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = "A SPACE FOR REAL CONVERSATIONS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Live chats",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "EXPERIMENTAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Text(
            text = "Separate from your local demo conversations.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SignInRequiredCard(
            title = "Sign in to connect.",
            body = "Live conversations require your Google account and a configured Firestore database.",
            icon = Icons.Rounded.Group,
            isBusy = authBusy,
            onSignIn = onSignIn,
            secondaryButtonLabel = "Explore the demo",
            onSecondaryClick = onExploreDemo,
            testTagPrefix = "live"
        )
    }
}

@Composable
fun LiveChatsScreen(
    currentUserId: String,
    conversationsState: UiState<List<Conversation>>,
    blockedIds: List<String>,
    peerProfiles: Map<String, UserProfile>,
    conversationMessages: Map<String, List<LiveMessage>>,
    threadState: LiveThreadState,
    profileReady: Boolean,
    onSelectConversation: (String) -> Unit,
    onSetScreen: (LiveScreenMode) -> Unit,
    onToggleNewConversation: () -> Unit,
    onSearchChange: (String) -> Unit,
    onPeerDraftChange: (String) -> Unit,
    onMarkIdCopied: () -> Unit,
    onStartConversation: () -> Unit,
    onRespondToRequest: (String, String) -> Unit,
    onLoadOlderMessages: () -> Unit,
    onMessageDraftChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onBlockPeer: (String, Conversation) -> Unit,
    onUnblockPeer: (String) -> Unit
) {
    val allConversations = (conversationsState as? UiState.Success)?.data.orEmpty()
    val activeConversation = allConversations.firstOrNull { it.id == threadState.selectedId }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTwoPane = maxWidth >= 600.dp

        if (!isTwoPane) {
            BackHandler(enabled = threadState.screen != LiveScreenMode.LIST) {
                when (threadState.screen) {
                    LiveScreenMode.DETAILS -> onSetScreen(LiveScreenMode.THREAD)
                    LiveScreenMode.THREAD -> onSetScreen(LiveScreenMode.LIST)
                    LiveScreenMode.LIST -> Unit
                }
            }
        }

        if (isTwoPane) {
            Row(modifier = Modifier.fillMaxSize()) {
                LiveListPane(
                    currentUserId = currentUserId,
                    conversationsState = conversationsState,
                    peerProfiles = peerProfiles,
                    conversationMessages = conversationMessages,
                    threadState = threadState,
                    profileReady = profileReady,
                    onToggleNewConversation = onToggleNewConversation,
                    onSearchChange = onSearchChange,
                    onPeerDraftChange = onPeerDraftChange,
                    onMarkIdCopied = onMarkIdCopied,
                    onStartConversation = onStartConversation,
                    onSelectConversation = onSelectConversation,
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outline)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (activeConversation != null) {
                        val peerUid = activeConversation.peerUid(currentUserId)
                        val isBlocked = peerUid in blockedIds
                        if (threadState.screen == LiveScreenMode.DETAILS) {
                            LiveDetailsPane(
                                conversation = activeConversation,
                                currentUserId = currentUserId,
                                peerProfile = peerProfiles[peerUid],
                                isBlocked = isBlocked,
                                busy = threadState.busy || !profileReady,
                                onBackToThread = { onSetScreen(LiveScreenMode.THREAD) },
                                onAcceptRequest = { onRespondToRequest(activeConversation.id, "active") },
                                onCloseOrDecline = { onRespondToRequest(activeConversation.id, "closed") },
                                onBlockPeer = { onBlockPeer(peerUid, activeConversation) },
                                onUnblockPeer = { onUnblockPeer(peerUid) },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            LiveThreadPane(
                                conversation = activeConversation,
                                currentUserId = currentUserId,
                                peerProfile = peerProfiles[peerUid],
                                threadState = threadState,
                                isBlocked = isBlocked,
                                profileReady = profileReady,
                                showBackButton = false,
                                onBack = {},
                                onOpenDetails = { onSetScreen(LiveScreenMode.DETAILS) },
                                onAcceptRequest = { onRespondToRequest(activeConversation.id, "active") },
                                onCloseOrDecline = { onRespondToRequest(activeConversation.id, "closed") },
                                onLoadOlder = onLoadOlderMessages,
                                onMessageDraftChange = onMessageDraftChange,
                                onSendMessage = onSendMessage,
                                onUnblockPeer = { onUnblockPeer(peerUid) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Choose a conversation or start a new one.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            when {
                threadState.screen == LiveScreenMode.DETAILS && activeConversation != null -> {
                    val peerUid = activeConversation.peerUid(currentUserId)
                    LiveDetailsPane(
                        conversation = activeConversation,
                        currentUserId = currentUserId,
                        peerProfile = peerProfiles[peerUid],
                        isBlocked = peerUid in blockedIds,
                        busy = threadState.busy || !profileReady,
                        onBackToThread = { onSetScreen(LiveScreenMode.THREAD) },
                        onAcceptRequest = { onRespondToRequest(activeConversation.id, "active") },
                        onCloseOrDecline = { onRespondToRequest(activeConversation.id, "closed") },
                        onBlockPeer = { onBlockPeer(peerUid, activeConversation) },
                        onUnblockPeer = { onUnblockPeer(peerUid) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                threadState.screen == LiveScreenMode.THREAD && activeConversation != null -> {
                    val peerUid = activeConversation.peerUid(currentUserId)
                    LiveThreadPane(
                        conversation = activeConversation,
                        currentUserId = currentUserId,
                        peerProfile = peerProfiles[peerUid],
                        threadState = threadState,
                        isBlocked = peerUid in blockedIds,
                        profileReady = profileReady,
                        showBackButton = true,
                        onBack = { onSetScreen(LiveScreenMode.LIST) },
                        onOpenDetails = { onSetScreen(LiveScreenMode.DETAILS) },
                        onAcceptRequest = { onRespondToRequest(activeConversation.id, "active") },
                        onCloseOrDecline = { onRespondToRequest(activeConversation.id, "closed") },
                        onLoadOlder = onLoadOlderMessages,
                        onMessageDraftChange = onMessageDraftChange,
                        onSendMessage = onSendMessage,
                        onUnblockPeer = { onUnblockPeer(peerUid) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    LiveListPane(
                        currentUserId = currentUserId,
                        conversationsState = conversationsState,
                        peerProfiles = peerProfiles,
                        conversationMessages = conversationMessages,
                        threadState = threadState,
                        profileReady = profileReady,
                        onToggleNewConversation = onToggleNewConversation,
                        onSearchChange = onSearchChange,
                        onPeerDraftChange = onPeerDraftChange,
                        onMarkIdCopied = onMarkIdCopied,
                        onStartConversation = onStartConversation,
                        onSelectConversation = onSelectConversation,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveListPane(
    currentUserId: String,
    conversationsState: UiState<List<Conversation>>,
    peerProfiles: Map<String, UserProfile>,
    conversationMessages: Map<String, List<LiveMessage>>,
    threadState: LiveThreadState,
    profileReady: Boolean,
    onToggleNewConversation: () -> Unit,
    onSearchChange: (String) -> Unit,
    onPeerDraftChange: (String) -> Unit,
    onMarkIdCopied: () -> Unit,
    onStartConversation: () -> Unit,
    onSelectConversation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var privacyExpanded by rememberSaveable { mutableStateOf(false) }
    var notificationsGranted by rememberSaveable {
        mutableStateOf(NotificationHelper.hasNotificationPermission(context))
    }
    var notificationBannerDismissed by rememberSaveable { mutableStateOf(false) }
    var notificationDeniedNotice by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        notificationDeniedNotice = !granted
    }

    val conversations = (conversationsState as? UiState.Success)?.data.orEmpty()
    val trimmedQuery = threadState.searchQuery.trim()
    val query = trimmedQuery.lowercase()
    val filtered = if (query.isEmpty()) {
        conversations
    } else {
        conversations.filter { conv ->
            val peerUid = conv.peerUid(currentUserId)
            val peerName = resolvePeerDisplayName(peerUid, peerProfiles[peerUid])
            val matchesNameOrId = peerUid.lowercase().contains(query) ||
                peerName.lowercase().contains(query)
            val matchesMessage = conversationMessages[conv.id].orEmpty().any { msg ->
                msg.body.lowercase().contains(query)
            }
            matchesNameOrId || matchesMessage
        }
    }
    val incomingRequests = filtered.filter { it.status == "pending" && it.createdBy != currentUserId }
    val chats = filtered.filterNot { it.status == "pending" && it.createdBy != currentUserId }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "CHATBOX · EXPERIMENTAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Messages",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Surface(
                onClick = onToggleNewConversation,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("live_new_toggle_button")
            ) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (threadState.newConversationOpen) Icons.Rounded.Close else Icons.Rounded.EditNote,
                        contentDescription = stringResource(id = R.string.cd_new_conversation),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Text(
            text = "Your space for real conversations · Names are unverified",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        ConversationSearchBar(
            query = threadState.searchQuery,
            onQueryChange = onSearchChange,
            testTag = "live_search_input"
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationsGranted &&
            !notificationBannerDismissed
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (notificationDeniedNotice) {
                                "Notifications are blocked. Enable them in Android Settings to get new message alerts."
                            } else {
                                "Turn on push notifications to be alerted when a new message arrives."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { notificationBannerDismissed = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("live_dismiss_notifications_button")
                        ) {
                            Text("Not now")
                        }
                        if (!notificationDeniedNotice) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("live_enable_notifications_button")
                            ) {
                                Text("Enable")
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (threadState.pendingNotice) {
            ChatboxNoticeBanner(
                text = "Still waiting for the service. The outcome is unknown; check the conversation before retrying."
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        val activeError = threadState.error.ifBlank {
            (conversationsState as? UiState.Error)?.message.orEmpty()
        }
        if (activeError.isNotBlank()) {
            ChatboxNoticeBanner(text = activeError, isError = true)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { privacyExpanded = !privacyExpanded }
                .minimumInteractiveComponentSize()
                .padding(vertical = 4.dp)
                .testTag("live_privacy_toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "About your privacy",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = if (privacyExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        AnimatedVisibility(visible = privacyExpanded) {
            Text(
                text = "Live chat is not end-to-end encrypted. Do not use it for sensitive conversations. Drafts stay in memory and clear when you leave or sign out.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        AnimatedVisibility(visible = threadState.newConversationOpen) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Your Chatbox ID",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = currentUserId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(ClipboardManager::class.java)
                                cm?.setPrimaryClip(ClipData.newPlainText("Chatbox ID", currentUserId))
                                onMarkIdCopied()
                            },
                            modifier = Modifier.testTag("live_copy_id_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = stringResource(id = R.string.cd_copy_chatbox_id),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (threadState.idCopiedNotice) {
                        Text(
                            text = "Copied your Chatbox ID.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "Request a conversation",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = threadState.peerDraft,
                        onValueChange = onPeerDraftChange,
                        singleLine = true,
                        placeholder = { Text("Their Chatbox ID") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onStartConversation() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("live_peer_uid_input")
                    )
                    Button(
                        onClick = onStartConversation,
                        enabled = !threadState.busy && profileReady && threadState.peerDraft.trim().isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("live_send_request_button")
                    ) {
                        Text(if (threadState.busy) "Please wait…" else "Send request")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (conversationsState is UiState.Loading) {
                item {
                    Row(
                        modifier = Modifier.padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Connecting to Firestore…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (incomingRequests.isNotEmpty()) {
                item(key = "requests_header") {
                    Text(
                        text = "MESSAGE REQUESTS (${incomingRequests.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                items(incomingRequests, key = { "req_${it.id}" }) { conv ->
                    LiveConversationRow(
                        conversation = conv,
                        currentUserId = currentUserId,
                        peerProfile = peerProfiles[conv.peerUid(currentUserId)],
                        messages = conversationMessages[conv.id].orEmpty(),
                        searchQuery = trimmedQuery,
                        selected = conv.id == threadState.selectedId,
                        onClick = { onSelectConversation(conv.id) }
                    )
                }
            }

            if (chats.isNotEmpty() || incomingRequests.isEmpty()) {
                item(key = "chats_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (query.isNotEmpty()) "MATCHING CHATS" else "CHATS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (chats.isNotEmpty()) {
                            Text(
                                text = chats.size.toString().padStart(2, '0'),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (chats.isEmpty() && incomingRequests.isEmpty() && conversationsState is UiState.Success) {
                item(key = "no_chats") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (query.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Rounded.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "No chats match \"$trimmedQuery\"",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Try searching another name or message keyword.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { onSearchChange("") },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("live_clear_search_empty_button")
                            ) {
                                Text("Clear search")
                            }
                        } else {
                            Text(
                                text = "No conversations yet. Tap the new message button to connect.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(chats, key = { it.id }) { conv ->
                    LiveConversationRow(
                        conversation = conv,
                        currentUserId = currentUserId,
                        peerProfile = peerProfiles[conv.peerUid(currentUserId)],
                        messages = conversationMessages[conv.id].orEmpty(),
                        searchQuery = trimmedQuery,
                        selected = conv.id == threadState.selectedId,
                        onClick = { onSelectConversation(conv.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveConversationRow(
    conversation: Conversation,
    currentUserId: String,
    peerProfile: UserProfile?,
    messages: List<LiveMessage>,
    searchQuery: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val peerUid = conversation.peerUid(currentUserId)
    val peerName = resolvePeerDisplayName(peerUid, peerProfile)
    val trimmedQuery = searchQuery.trim()
    val queryLower = trimmedQuery.lowercase()
    val matchedMessage = if (queryLower.isNotEmpty()) {
        messages.lastOrNull { it.body.lowercase().contains(queryLower) }
    } else {
        null
    }
    val latestMessage = messages.lastOrNull()
    val previewText = when {
        matchedMessage != null -> {
            val prefix = if (matchedMessage.senderId == currentUserId) "You: " else ""
            "$prefix${matchedMessage.body}"
        }
        conversation.status == "pending" && conversation.createdBy == currentUserId ->
            "Request sent · waiting"
        conversation.status == "pending" ->
            "Message request · tap to review"
        latestMessage != null -> {
            val prefix = if (latestMessage.senderId == currentUserId) "You: " else ""
            "$prefix${latestMessage.body}"
        }
        conversation.status == "active" -> "Conversation"
        else -> "Closed conversation"
    }

    val highlightColor = MaterialTheme.colorScheme.primary
    val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("live_conversation_${conversation.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MessengerAvatar(label = peerName, size = 44.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = highlightSearchMatch(
                        text = peerName,
                        query = trimmedQuery,
                        highlightColor = highlightColor,
                        highlightBackground = highlightBg
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = highlightSearchMatch(
                        text = previewText,
                        query = trimmedQuery,
                        highlightColor = highlightColor,
                        highlightBackground = highlightBg
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (matchedMessage != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
