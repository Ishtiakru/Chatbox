package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.ai.SairaAiClient
import com.example.data.ai.SairaPrompts
import com.example.data.model.ChatboxConstants
import com.example.data.model.Conversation
import com.example.data.model.LiveMessage
import com.example.data.model.UserProfile
import com.example.data.repository.LiveChatRepository
import com.example.saira.SairaLocationHelper
import com.example.saira.SairaTimeGreeting
import com.example.ui.components.ChatboxLogoWithUpdateBadge
import com.example.ui.components.ChatboxSearchField
import com.example.ui.components.MessengerAvatar
import com.example.ui.components.NoticeBanner
import com.example.ui.components.SairaMetaOrb
import com.example.ui.components.VoiceNoteBubblePlayer
import com.example.ui.components.VoiceNoteRecorder
import com.example.ui.components.VoiceRecordingBar
import com.example.ui.theme.ChatboxErrorInk
import com.example.ui.theme.ChatboxPrimary
import com.example.ui.theme.ChatboxSoftLavender
import com.example.ui.theme.ChatboxUpdateGreen
import com.example.ui.theme.LocalChatboxColors
import com.example.util.friendlyFirestoreMessage
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private val QUICK_REACTIONS = listOf("💜", "👍", "❤️", "😂", "😮", "🔥", "🎉", "🙏")

private fun formatLiveTimestamp(msg: LiveMessage, isOnline: Boolean): String {
    if (msg.pendingWrites && !isOnline) return "Queued · offline"
    val date = msg.sentAt?.toDate() ?: return "Sending…"
    return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
}

@Composable
fun LiveChatsScreen(
    currentUserId: String,
    displayName: String?,
    isOnline: Boolean,
    appUpdateActive: Boolean,
    oneMinAutoReplyEnabled: Boolean,
    liveLocationEnabled: Boolean,
    liveLocationAllowedPeers: Set<String>,
    quickReaction: String,
    ownProfile: UserProfile?,
    homeAnnouncement: String,
    appTitle: String = "Chatbox",
    liveChatRepo: LiveChatRepository,
    onQuickReactionChange: (String) -> Unit,
    onSetPeerLocationAllowed: (String, Boolean) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenCalls: () -> Unit,
    onAppUpdateClick: () -> Unit
) {
    val colors = LocalChatboxColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val conversations by remember(currentUserId) {
        liveChatRepo.observeUserConversations(currentUserId)
    }.collectAsState(initial = emptyList())

    val blockedIds by remember(currentUserId) {
        liveChatRepo.observeBlockedPeerIds(currentUserId)
    }.collectAsState(initial = emptySet())

    val peerProfiles = remember { mutableStateMapOf<String, UserProfile>() }
    var selectedConvId by remember { mutableStateOf<String?>(null) }
    var showMobileThread by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var listSearch by remember { mutableStateOf("") }
    var showNewConvDialog by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    // Fetch peer profiles cleanly for active conversations
    val peerUids = remember(conversations, currentUserId) {
        conversations.mapNotNull { c -> c.participants.firstOrNull { it != currentUserId } }.distinct()
    }
    LaunchedEffect(peerUids) {
        peerUids.forEach { pUid ->
            if (!peerProfiles.containsKey(pUid)) {
                launch {
                    liveChatRepo.observePeerProfile(pUid).collect { prof ->
                        if (prof != null) peerProfiles[pUid] = prof
                    }
                }
            }
        }
    }

    val selectedConversation = remember(conversations, selectedConvId) {
        conversations.firstOrNull { it.id == selectedConvId }
    }

    val filteredConversations = remember(conversations, listSearch, peerProfiles.size, currentUserId) {
        val q = listSearch.trim().lowercase()
        if (q.isEmpty()) conversations
        else conversations.filter { conv ->
            val peerId = conv.participants.firstOrNull { it != currentUserId }.orEmpty()
            val peer = peerProfiles[peerId]
            peerId.lowercase().contains(q) ||
                peer?.displayName?.lowercase()?.contains(q) == true ||
                peer?.shortId?.contains(q) == true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
    ) {
        val isWide = maxWidth >= 720.dp

        if (!isWide && showMobileThread) {
            BackHandler {
                showDetails = false
                showMobileThread = false
            }
        }

        if (isWide) {
            Row(modifier = Modifier.fillMaxSize()) {
                LiveConversationListPane(
                    modifier = Modifier.width(320.dp).fillMaxHeight(),
                    currentUserId = currentUserId,
                    ownProfile = ownProfile,
                    isOnline = isOnline,
                    appUpdateActive = appUpdateActive,
                    homeAnnouncement = homeAnnouncement,
                    appTitle = appTitle,
                    listSearch = listSearch,
                    onListSearchChange = { listSearch = it },
                    conversations = filteredConversations,
                    peerProfiles = peerProfiles,
                    blockedIds = blockedIds,
                    selectedConvId = selectedConvId,
                    actionError = actionError,
                    onSelectConversation = { id -> selectedConvId = id },
                    onNewChatClick = { showNewConvDialog = true },
                    onOpenProfile = onOpenProfile,
                    onAppUpdateClick = onAppUpdateClick
                )
                HorizontalDivider(modifier = Modifier.width(1.dp).fillMaxHeight(), color = colors.border)
                if (selectedConversation != null) {
                    val peerId = selectedConversation.participants.firstOrNull { it != currentUserId }.orEmpty()
                    LiveThreadPane(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        currentUserId = currentUserId,
                        userDisplayName = ownProfile?.displayName ?: displayName ?: "Friend",
                        conversation = selectedConversation,
                        peerProfile = peerProfiles[peerId],
                        peerUid = peerId,
                        isBlockedByMe = peerId in blockedIds,
                        isOnline = isOnline,
                        oneMinAutoReplyEnabled = oneMinAutoReplyEnabled,
                        liveLocationEnabled = liveLocationEnabled,
                        peerLocationAllowed = peerId in liveLocationAllowedPeers,
                        onSetPeerLocationAllowed = { allowed -> onSetPeerLocationAllowed(peerId, allowed) },
                        quickReaction = quickReaction,
                        showBack = false,
                        liveChatRepo = liveChatRepo,
                        onBack = {},
                        onToggleDetails = { showDetails = !showDetails },
                        onOpenCalls = onOpenCalls,
                        onQuickReactionChange = onQuickReactionChange
                    )
                } else {
                    LiveEmptyThreadPlaceholder(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        ownProfile = ownProfile,
                        onNewChatClick = { showNewConvDialog = true }
                    )
                }
            }
        } else {
            if (!showMobileThread || selectedConversation == null) {
                LiveConversationListPane(
                    modifier = Modifier.fillMaxSize(),
                    currentUserId = currentUserId,
                    ownProfile = ownProfile,
                    isOnline = isOnline,
                    appUpdateActive = appUpdateActive,
                    homeAnnouncement = homeAnnouncement,
                    appTitle = appTitle,
                    listSearch = listSearch,
                    onListSearchChange = { listSearch = it },
                    conversations = filteredConversations,
                    peerProfiles = peerProfiles,
                    blockedIds = blockedIds,
                    selectedConvId = selectedConvId,
                    actionError = actionError,
                    onSelectConversation = { id ->
                        selectedConvId = id
                        showMobileThread = true
                    },
                    onNewChatClick = { showNewConvDialog = true },
                    onOpenProfile = onOpenProfile,
                    onAppUpdateClick = onAppUpdateClick
                )
            } else {
                val peerId = selectedConversation.participants.firstOrNull { it != currentUserId }.orEmpty()
                LiveThreadPane(
                    modifier = Modifier.fillMaxSize(),
                    currentUserId = currentUserId,
                    userDisplayName = ownProfile?.displayName ?: displayName ?: "Friend",
                    conversation = selectedConversation,
                    peerProfile = peerProfiles[peerId],
                    peerUid = peerId,
                    isBlockedByMe = peerId in blockedIds,
                    isOnline = isOnline,
                    oneMinAutoReplyEnabled = oneMinAutoReplyEnabled,
                    liveLocationEnabled = liveLocationEnabled,
                    peerLocationAllowed = peerId in liveLocationAllowedPeers,
                    onSetPeerLocationAllowed = { allowed -> onSetPeerLocationAllowed(peerId, allowed) },
                    quickReaction = quickReaction,
                    showBack = true,
                    liveChatRepo = liveChatRepo,
                    onBack = { showMobileThread = false },
                    onToggleDetails = { showDetails = !showDetails },
                    onOpenCalls = onOpenCalls,
                    onQuickReactionChange = onQuickReactionChange
                )
            }
        }
    }

    if (showNewConvDialog) {
        NewLiveChatDialog(
            isOnline = isOnline,
            onStartConversation = { identifier ->
                liveChatRepo.startOrOpenConversation(currentUserId, identifier)
            },
            onOpened = { convId ->
                selectedConvId = convId
                showMobileThread = true
                showNewConvDialog = false
                actionError = null
            },
            onDismiss = { showNewConvDialog = false }
        )
    }

    if (showDetails && selectedConversation != null) {
        val peerId = selectedConversation.participants.firstOrNull { it != currentUserId }.orEmpty()
        val peerProf = peerProfiles[peerId]
        val isBlocked = peerId in blockedIds
        Dialog(onDismissRequest = { showDetails = false }) {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MessengerAvatar(label = peerProf?.displayName ?: peerId, size = 64.dp)
                    Text(peerProf?.displayName ?: "Chatbox User", style = MaterialTheme.typography.titleMedium)
                    if (!peerProf?.shortId.isNullOrBlank()) {
                        Text(
                            text = "ID: ${peerProf!!.shortId}",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelLarge,
                            color = ChatboxPrimary
                        )
                    }
                    if (!peerProf?.about.isNullOrBlank()) {
                        Text(peerProf!!.about, style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
                    }
                    HorizontalDivider(color = colors.border)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedConversation.status != "closed") {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        liveChatRepo.updateConversationStatus(selectedConversation.id, "closed")
                                        showDetails = false
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Close Chat")
                            }
                        }
                        Button(
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isBlocked) ChatboxPrimary else ChatboxErrorInk
                            ),
                            onClick = {
                                scope.launch {
                                    if (isBlocked) {
                                        liveChatRepo.unblockPeerUser(currentUserId, peerId)
                                    } else {
                                        liveChatRepo.blockPeerUser(currentUserId, peerId, selectedConversation)
                                    }
                                    showDetails = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isBlocked) "Unblock" else "Block User")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveConversationListPane(
    modifier: Modifier,
    currentUserId: String,
    ownProfile: UserProfile?,
    isOnline: Boolean,
    appUpdateActive: Boolean,
    homeAnnouncement: String,
    appTitle: String,
    listSearch: String,
    onListSearchChange: (String) -> Unit,
    conversations: List<Conversation>,
    peerProfiles: Map<String, UserProfile>,
    blockedIds: Set<String>,
    selectedConvId: String?,
    actionError: String?,
    onSelectConversation: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onOpenProfile: () -> Unit,
    onAppUpdateClick: () -> Unit
) {
    val colors = LocalChatboxColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copiedOwn by remember { mutableStateOf(false) }
    val shortId = ownProfile?.shortId.orEmpty()

    Column(modifier = modifier.background(colors.surface)) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    ChatboxLogoWithUpdateBadge(
                        appUpdateActive = appUpdateActive,
                        onUpdateClick = onAppUpdateClick,
                        logoSize = 34.dp
                    )
                    Column {
                        Text(
                            text = "${appTitle.ifBlank { "Chatbox" }.uppercase()} · LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkSubtle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("Live Chats", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onNewChatClick, modifier = Modifier.testTag("live_new_chat_button")) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = "Add by 6-digit ID or phone", tint = ChatboxPrimary)
                    }
                    Surface(
                        onClick = onOpenProfile,
                        shape = CircleShape,
                        color = ChatboxSoftLavender,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("live_open_profile_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            MessengerAvatar(label = ownProfile?.displayName ?: "U", size = 38.dp)
                        }
                    }
                }
            }

            if (shortId.isNotEmpty()) {
                Surface(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("Chatbox 6-Digit ID", shortId))
                        copiedOwn = true
                        scope.launch {
                            delay(1600)
                            copiedOwn = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = colors.fieldBg,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Your 6-digit ID: $shortId",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelLarge,
                            color = ChatboxPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                imageVector = if (copiedOwn) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                contentDescription = "Copy your 6-digit ID",
                                tint = if (copiedOwn) ChatboxUpdateGreen else ChatboxPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(if (copiedOwn) "Copied" else "Copy", style = MaterialTheme.typography.labelSmall, color = ChatboxPrimary)
                        }
                    }
                }
            }

            ChatboxSearchField(
                value = listSearch,
                onValueChange = onListSearchChange,
                placeholder = "Search friend, 6-digit ID…",
                testTag = "live_search_input"
            )

            if (homeAnnouncement.isNotBlank()) NoticeBanner(text = homeAnnouncement)
            if (!isOnline) NoticeBanner(text = "Offline · Messages queue locally and sync automatically.", isError = true)
            actionError?.let { NoticeBanner(text = it, isError = true) }
        }

        HorizontalDivider(color = colors.border)

        if (conversations.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No live conversations yet", style = MaterialTheme.typography.titleSmall, color = colors.ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Add a friend using their 6-digit Chatbox ID or phone number.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onNewChatClick, modifier = Modifier.testTag("empty_add_friend_button")) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add by ID / Phone")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(conversations, key = { it.id }) { conv ->
                    val peerId = conv.participants.firstOrNull { it != currentUserId }.orEmpty()
                    val peer = peerProfiles[peerId]
                    val name = peer?.displayName ?: peerId.take(10)
                    val selected = conv.id == selectedConvId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) colors.selectedBg else Color.Transparent)
                            .clickable { onSelectConversation(conv.id) }
                            .padding(12.dp)
                            .testTag("live_conv_${conv.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MessengerAvatar(label = name, size = 44.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = when {
                                        peerId in blockedIds -> Color(0xFFFFF1ED)
                                        conv.status == "active" -> ChatboxSoftLavender
                                        else -> colors.fieldBg
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = when {
                                            peerId in blockedIds -> "BLOCKED"
                                            else -> conv.status.uppercase()
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (peerId in blockedIds) ChatboxErrorInk else ChatboxPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = peer?.shortId?.takeIf { it.isNotEmpty() }?.let { "ID: $it" }
                                    ?: peer?.about?.takeIf { it.isNotEmpty() }
                                    ?: "Chatbox contact",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkMuted,
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

@Composable
private fun LiveEmptyThreadPlaceholder(
    modifier: Modifier,
    ownProfile: UserProfile?,
    onNewChatClick: () -> Unit
) {
    val colors = LocalChatboxColors.current
    Column(
        modifier = modifier
            .background(colors.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Select a conversation", style = MaterialTheme.typography.titleLarge, color = colors.ink)
        Spacer(Modifier.height(6.dp))
        Text(
            text = ownProfile?.shortId?.takeIf { it.isNotEmpty() }
                ?.let { "Your 6-digit Chatbox ID is $it. Share it or tap Add Friend below." }
                ?: "Add a friend by their 6-digit ID or phone number to begin.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkMuted
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = onNewChatClick) {
            Text("Start Live Chat")
        }
    }
}

@Composable
private fun LiveThreadPane(
    modifier: Modifier,
    currentUserId: String,
    userDisplayName: String,
    conversation: Conversation,
    peerProfile: UserProfile?,
    peerUid: String,
    isBlockedByMe: Boolean,
    isOnline: Boolean,
    oneMinAutoReplyEnabled: Boolean,
    liveLocationEnabled: Boolean,
    peerLocationAllowed: Boolean,
    onSetPeerLocationAllowed: (Boolean) -> Unit,
    quickReaction: String,
    showBack: Boolean,
    liveChatRepo: LiveChatRepository,
    onBack: () -> Unit,
    onToggleDetails: () -> Unit,
    onOpenCalls: () -> Unit,
    onQuickReactionChange: (String) -> Unit
) {
    val colors = LocalChatboxColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val messagesState by remember(conversation.id) {
        liveChatRepo.observeConversationMessages(conversation.id)
    }.collectAsState(initial = emptyList<LiveMessage>() to null)

    var olderMessages by remember(conversation.id) { mutableStateOf<List<LiveMessage>>(emptyList()) }
    var olderCursor by remember(conversation.id) { mutableStateOf<DocumentSnapshot?>(null) }
    var hasMoreOlder by remember(conversation.id) { mutableStateOf(true) }
    var loadingOlder by remember(conversation.id) { mutableStateOf(false) }

    val combinedMessages = remember(olderMessages, messagesState.first) {
        val seen = linkedMapOf<String, LiveMessage>()
        olderMessages.forEach { seen[it.id] = it }
        messagesState.first.forEach { seen[it.id] = it }
        seen.values.toList()
    }

    var draft by remember { mutableStateOf("") }
    var busySending by remember { mutableStateOf(false) }
    var threadError by remember { mutableStateOf<String?>(null) }
    var lightboxUrl by remember { mutableStateOf<String?>(null) }
    var showReactionPicker by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Voice recorder
    val recorder = remember { VoiceNoteRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingMs by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose { recorder.stop() }
    }

    LaunchedEffect(isRecording) {
        val start = System.currentTimeMillis()
        while (isRecording) {
            recordingMs = System.currentTimeMillis() - start
            delay(200)
        }
    }

    // One-minute Saira companion banner state
    var companionNotice by remember(conversation.id) { mutableStateOf<String?>(null) }
    var showLocationConsentDialog by remember(conversation.id) { mutableStateOf(false) }
    val latestMsg = combinedMessages.lastOrNull()
    val peerName = peerProfile?.displayName ?: peerUid.take(10)
    LaunchedEffect(latestMsg?.id, oneMinAutoReplyEnabled, conversation.status, isOnline, peerName) {
        if (!oneMinAutoReplyEnabled || conversation.status != "active") {
            companionNotice = null
            return@LaunchedEffect
        }
        val msg = latestMsg ?: return@LaunchedEffect
        if (msg.pendingWrites) {
            companionNotice = null
            return@LaunchedEffect
        }
        val ageMs = msg.sentAt?.toDate()?.let { System.currentTimeMillis() - it.time } ?: 0L
        if (ageMs > 10 * 60_000L) return@LaunchedEffect
        val waitMs = (60_000L - ageMs).coerceAtLeast(1_000L)
        delay(waitMs)

        val senderName = if (msg.senderId == currentUserId) userDisplayName else peerName
        val recipientName = if (msg.senderId == currentUserId) peerName else userDisplayName
        val fallbackReply = SairaTimeGreeting.buildOneMinuteAbsenceFallback(
            senderName = senderName,
            peerName = recipientName
        )

        if (!isOnline) {
            companionNotice = fallbackReply
            return@LaunchedEffect
        }

        val aiReply = runCatching {
            val prompt = SairaPrompts.buildOneMinAbsencePrompt(
                senderName = senderName,
                peerName = recipientName,
                lastMessageText = msg.body.ifBlank { "[Media message]" }
            )
            SairaAiClient.complete(SairaPrompts.chatSystemInstruction, emptyList(), prompt)
        }.getOrNull()?.trim().orEmpty()

        companionNotice = aiReply.ifEmpty { fallbackReply }
    }

    // Zero-permission Android Photo Picker
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null || conversation.status != "active" || isBlockedByMe || !isOnline) return@rememberLauncherForActivityResult
        scope.launch {
            busySending = true
            threadError = null
            liveChatRepo.uploadChatImage(conversation.id, currentUserId, uri)
                .onSuccess { downloadUrl ->
                    liveChatRepo.sendLiveMessage(
                        conversationId = conversation.id,
                        senderId = currentUserId,
                        rawBody = draft,
                        imageUrl = downloadUrl
                    ).onSuccess { draft = "" }
                        .onFailure { threadError = friendlyFirestoreMessage(it) }
                }
                .onFailure { threadError = it.message ?: "Image upload failed." }
            busySending = false
        }
    }

    fun startRecordingAudio() {
        isRecording = true
        recordingMs = 0L
        if (recorder.start() == null) {
            isRecording = false
            threadError = "Could not start microphone recorder."
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecordingAudio()
        else threadError = "Microphone permission is required to record voice notes."
    }

    fun dispatchLiveLocationMessage() {
        val snapshot = SairaLocationHelper.getBestAvailableLocation(context)
        if (snapshot == null) {
            threadError = "লোকেশন পাওয়া যায়নি—ডিভাইসের Location চালু আছে কিনা দেখুন।"
            return
        }
        scope.launch {
            busySending = true
            threadError = null
            liveChatRepo.sendLiveMessage(
                conversationId = conversation.id,
                senderId = currentUserId,
                rawBody = snapshot.toShareText()
            ).onFailure { threadError = friendlyFirestoreMessage(it) }
            busySending = false
        }
    }

    val locationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) dispatchLiveLocationMessage()
        else threadError = "লোকেশন শেয়ার করতে লোকেশন পারমিশন প্রয়োজন।"
    }

    val listState = rememberLazyListState()
    LaunchedEffect(combinedMessages.size) {
        if (combinedMessages.isNotEmpty() && searchQuery.isEmpty()) {
            listState.animateScrollToItem(combinedMessages.size - 1)
        }
    }

    val isPendingRecipient = conversation.status == "pending" && conversation.createdBy != currentUserId

    Column(modifier = modifier.background(colors.background)) {
        // Header
        Surface(color = colors.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showBack) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("live_thread_back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = ChatboxPrimary)
                    }
                }
                MessengerAvatar(label = peerName, size = 38.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(peerName, style = MaterialTheme.typography.titleSmall, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = peerProfile?.shortId?.takeIf { it.isNotEmpty() }?.let { "ID: $it · ${conversation.status}" } ?: conversation.status,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkSubtle,
                        maxLines = 1
                    )
                }
                IconButton(onClick = { searchOpen = !searchOpen }) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search in thread", tint = ChatboxPrimary)
                }
                IconButton(onClick = onOpenCalls) {
                    Icon(Icons.Rounded.Phone, contentDescription = "Calls", tint = ChatboxPrimary)
                }
                IconButton(onClick = onToggleDetails) {
                    Icon(Icons.Rounded.Info, contentDescription = "Conversation details", tint = ChatboxPrimary)
                }
            }
        }
        HorizontalDivider(color = colors.border)

        if (searchOpen) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                ChatboxSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Search messages in this chat…",
                    testTag = "live_thread_search_input"
                )
            }
        }

        // Consent banners
        if (conversation.status == "pending") {
            Surface(color = ChatboxSoftLavender, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isPendingRecipient) {
                            "$peerName invited you to connect."
                        } else {
                            "Waiting for $peerName to accept your chat request."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.ink,
                        modifier = Modifier.weight(1f)
                    )
                    if (isPendingRecipient) {
                        Button(
                            onClick = {
                                scope.launch {
                                    liveChatRepo.updateConversationStatus(conversation.id, "active")
                                        .onFailure { threadError = friendlyFirestoreMessage(it) }
                                }
                            },
                            modifier = Modifier.testTag("accept_conversation_button")
                        ) {
                            Text("Accept")
                        }
                    }
                }
            }
        } else if (conversation.status == "closed") {
            NoticeBanner(
                text = "This conversation is closed.",
                isError = true,
                modifier = Modifier.padding(10.dp)
            )
        }

        threadError?.let { err ->
            NoticeBanner(text = err, isError = true, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }

        // Message list
        val displayedMessages = remember(combinedMessages, searchQuery) {
            val q = searchQuery.trim().lowercase()
            if (q.isEmpty()) combinedMessages
            else combinedMessages.filter { it.body.lowercase().contains(q) }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messagesState.first.size >= 100 && hasMoreOlder && searchQuery.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(
                            enabled = !loadingOlder,
                            onClick = {
                                val cursor = olderCursor ?: messagesState.second ?: return@TextButton
                                scope.launch {
                                    loadingOlder = true
                                    liveChatRepo.fetchOlderMessages(conversation.id, cursor, 50L)
                                        .onSuccess { (older, nextCursor) ->
                                            olderMessages = older + olderMessages
                                            olderCursor = nextCursor
                                            hasMoreOlder = older.size >= 50
                                        }
                                    loadingOlder = false
                                }
                            }
                        ) {
                            Text(if (loadingOlder) "Loading older…" else "Load older messages")
                        }
                    }
                }
            }

            items(displayedMessages, key = { it.id }) { msg ->
                val isMe = msg.senderId == currentUserId
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                ) {
                    Surface(
                        color = if (isMe) colors.outgoingBubble else colors.incomingBubble,
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isMe) 18.dp else 4.dp,
                            bottomEnd = if (isMe) 4.dp else 18.dp
                        ),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!msg.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = msg.imageUrl,
                                    contentDescription = "Shared photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(190.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { lightboxUrl = msg.imageUrl }
                                )
                            }
                            if (!msg.audioUrl.isNullOrBlank()) {
                                VoiceNoteBubblePlayer(
                                    audioUrl = msg.audioUrl,
                                    durationMs = msg.audioDurationMs,
                                    isMe = isMe
                                )
                            }
                            if (msg.body.isNotBlank()) {
                                Text(
                                    text = msg.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isMe) colors.outgoingText else colors.incomingText
                                )
                            }
                        }
                    }
                    Text(
                        text = formatLiveTimestamp(msg, isOnline),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkSubtle,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // 1-Minute Saira Companion Card
        companionNotice?.let { note ->
            Surface(
                color = Color(0xFFF7F5FB),
                border = BorderStroke(1.dp, ChatboxPrimary.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SairaMetaOrb(size = 24.dp, animate = false)
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.ink,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { companionNotice = null }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Dismiss companion note", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Quick reaction selector
        if (showReactionPicker) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QUICK_REACTIONS.forEach { emoji ->
                    Surface(
                        onClick = {
                            onQuickReactionChange(emoji)
                            showReactionPicker = false
                        },
                        shape = CircleShape,
                        color = if (emoji == quickReaction) ChatboxSoftLavender else colors.fieldBg
                    ) {
                        Text(emoji, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
        }

        // Composer / Voice Recorder
        val canSend = conversation.status == "active" && !isBlockedByMe
        if (isRecording) {
            VoiceRecordingBar(
                elapsedMs = recordingMs,
                onStop = {
                    isRecording = false
                    val (file, durMs) = recorder.stop()
                    if (file != null && durMs >= 400L && canSend && isOnline) {
                        scope.launch {
                            busySending = true
                            liveChatRepo.uploadChatVoiceNote(conversation.id, currentUserId, file)
                                .onSuccess { audioUrl ->
                                    liveChatRepo.sendLiveMessage(
                                        conversationId = conversation.id,
                                        senderId = currentUserId,
                                        rawBody = "",
                                        audioUrl = audioUrl,
                                        audioDurationMs = durMs
                                    )
                                }
                                .onFailure { threadError = it.message ?: "Voice note upload failed." }
                            runCatching { file.delete() }
                            busySending = false
                        }
                    } else {
                        runCatching { file?.delete() }
                    }
                },
                onCancel = {
                    isRecording = false
                    val (file, _) = recorder.stop()
                    runCatching { file?.delete() }
                }
            )
        } else {
            HorizontalDivider(color = colors.border)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    enabled = canSend && isOnline && !busySending,
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("live_image_picker_button")
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = "Send compressed photo", tint = ChatboxPrimary)
                }

                IconButton(
                    enabled = canSend && isOnline && !busySending,
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) startRecordingAudio()
                        else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.testTag("live_record_audio_button")
                ) {
                    Icon(Icons.Rounded.Mic, contentDescription = "Record voice note", tint = ChatboxPrimary)
                }

                IconButton(
                    enabled = canSend && isOnline && !busySending,
                    onClick = {
                        if (!liveLocationEnabled || !peerLocationAllowed) {
                            showLocationConsentDialog = true
                        } else if (SairaLocationHelper.hasLocationPermission(context)) {
                            dispatchLiveLocationMessage()
                        } else {
                            locationPermLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    },
                    modifier = Modifier.testTag("live_location_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = "Share Live Location with consent",
                        tint = if (liveLocationEnabled && peerLocationAllowed) ChatboxUpdateGreen else ChatboxPrimary
                    )
                }

                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(ChatboxConstants.MAX_MESSAGE_BODY_LENGTH) },
                    enabled = canSend && !busySending,
                    placeholder = {
                        Text(
                            if (canSend) "Message $peerName…" else "Messaging locked until active"
                        )
                    },
                    singleLine = true,
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("live_message_input")
                )

                if (draft.isBlank()) {
                    Surface(
                        onClick = {
                            if (canSend && !busySending) {
                                scope.launch {
                                    liveChatRepo.sendLiveMessage(conversation.id, currentUserId, quickReaction)
                                }
                            } else {
                                showReactionPicker = !showReactionPicker
                            }
                        },
                        shape = CircleShape,
                        color = ChatboxSoftLavender,
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("live_quick_reaction_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(quickReaction)
                        }
                    }
                } else {
                    Surface(
                        onClick = {
                            if (canSend && !busySending && draft.isNotBlank()) {
                                val body = draft
                                draft = ""
                                scope.launch {
                                    busySending = true
                                    liveChatRepo.sendLiveMessage(conversation.id, currentUserId, body)
                                        .onFailure {
                                            threadError = friendlyFirestoreMessage(it)
                                            draft = body
                                        }
                                    busySending = false
                                }
                            }
                        },
                        shape = CircleShape,
                        color = ChatboxPrimary,
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("live_send_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (busySending) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Rounded.ArrowUpward, contentDescription = "Send message", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    lightboxUrl?.let { url ->
        Dialog(onDismissRequest = { lightboxUrl = null }) {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.Black)) {
                Box(contentAlignment = Alignment.TopEnd) {
                    AsyncImage(
                        model = url,
                        contentDescription = "Full photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp, max = 520.dp)
                    )
                    IconButton(onClick = { lightboxUrl = null }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close photo", tint = Color.White)
                    }
                }
            }
        }
    }

    if (showLocationConsentDialog) {
        AlertDialog(
            onDismissRequest = { showLocationConsentDialog = false },
            title = { Text("লাইভ লোকেশন অনুমতি") },
            text = {
                Text(
                    text = if (!liveLocationEnabled) {
                        "লোকেশন শেয়ার করার আগে Saira → Auto-Reply থেকে Live Location Sharing চালু করুন অথবা $peerName-এর জন্য অনুমতি দিন।"
                    } else {
                        "আপনি কি $peerName-এর সাথে আপনার লাইভ লোকেশন শেয়ার করার অনুমতি দিচ্ছেন?"
                    }
                )
            },
            confirmButton = {
                Button(
                    enabled = liveLocationEnabled,
                    onClick = {
                        onSetPeerLocationAllowed(true)
                        showLocationConsentDialog = false
                        if (SairaLocationHelper.hasLocationPermission(context)) {
                            dispatchLiveLocationMessage()
                        } else {
                            locationPermLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    }
                ) {
                    Text("অনুমতি দিন ও পাঠান")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationConsentDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }
}

@Composable
private fun NewLiveChatDialog(
    isOnline: Boolean,
    onStartConversation: suspend (String) -> Result<String>,
    onOpened: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var identifier by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Add Friend to Chat", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }
                Text(
                    "Enter their 6-digit Chatbox ID code (e.g. 482910) or linked phone number (+8801...).",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalChatboxColors.current.inkMuted
                )
                OutlinedTextField(
                    value = identifier,
                    onValueChange = {
                        identifier = it
                        error = null
                    },
                    singleLine = true,
                    placeholder = { Text("6-digit ID or phone number") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_friend_identifier_input")
                )
                error?.let { NoticeBanner(text = it, isError = true) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = !busy && identifier.isNotBlank() && isOnline,
                        onClick = {
                            scope.launch {
                                busy = true
                                error = null
                                onStartConversation(identifier)
                                    .onSuccess { convId -> onOpened(convId) }
                                    .onFailure { err -> error = friendlyFirestoreMessage(err) }
                                busy = false
                            }
                        },
                        modifier = Modifier.testTag("start_live_chat_button")
                    ) {
                        Text(if (busy) "Finding…" else "Start Chat")
                    }
                }
            }
        }
    }
}
