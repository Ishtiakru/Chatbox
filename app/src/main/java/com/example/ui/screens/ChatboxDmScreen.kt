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
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PhoneMissed
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.model.ChatboxConstants
import com.example.data.model.ChatboxMessage
import com.example.data.model.ChatboxMessageKind
import com.example.data.model.ChatboxThread
import com.example.data.model.PeerSearchState
import com.example.data.model.UserProfile
import com.example.data.model.DisappearingOption
import com.example.ui.components.ChatboxLogoWithUpdateBadge
import com.example.ui.components.ChatboxSearchField
import com.example.ui.components.MessengerAvatar
import com.example.ui.components.NoticeBanner
import com.example.ui.components.SairaMetaOrb
import com.example.ui.components.VoiceNoteBubblePlayer
import com.example.ui.components.VoiceRecordingBar
import com.example.ui.components.VoiceNoteRecorder
import com.example.ui.theme.ChatboxErrorInk
import com.example.ui.theme.ChatboxPrimary
import com.example.ui.theme.ChatboxSoftLavender
import com.example.ui.theme.ChatboxUpdateGreen
import com.example.ui.theme.DmHairlineBorder
import com.example.ui.theme.DmInkDark
import com.example.ui.theme.DmInkMuted
import com.example.ui.theme.DmLavenderAccent
import com.example.ui.theme.DmLavenderDeep
import com.example.ui.theme.DmLavenderSoft
import com.example.ui.theme.LocalChatboxColors
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val STICKER_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "🔥", "💜", "🎉", "☕", "✨", "👋")

private enum class InboxFilter(val label: String) {
    ALL("All"),
    UNREAD("Unread"),
    GROUPS("Groups")
}

@Composable
fun ChatboxDmScreen(
    threads: List<ChatboxThread>,
    activeThreadId: String?,
    messagesByThread: Map<String, List<ChatboxMessage>>,
    peerNicknames: Map<String, String>,
    mutedThreadIds: Set<String>,
    blockedThreadIds: Set<String>,
    globalQuickReaction: String,
    backupPinHint: String,
    publicWelcomeBanner: String,
    isOnline: Boolean,
    appUpdateActive: Boolean,
    ownShortId: String = "482910",
    onSelectThread: (String) -> Unit,
    onSendMessage: (threadId: String, body: String, timeStr: String) -> Unit,
    onSendImage: (threadId: String, uri: Uri) -> Unit,
    onSendVoice: (threadId: String, tempFile: File, durationMs: Long) -> Unit,
    onCreateDirectThread: (name: String, identifier: String) -> String,
    onCreateGroupWithContact: (baseThreadId: String, groupName: String, members: List<String>) -> String,
    onMarkThreadUnread: (threadId: String, unread: Boolean) -> Unit,
    onToggleMute: (threadId: String, muted: Boolean) -> Unit,
    onUpdateNickname: (threadId: String, nickname: String) -> Unit,
    onThreadQuickReactionChange: (threadId: String, emoji: String) -> Unit,
    onToggleE2eeVerified: (threadId: String, verified: Boolean) -> Unit,
    onDisappearingChange: (threadId: String, key: String) -> Unit,
    onToggleReadReceipts: (threadId: String, enabled: Boolean) -> Unit,
    onUnlockHistoryWithPin: (threadId: String, pin: String) -> Boolean,
    onUpdateBackupPin: (newPin: String) -> Unit,
    onToggleBlock: (threadId: String) -> Unit,
    onSubmitReport: (threadId: String, peerName: String, reason: String, note: String) -> Unit,
    onLogCall: (threadId: String, peerName: String, isVideo: Boolean, isMissed: Boolean, durationLabel: String) -> Unit,
    onAppUpdateClick: () -> Unit,
    onSearchPeer: suspend (String) -> PeerSearchState = { PeerSearchState.Idle },
    onOpenPeer: (UserProfile) -> String = { "" },
    onRequestLogin: () -> Unit = {},
    sairaAutoReplyOn: Boolean = true,
    onSairaAutoReplyChange: (Boolean) -> Unit = {}
) {
    val selectedThread = remember(threads, activeThreadId) {
        threads.firstOrNull { it.id == activeThreadId } ?: threads.firstOrNull()
    }

    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var inboxFilter by remember { mutableStateOf(InboxFilter.ALL) }
    var showMobileThread by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showPeerProfileDialog by remember { mutableStateOf(false) }
    var showE2eeInfoDialog by remember { mutableStateOf(false) }
    var showRestorePinDialog by remember { mutableStateOf(false) }
    var activeCallDialogVideo by remember { mutableStateOf<Boolean?>(null) }
    var openThreadSearchSignal by remember { mutableIntStateOf(0) }

    val filteredThreads = remember(threads, searchQuery, inboxFilter, messagesByThread, peerNicknames) {
        val q = searchQuery.trim().lowercase()
        threads.filter { thread ->
            val matchesFilter = when (inboxFilter) {
                InboxFilter.ALL -> true
                InboxFilter.UNREAD -> thread.unreadCount > 0
                InboxFilter.GROUPS -> thread.isGroup
            }
            if (!matchesFilter) return@filter false
            if (q.isEmpty()) return@filter true

            val nick = peerNicknames[thread.id].orEmpty()
            val msgs = messagesByThread[thread.id].orEmpty()
            thread.name.lowercase().contains(q) ||
                nick.lowercase().contains(q) ||
                thread.shortId.contains(q) ||
                thread.phoneNumber.contains(q) ||
                msgs.any { it.text.lowercase().contains(q) }
        }
    }

    if (showDetails && selectedThread != null) {
        val effectiveQuickReaction = selectedThread.quickReaction.ifBlank { globalQuickReaction }.ifBlank { "👍" }
        ChatboxDmDetailsScreen(
            thread = selectedThread,
            allThreads = threads,
            peerNickname = peerNicknames[selectedThread.id],
            isMuted = selectedThread.id in mutedThreadIds,
            isBlocked = selectedThread.id in blockedThreadIds,
            effectiveQuickReaction = effectiveQuickReaction,
            onClose = { showDetails = false },
            onStartAudioCall = { activeCallDialogVideo = false },
            onStartVideoCall = { activeCallDialogVideo = true },
            onOpenPeerProfile = { showPeerProfileDialog = true },
            onToggleMute = { muted -> onToggleMute(selectedThread.id, muted) },
            onMarkAsUnread = {
                onMarkThreadUnread(selectedThread.id, true)
                showDetails = false
                showMobileThread = false
            },
            onCreateGroup = { groupName, members ->
                val newGroupId = onCreateGroupWithContact(selectedThread.id, groupName, members)
                onSelectThread(newGroupId)
                showDetails = false
                showMobileThread = true
            },
            onOpenSearch = { openThreadSearchSignal++ },
            onQuickReactionChange = { emoji -> onThreadQuickReactionChange(selectedThread.id, emoji) },
            onUpdateNickname = { nick -> onUpdateNickname(selectedThread.id, nick) },
            onToggleE2eeVerified = { verified -> onToggleE2eeVerified(selectedThread.id, verified) },
            onDisappearingChange = { key -> onDisappearingChange(selectedThread.id, key) },
            onToggleReadReceipts = { enabled -> onToggleReadReceipts(selectedThread.id, enabled) },
            onToggleBlock = { onToggleBlock(selectedThread.id) },
            onSubmitReport = { reason, note ->
                onSubmitReport(selectedThread.id, selectedThread.name, reason, note)
            }
        )
    } else {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .imePadding()
                .testTag("chatbox_dm_root")
        ) {
            val isWide = maxWidth >= 720.dp

            if (!isWide && showMobileThread) {
                BackHandler {
                    showMobileThread = false
                }
            } else if (searchExpanded) {
                BackHandler {
                    searchExpanded = false
                    searchQuery = ""
                }
            }

            if (isWide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    ChatboxInboxPane(
                        modifier = Modifier
                            .width(330.dp)
                            .fillMaxHeight(),
                        threads = threads,
                        filteredThreads = filteredThreads,
                        selectedThreadId = selectedThread?.id,
                        messagesByThread = messagesByThread,
                        peerNicknames = peerNicknames,
                        mutedThreadIds = mutedThreadIds,
                        blockedThreadIds = blockedThreadIds,
                        searchExpanded = searchExpanded,
                        searchQuery = searchQuery,
                        onToggleSearch = {
                            searchExpanded = !searchExpanded
                            if (!searchExpanded) searchQuery = ""
                        },
                        onSearchChange = { searchQuery = it },
                        activeFilter = inboxFilter,
                        onFilterChange = { inboxFilter = it },
                        publicWelcomeBanner = publicWelcomeBanner,
                        isOnline = isOnline,
                        appUpdateActive = appUpdateActive,
                        onAppUpdateClick = onAppUpdateClick,
                        onSelectThread = { id ->
                            onSelectThread(id)
                            onMarkThreadUnread(id, false)
                        },
                        onCreateDirectThread = onCreateDirectThread,
                        onSearchPeer = onSearchPeer,
                        onOpenPeer = onOpenPeer,
                        onRequestLogin = onRequestLogin,
                        sairaAutoReplyOn = sairaAutoReplyOn,
                        onSairaAutoReplyChange = onSairaAutoReplyChange
                    )
                    HorizontalDivider(modifier = Modifier.width(1.dp).fillMaxHeight(), color = DmHairlineBorder)
                    if (selectedThread != null) {
                        ChatboxThreadPane(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            thread = selectedThread,
                            messages = messagesByThread[selectedThread.id].orEmpty(),
                            peerNickname = peerNicknames[selectedThread.id],
                            isMuted = selectedThread.id in mutedThreadIds,
                            isBlocked = selectedThread.id in blockedThreadIds,
                            quickReaction = selectedThread.quickReaction.ifBlank { globalQuickReaction }.ifBlank { "👍" },
                            showBack = false,
                            openSearchSignal = openThreadSearchSignal,
                            onBack = {},
                            onOpenDetails = { showDetails = true },
                            onViewProfile = { showPeerProfileDialog = true },
                            onLearnMoreE2ee = { showE2eeInfoDialog = true },
                            onEnterPin = { showRestorePinDialog = true },
                            onStartAudioCall = { activeCallDialogVideo = false },
                            onStartVideoCall = { activeCallDialogVideo = true },
                            onSendMessage = { body ->
                                val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                                onSendMessage(selectedThread.id, body, now)
                            },
                            onSendImage = { uri -> onSendImage(selectedThread.id, uri) },
                            onSendVoice = { file, dur -> onSendVoice(selectedThread.id, file, dur) },
                            onUnblock = { onToggleBlock(selectedThread.id) }
                        )
                    }
                }
            } else {
                if (!showMobileThread || selectedThread == null) {
                    ChatboxInboxPane(
                        modifier = Modifier.fillMaxSize(),
                        threads = threads,
                        filteredThreads = filteredThreads,
                        selectedThreadId = selectedThread?.id,
                        messagesByThread = messagesByThread,
                        peerNicknames = peerNicknames,
                        mutedThreadIds = mutedThreadIds,
                        blockedThreadIds = blockedThreadIds,
                        searchExpanded = searchExpanded,
                        searchQuery = searchQuery,
                        onToggleSearch = {
                            searchExpanded = !searchExpanded
                            if (!searchExpanded) searchQuery = ""
                        },
                        onSearchChange = { searchQuery = it },
                        activeFilter = inboxFilter,
                        onFilterChange = { inboxFilter = it },
                        publicWelcomeBanner = publicWelcomeBanner,
                        isOnline = isOnline,
                        appUpdateActive = appUpdateActive,
                        onAppUpdateClick = onAppUpdateClick,
                        onSelectThread = { id ->
                            onSelectThread(id)
                            onMarkThreadUnread(id, false)
                            showMobileThread = true
                        },
                        onCreateDirectThread = onCreateDirectThread,
                        onSearchPeer = onSearchPeer,
                        onOpenPeer = onOpenPeer,
                        onRequestLogin = onRequestLogin,
                        sairaAutoReplyOn = sairaAutoReplyOn,
                        onSairaAutoReplyChange = onSairaAutoReplyChange
                    )
                } else {
                    ChatboxThreadPane(
                        modifier = Modifier.fillMaxSize(),
                        thread = selectedThread,
                        messages = messagesByThread[selectedThread.id].orEmpty(),
                        peerNickname = peerNicknames[selectedThread.id],
                        isMuted = selectedThread.id in mutedThreadIds,
                        isBlocked = selectedThread.id in blockedThreadIds,
                        quickReaction = selectedThread.quickReaction.ifBlank { globalQuickReaction }.ifBlank { "👍" },
                        showBack = true,
                        openSearchSignal = openThreadSearchSignal,
                        onBack = { showMobileThread = false },
                        onOpenDetails = { showDetails = true },
                        onViewProfile = { showPeerProfileDialog = true },
                        onLearnMoreE2ee = { showE2eeInfoDialog = true },
                        onEnterPin = { showRestorePinDialog = true },
                        onStartAudioCall = { activeCallDialogVideo = false },
                        onStartVideoCall = { activeCallDialogVideo = true },
                        onSendMessage = { body ->
                            val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                            onSendMessage(selectedThread.id, body, now)
                        },
                        onSendImage = { uri -> onSendImage(selectedThread.id, uri) },
                        onSendVoice = { file, dur -> onSendVoice(selectedThread.id, file, dur) },
                        onUnblock = { onToggleBlock(selectedThread.id) }
                    )
                }
            }
        }
    }


    // Peer Profile Dialog ("View profile" from thread or "Profile" from Details)
    if (showPeerProfileDialog && selectedThread != null) {
        val context = LocalContext.current
        val shownName = peerNicknames[selectedThread.id]?.takeIf { it.isNotBlank() } ?: selectedThread.name
        var copied by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showPeerProfileDialog = false },
            containerColor = Color.White,
            title = null,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MessengerAvatar(
                        label = shownName,
                        size = 76.dp,
                        showOnlineDot = selectedThread.online && selectedThread.id !in blockedThreadIds
                    )
                    Text(
                        text = shownName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DmInkDark
                    )
                    Text(
                        text = selectedThread.connectionLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = DmInkMuted
                    )
                    if (selectedThread.locationText.isNotBlank()) {
                        Text(
                            text = selectedThread.locationText,
                            style = MaterialTheme.typography.bodySmall,
                            color = DmInkMuted
                        )
                    }
                    if (selectedThread.workBio.isNotBlank()) {
                        Text(
                            text = selectedThread.workBio,
                            style = MaterialTheme.typography.bodySmall,
                            color = DmInkMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (selectedThread.shortId.isNotBlank()) {
                        Surface(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("Chatbox ID", selectedThread.shortId))
                                copied = true
                            },
                            shape = CircleShape,
                            color = DmLavenderSoft
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "ID: ${selectedThread.shortId}",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = DmLavenderDeep
                                )
                                Icon(
                                    imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy ID",
                                    tint = DmLavenderDeep,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(onClick = {
                            showPeerProfileDialog = false
                            activeCallDialogVideo = false
                        }) {
                            Icon(Icons.Rounded.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Call")
                        }
                        OutlinedButton(onClick = {
                            showPeerProfileDialog = false
                            activeCallDialogVideo = true
                        }) {
                            Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Video")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPeerProfileDialog = false }) { Text("Close") }
            }
        )
    }

    // E2EE Learn More Dialog
    if (showE2eeInfoDialog) {
        AlertDialog(
            onDismissRequest = { showE2eeInfoDialog = false },
            containerColor = Color.White,
            icon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = DmLavenderAccent) },
            title = { Text("End-to-end encryption", color = DmInkDark) },
            text = {
                Text(
                    text = "Messages and calls in this chat are secured with end-to-end encryption. Only people in this chat can read, listen to, or share them. You can verify keys anytime from Chat settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DmInkMuted
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = DmLavenderAccent),
                    onClick = { showE2eeInfoDialog = false }
                ) {
                    Text("Got it")
                }
            }
        )
    }

    // Restore Chat History PIN Dialog
    if (showRestorePinDialog && selectedThread != null) {
        var pinInput by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf<String?>(null) }
        var settingNewPin by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showRestorePinDialog = false },
            containerColor = Color.White,
            icon = { Icon(Icons.Rounded.Refresh, contentDescription = null, tint = DmLavenderAccent) },
            title = {
                Text(
                    text = if (settingNewPin) "Change backup PIN" else "Restore chat history",
                    color = DmInkDark
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (settingNewPin) {
                            "Enter a new 4 to 8 digit PIN to protect your encrypted chat history."
                        } else {
                            "Enter your PIN on this device to restore missing messages (Default PIN: $backupPinHint)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = DmInkMuted
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            pinInput = it.filter { ch -> ch.isDigit() }.take(8)
                            pinError = null
                        },
                        label = { Text(if (settingNewPin) "New PIN" else "Enter PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_pin_input")
                    )
                    pinError?.let { err ->
                        NoticeBanner(text = err, isError = true)
                    }
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = DmLavenderAccent),
                    onClick = {
                        if (settingNewPin) {
                            if (pinInput.length < 4) {
                                pinError = "Enter at least 4 digits."
                            } else {
                                onUpdateBackupPin(pinInput)
                                settingNewPin = false
                                pinInput = ""
                            }
                        } else {
                            val ok = onUnlockHistoryWithPin(selectedThread.id, pinInput)
                            if (ok) {
                                showRestorePinDialog = false
                            } else {
                                pinError = "Incorrect PIN. Try $backupPinHint."
                            }
                        }
                    },
                    modifier = Modifier.testTag("restore_pin_confirm")
                ) {
                    Text(if (settingNewPin) "Save PIN" else "Unlock")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        settingNewPin = !settingNewPin
                        pinInput = ""
                        pinError = null
                    }) {
                        Text(if (settingNewPin) "Back" else "Change PIN")
                    }
                    TextButton(onClick = { showRestorePinDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Active Call Dialog
    val isVideoCall = activeCallDialogVideo
    if (isVideoCall != null && selectedThread != null) {
        val shownName = peerNicknames[selectedThread.id]?.takeIf { it.isNotBlank() } ?: selectedThread.name
        var elapsedSec by remember(selectedThread.id, isVideoCall) { mutableIntStateOf(0) }
        LaunchedEffect(selectedThread.id, isVideoCall) {
            while (true) {
                delay(1000)
                elapsedSec++
            }
        }
        val statusText = if (elapsedSec < 3) {
            "Ringing…"
        } else {
            val mins = (elapsedSec - 3) / 60
            val secs = (elapsedSec - 3) % 60
            String.format(Locale.US, "Connected · %d:%02d", mins, secs)
        }

        Dialog(onDismissRequest = { activeCallDialogVideo = null }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF1E1930),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MessengerAvatar(
                        label = shownName,
                        size = 84.dp,
                        showOnlineDot = true
                    )
                    Text(
                        text = shownName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${if (isVideoCall) "Video call" else "Audio call"} · $statusText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                onLogCall(selectedThread.id, shownName, isVideoCall, true, "Missed")
                                activeCallDialogVideo = null
                            },
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("Mark missed")
                        }
                        Surface(
                            onClick = {
                                val mins = (elapsedSec - 3).coerceAtLeast(1) / 60
                                val secs = (elapsedSec - 3).coerceAtLeast(1) % 60
                                val dur = String.format(Locale.US, "%d:%02d", mins, secs)
                                onLogCall(selectedThread.id, shownName, isVideoCall, false, dur)
                                activeCallDialogVideo = null
                            },
                            shape = CircleShape,
                            color = Color(0xFFE53935),
                            modifier = Modifier
                                .size(56.dp)
                                .testTag("call_dialog_end")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.CallEnd,
                                    contentDescription = "End call",
                                    tint = Color.White
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
private fun ChatboxInboxPane(
    modifier: Modifier,
    threads: List<ChatboxThread>,
    filteredThreads: List<ChatboxThread>,
    selectedThreadId: String?,
    messagesByThread: Map<String, List<ChatboxMessage>>,
    peerNicknames: Map<String, String>,
    mutedThreadIds: Set<String>,
    blockedThreadIds: Set<String>,
    searchExpanded: Boolean,
    searchQuery: String,
    onToggleSearch: () -> Unit,
    onSearchChange: (String) -> Unit,
    activeFilter: InboxFilter,
    onFilterChange: (InboxFilter) -> Unit,
    publicWelcomeBanner: String,
    isOnline: Boolean,
    appUpdateActive: Boolean,
    onAppUpdateClick: () -> Unit,
    onSelectThread: (String) -> Unit,
    onCreateDirectThread: (name: String, identifier: String) -> String,
    onSearchPeer: suspend (String) -> PeerSearchState = { PeerSearchState.Idle },
    onOpenPeer: (UserProfile) -> String = { "" },
    onRequestLogin: () -> Unit = {},
    sairaAutoReplyOn: Boolean = true,
    onSairaAutoReplyChange: (Boolean) -> Unit = {}
) {
    val colors = LocalChatboxColors.current
    val activeContacts = remember(threads, blockedThreadIds) {
        threads.filter { !it.isGroup && it.online && it.id !in blockedThreadIds }
    }
    val trimmedQuery = searchQuery.trim()
    val isRemoteQuery = remember(trimmedQuery) {
        val q = trimmedQuery.removePrefix("#").replace(" ", "")
        ChatboxConstants.VALID_SHORT_ID_REGEX.matches(q) ||
            (q.none { it.isLetter() } && q.count { it.isDigit() } >= 7)
    }
    var peerSearch by remember { mutableStateOf<PeerSearchState>(PeerSearchState.Idle) }
    LaunchedEffect(trimmedQuery, isRemoteQuery) {
        if (!isRemoteQuery) {
            peerSearch = PeerSearchState.Idle
            return@LaunchedEffect
        }
        peerSearch = PeerSearchState.Searching
        delay(450)
        peerSearch = onSearchPeer(trimmedQuery)
    }
    val hasExactThread = remember(threads, trimmedQuery) {
        if (trimmedQuery.isEmpty()) true
        else {
            val digits = trimmedQuery.filter { it.isDigit() }
            threads.any { t ->
                !t.isGroup && (
                    t.name.equals(trimmedQuery, ignoreCase = true) ||
                        t.shortId == trimmedQuery ||
                        (digits.length >= 7 && t.phoneNumber.filter { it.isDigit() } == digits)
                    )
            }
        }
    }

    Column(modifier = modifier.background(Color.White)) {
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
                    Text(
                        text = "Chatbox",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = DmInkDark
                    )
                }
                // Small Saira on/off: ON = Saira answers for me only while I'm not on line.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("chatbox_saira_toggle_row")
                ) {
                    Text(
                        text = "Saira",
                        fontSize = 11.sp,
                        color = if (sairaAutoReplyOn) DmLavenderAccent else DmInkDark.copy(alpha = 0.5f)
                    )
                    androidx.compose.material3.Switch(
                        checked = sairaAutoReplyOn,
                        onCheckedChange = onSairaAutoReplyChange,
                        modifier = Modifier
                            .scale(0.62f)
                            .testTag("chatbox_saira_toggle")
                    )
                }
                IconButton(
                    onClick = onToggleSearch,
                    modifier = Modifier.testTag("chatbox_search_toggle_button")
                ) {
                    Icon(
                        imageVector = if (searchExpanded) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = if (searchExpanded) "Close search" else "Search user by name or ID",
                        tint = DmLavenderAccent
                    )
                }
            }

            AnimatedVisibility(visible = searchExpanded) {
                ChatboxSearchField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = "নাম, মোবাইল নম্বর বা ৬-সংখ্যার ID…",
                    testTag = "chatbox_search_input"
                )
            }
        }

        HorizontalDivider(color = DmHairlineBorder)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("chatbox_inbox_lazy_list"),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (activeContacts.isNotEmpty() && searchQuery.isBlank()) {
                item(key = "inbox_active_strip") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        activeContacts.forEach { contact ->
                            val shown = peerNicknames[contact.id]?.takeIf { it.isNotBlank() } ?: contact.name
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .width(60.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectThread(contact.id) }
                                    .padding(vertical = 4.dp)
                                    .testTag("active_strip_${contact.id}")
                            ) {
                                MessengerAvatar(
                                    label = shown,
                                    size = 50.dp,
                                    showOnlineDot = true
                                )
                                Text(
                                    text = shown.substringBefore(" "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DmInkDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            item(key = "inbox_filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InboxFilter.entries.forEach { filter ->
                        val selected = filter == activeFilter
                        FilterChip(
                            selected = selected,
                            onClick = { onFilterChange(filter) },
                            label = { Text(filter.label, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DmLavenderSoft,
                                selectedLabelColor = DmLavenderDeep
                            )
                        )
                    }
                }
            }

            if (publicWelcomeBanner.isNotBlank()) {
                item(key = "inbox_welcome_banner") {
                    Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                        NoticeBanner(text = publicWelcomeBanner)
                    }
                }
            }
            if (!isOnline) {
                item(key = "inbox_offline_banner") {
                    Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                        NoticeBanner(
                            text = "Offline · All DMs & chat history are saved locally on your device.",
                            isError = false
                        )
                    }
                }
            }
            if (trimmedQuery.isNotEmpty() && isRemoteQuery) {
                item(key = "inbox_peer_search_result") {
                    PeerSearchResultCard(
                        state = peerSearch,
                        onOpen = { profile ->
                            val id = onOpenPeer(profile)
                            if (id.isNotBlank()) onSelectThread(id)
                            onSearchChange("")
                            if (searchExpanded) onToggleSearch()
                        },
                        onRequestLogin = onRequestLogin
                    )
                }
            } else if (trimmedQuery.isNotEmpty() && !hasExactThread) {
                item(key = "inbox_search_hint") {
                    Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).testTag("chatbox_search_hint")) {
                        NoticeBanner(
                            text = "নতুন কাউকে খুঁজতে তার মোবাইল নম্বর বা ৬-সংখ্যার Chatbox ID লিখুন।"
                        )
                    }
                }
            }
            if (filteredThreads.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (trimmedQuery.isNotEmpty()) Icons.Rounded.Search else Icons.Rounded.ChatBubbleOutline,
                            contentDescription = null,
                            tint = DmLavenderAccent,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = when {
                                trimmedQuery.isNotEmpty() -> "No added chats match \"$trimmedQuery\""
                                threads.isEmpty() -> "No users added yet"
                                else -> "No conversations in this filter"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.ink,
                            textAlign = TextAlign.Center
                        )
                        if (threads.isEmpty() && trimmedQuery.isEmpty()) {
                            Text(
                                text = "Tap the search icon to find and add a user by name or 6-digit ID.",
                                style = MaterialTheme.typography.bodySmall,
                                color = DmInkMuted,
                                textAlign = TextAlign.Center
                            )
                            OutlinedButton(
                                onClick = { if (!searchExpanded) onToggleSearch() },
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .testTag("chatbox_empty_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = DmLavenderAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Search by name or ID", color = DmLavenderDeep)
                            }
                        } else if (trimmedQuery.isNotEmpty()) {
                            TextButton(onClick = { onSearchChange("") }) {
                                Text("Clear search", color = DmLavenderAccent)
                            }
                        }
                    }
                }
            } else {
                items(filteredThreads, key = { it.id }) { thread ->
                    val msgs = messagesByThread[thread.id].orEmpty()
                    val lastMsg = msgs.lastOrNull()
                    val isSelected = thread.id == selectedThreadId
                    val isMuted = thread.id in mutedThreadIds
                    val isBlocked = thread.id in blockedThreadIds
                    val shownName = peerNicknames[thread.id]?.takeIf { it.isNotBlank() } ?: thread.name
                    val isUnread = thread.unreadCount > 0

                    val previewText = when {
                        isBlocked -> "Blocked"
                        lastMsg == null -> thread.role
                        lastMsg.kind == ChatboxMessageKind.IMAGE ->
                            if (lastMsg.from == "me") "You sent a photo" else "Sent a photo"
                        lastMsg.kind == ChatboxMessageKind.VOICE ->
                            if (lastMsg.from == "me") "You sent a voice note" else "Sent a voice note"
                        lastMsg.from == "me" -> "You: ${lastMsg.text}"
                        else -> lastMsg.text
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) DmLavenderSoft.copy(alpha = 0.7f) else Color.Transparent)
                            .clickable { onSelectThread(thread.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("chatbox_thread_${thread.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MessengerAvatar(
                            label = shownName,
                            size = 52.dp,
                            showOnlineDot = thread.online && !isBlocked
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = shownName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (isUnread) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        color = DmInkDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    thread.label?.let { badge ->
                                        Surface(
                                            color = DmLavenderSoft,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = badge,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = DmLavenderDeep,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    if (isMuted) {
                                        Icon(
                                            imageVector = Icons.Rounded.NotificationsOff,
                                            contentDescription = "Muted",
                                            tint = DmLavenderAccent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                if (lastMsg != null) {
                                    Text(
                                        text = lastMsg.time,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isUnread) DmLavenderAccent else DmInkMuted,
                                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = previewText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isUnread) DmInkDark else DmInkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isUnread) {
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(DmLavenderAccent)
                                            .testTag("unread_dot_${thread.id}")
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
private fun ChatboxThreadPane(
    modifier: Modifier,
    thread: ChatboxThread,
    messages: List<ChatboxMessage>,
    peerNickname: String?,
    isMuted: Boolean,
    isBlocked: Boolean,
    quickReaction: String,
    showBack: Boolean,
    openSearchSignal: Int,
    onBack: () -> Unit,
    onOpenDetails: () -> Unit,
    onViewProfile: () -> Unit,
    onLearnMoreE2ee: () -> Unit,
    onEnterPin: () -> Unit,
    onStartAudioCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendImage: (Uri) -> Unit,
    onSendVoice: (File, Long) -> Unit,
    onUnblock: () -> Unit
) {
    val context = LocalContext.current
    val shownName = peerNickname?.takeIf { it.isNotBlank() } ?: thread.name
    val showOnlineDot = thread.online && !isBlocked
    val disappearingOption = DisappearingOption.fromKey(thread.disappearingKey)

    var draft by remember(thread.id) { mutableStateOf("") }
    var searchOpen by remember(thread.id) { mutableStateOf(false) }
    var searchQuery by remember(thread.id) { mutableStateOf("") }
    var lastHandledSearchSignal by remember(thread.id) { mutableIntStateOf(openSearchSignal) }
    var showStickerTray by remember(thread.id) { mutableStateOf(false) }
    var lightboxImagePath by remember(thread.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(openSearchSignal, thread.id) {
        if (openSearchSignal > lastHandledSearchSignal) {
            lastHandledSearchSignal = openSearchSignal
            searchOpen = true
        }
    }

    val recorder = remember { VoiceNoteRecorder(context) }
    var isRecording by remember(thread.id) { mutableStateOf(false) }
    var recordingMs by remember(thread.id) { mutableLongStateOf(0L) }
    var voiceError by remember(thread.id) { mutableStateOf<String?>(null) }

    DisposableEffect(thread.id) {
        onDispose {
            val (file, _) = recorder.stop()
            runCatching { file?.delete() }
        }
    }

    LaunchedEffect(isRecording) {
        val start = System.currentTimeMillis()
        while (isRecording) {
            recordingMs = System.currentTimeMillis() - start
            delay(200)
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && !isBlocked) {
            onSendImage(uri)
        }
    }

    fun startRecording() {
        voiceError = null
        isRecording = true
        recordingMs = 0L
        if (recorder.start() == null) {
            isRecording = false
            voiceError = "Could not start microphone recorder on this device."
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording()
        else voiceError = "Microphone permission is required to record voice notes."
    }

    val displayedMessages = remember(messages, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) messages
        else messages.filter { it.text.lowercase().contains(q) }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(displayedMessages.size, thread.id) {
        if (displayedMessages.size > 3 && searchQuery.isBlank()) {
            val targetIdx = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            listState.animateScrollToItem(targetIdx)
        }
    }

    Column(
        modifier = modifier
            .background(Color.White)
            .testTag("chatbox_thread_screen")
    ) {
        // Top Header (matches Screenshot 2: <, Avatar + green dot, Name + Active now, Phone, Video, Gear)
        Surface(color = Color.White) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showBack) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chatbox_thread_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = DmLavenderAccent
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenDetails() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("chatbox_thread_header_peer")
                ) {
                    MessengerAvatar(
                        label = shownName,
                        size = 40.dp,
                        showOnlineDot = showOnlineDot
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = shownName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DmInkDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = when {
                            isBlocked -> "Blocked"
                            disappearingOption != DisappearingOption.OFF ->
                                "Disappearing: ${disappearingOption.shortLabel}"
                            isMuted -> "Active now · Muted"
                            thread.isGroup -> thread.role
                            thread.online -> "Active now"
                            else -> thread.role
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = DmInkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = onStartAudioCall,
                    modifier = Modifier.testTag("chatbox_thread_call")
                ) {
                    Icon(Icons.Rounded.Phone, contentDescription = "Audio call", tint = DmLavenderAccent)
                }
                IconButton(
                    onClick = onStartVideoCall,
                    modifier = Modifier.testTag("chatbox_thread_video")
                ) {
                    Icon(Icons.Rounded.Videocam, contentDescription = "Video call", tint = DmLavenderAccent)
                }
                IconButton(
                    onClick = onOpenDetails,
                    modifier = Modifier.testTag("chatbox_thread_settings")
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Conversation settings", tint = DmLavenderAccent)
                }
            }
        }
        HorizontalDivider(color = DmHairlineBorder)

        if (searchOpen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ChatboxSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search in conversation…",
                        testTag = "chatbox_thread_search_input"
                    )
                }
                IconButton(onClick = {
                    searchOpen = false
                    searchQuery = ""
                }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close search", tint = DmLavenderAccent)
                }
            }
        }

        voiceError?.let { err ->
            NoticeBanner(
                text = err,
                isError = true,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // Scrollable Thread Intro + Message Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("chatbox_thread_lazy_list"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Screenshot 2 Intro Hero Header split into clean LazyColumn items
            if (searchQuery.isBlank()) {
                item(key = "intro_profile_${thread.id}") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp, bottom = 6.dp, start = 12.dp, end = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MessengerAvatar(
                            label = shownName,
                            size = 86.dp,
                            showOnlineDot = showOnlineDot
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = shownName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = DmInkDark,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = thread.connectionLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = DmInkMuted,
                            textAlign = TextAlign.Center
                        )
                        if (thread.locationText.isNotBlank()) {
                            Text(
                                text = thread.locationText,
                                style = MaterialTheme.typography.bodySmall,
                                color = DmInkMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (thread.workBio.isNotBlank()) {
                            Text(
                                text = thread.workBio,
                                style = MaterialTheme.typography.bodySmall,
                                color = DmInkMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                item(key = "intro_view_profile_${thread.id}") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            onClick = onViewProfile,
                            color = Color(0xFFF0F2F5),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .testTag("thread_view_profile_button")
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "View profile",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = DmInkDark
                                )
                            }
                        }
                    }
                }

                item(key = "intro_e2ee_${thread.id}") {
                    val e2eeText = buildAnnotatedString {
                        append("Messages and calls are secured with end-to-end encryption. Only people in this chat can read, listen to, or share them. ")
                        withStyle(SpanStyle(color = DmLavenderAccent, fontWeight = FontWeight.SemiBold)) {
                            append("Learn more.")
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = e2eeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = DmInkMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onLearnMoreE2ee() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("thread_e2ee_learn_more")
                        )
                    }
                }

                item(key = "intro_restore_${thread.id}") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Restore chat history card (exact Screenshot 2 card)
                        if (thread.historyLocked && !thread.historyUnlocked) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFF4F5F7),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("restore_chat_history_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = null,
                                            tint = DmLavenderAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Restore chat history",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = DmInkDark
                                        )
                                    }
                                    Text(
                                        text = "To see missing messages, enter your PIN on this device.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DmInkMuted,
                                        textAlign = TextAlign.Center
                                    )
                                    TextButton(
                                        onClick = onEnterPin,
                                        modifier = Modifier.testTag("restore_enter_pin_button")
                                    ) {
                                        Text(
                                            text = "Enter PIN",
                                            fontWeight = FontWeight.Bold,
                                            color = DmLavenderAccent
                                        )
                                    }
                                }
                            }
                        } else if (thread.historyUnlocked) {
                            Surface(
                                shape = CircleShape,
                                color = DmLavenderSoft,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = "Chat history restored",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DmLavenderDeep,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (disappearingOption != DisappearingOption.OFF) {
                            Surface(
                                shape = CircleShape,
                                color = DmLavenderSoft
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Timer,
                                        contentDescription = null,
                                        tint = DmLavenderDeep,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Disappearing messages: ${disappearingOption.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DmLavenderDeep
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(displayedMessages.size, key = { idx ->
                val m = displayedMessages[idx]
                m.id.ifBlank { "${idx}_${m.time}_${m.text.hashCode()}" }
            }) { idx ->
                val msg = displayedMessages[idx]
                val nextMsg = displayedMessages.getOrNull(idx + 1)
                val isMe = msg.from == "me"
                val isSairaAutoReply = thread.id != "saira" && !isMe && msg.senderName == "Saira"
                val isLastMessage = idx == displayedMessages.lastIndex
                val showLeftAvatar = !isMe && (nextMsg == null || nextMsg.from == "me" || nextMsg.senderName != msg.senderName)

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (!isMe) {
                            if (showLeftAvatar) {
                                if (isSairaAutoReply) {
                                    SairaMetaOrb(
                                        size = 28.dp,
                                        modifier = Modifier.padding(end = 8.dp, bottom = 2.dp)
                                    )
                                } else {
                                    MessengerAvatar(
                                        label = shownName,
                                        size = 28.dp,
                                        modifier = Modifier.padding(end = 8.dp, bottom = 2.dp)
                                    )
                                }
                            } else {
                                Spacer(Modifier.width(36.dp))
                            }
                        }

                        Column(
                            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            if (isSairaAutoReply) {
                                Text(
                                    text = "Saira AI · Auto-reply",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DmLavenderDeep,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                                )
                            }
                            val isSoloEmoji = msg.kind == ChatboxMessageKind.TEXT &&
                                msg.imageUri.isNullOrBlank() &&
                                msg.audioPath.isNullOrBlank() &&
                                msg.text.trim().length <= 4 &&
                                msg.text.trim().isNotEmpty() &&
                                msg.text.trim().none { it.isLetterOrDigit() }

                            when {
                                msg.kind == ChatboxMessageKind.MISSED_AUDIO_CALL ||
                                    msg.kind == ChatboxMessageKind.MISSED_VIDEO_CALL ||
                                    msg.kind == ChatboxMessageKind.CALL_COMPLETED -> {
                                    Surface(
                                        color = Color(0xFFF0F2F5),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                if (msg.kind == ChatboxMessageKind.MISSED_VIDEO_CALL) onStartVideoCall()
                                                else onStartAudioCall()
                                            }
                                            .testTag("call_event_bubble")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (msg.kind == ChatboxMessageKind.CALL_COMPLETED) {
                                                    Icons.Rounded.Phone
                                                } else {
                                                    Icons.AutoMirrored.Rounded.PhoneMissed
                                                },
                                                contentDescription = null,
                                                tint = DmLavenderAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = msg.text,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = DmInkDark
                                                )
                                                Text(
                                                    text = msg.time,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = DmInkMuted
                                                )
                                            }
                                        }
                                    }
                                }
                                isSoloEmoji -> {
                                    Text(
                                        text = msg.text.trim(),
                                        fontSize = 36.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                                else -> {
                                    Surface(
                                        color = if (isMe) DmLavenderAccent else Color(0xFFF0F2F5),
                                        shape = RoundedCornerShape(
                                            topStart = 20.dp,
                                            topEnd = 20.dp,
                                            bottomStart = if (isMe) 20.dp else 6.dp,
                                            bottomEnd = if (isMe) 6.dp else 20.dp
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(
                                                horizontal = if (!msg.imageUri.isNullOrBlank()) 8.dp else 14.dp,
                                                vertical = if (!msg.imageUri.isNullOrBlank()) 8.dp else 10.dp
                                            ),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (!msg.imageUri.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = File(msg.imageUri),
                                                    contentDescription = "Shared photo",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(max = 220.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .clickable { lightboxImagePath = msg.imageUri }
                                                )
                                            }
                                            if (!msg.audioPath.isNullOrBlank()) {
                                                VoiceNoteBubblePlayer(
                                                    audioUrl = msg.audioPath,
                                                    durationMs = msg.audioDurationMs,
                                                    isMe = isMe
                                                )
                                            }
                                            if (msg.text.isNotBlank() && msg.kind != ChatboxMessageKind.VOICE) {
                                                Text(
                                                    text = msg.text,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = if (isMe) Color.White else DmInkDark
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Mini Read Receipt Avatar on last message when Read Receipts is On (matches Screenshot 2)
                    if (isLastMessage && thread.readReceiptsEnabled && !isBlocked) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, end = 2.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            MessengerAvatar(
                                label = shownName,
                                size = 16.dp,
                                modifier = Modifier.testTag("read_receipt_mini_avatar")
                            )
                        }
                    }
                }
            }
        }

        // Sticker / Quick Emoji Tray
        AnimatedVisibility(visible = showStickerTray && !isBlocked) {
            Surface(
                color = Color(0xFFF7F5FB),
                border = BorderStroke(1.dp, DmHairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    STICKER_EMOJIS.forEach { emoji ->
                        Surface(
                            onClick = {
                                onSendMessage(emoji)
                                showStickerTray = false
                            },
                            shape = CircleShape,
                            color = Color.White,
                            border = BorderStroke(1.dp, DmHairlineBorder)
                        ) {
                            Text(
                                text = emoji,
                                fontSize = 20.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = DmHairlineBorder)

        // Bottom Composer (Blocked bar / Voice Recording Bar / Screenshot 2 Messenger Composer)
        if (isBlocked) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Block, contentDescription = null, tint = ChatboxErrorInk)
                    Text(
                        text = "You blocked $shownName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DmInkDark
                    )
                }
                OutlinedButton(
                    onClick = onUnblock,
                    modifier = Modifier.testTag("thread_unblock_button")
                ) {
                    Text("Unblock")
                }
            }
        } else if (isRecording) {
            VoiceRecordingBar(
                elapsedMs = recordingMs,
                onStop = {
                    val (file, dur) = recorder.stop()
                    isRecording = false
                    if (file != null && dur >= 350L) {
                        onSendVoice(file, dur)
                    } else {
                        runCatching { file?.delete() }
                    }
                },
                onCancel = {
                    val (file, _) = recorder.stop()
                    isRecording = false
                    runCatching { file?.delete() }
                }
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("chatbox_composer_gallery")
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = "Send photo", tint = DmLavenderAccent)
                }
                IconButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) startRecording()
                        else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.testTag("chatbox_composer_mic")
                ) {
                    Icon(Icons.Rounded.Mic, contentDescription = "Record voice note", tint = DmLavenderAccent)
                }

                // Pill "Message" input with Sticker/Emoji icon inside
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF0F2F5),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (draft.isEmpty()) {
                                Text(
                                    text = "Message",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = DmInkMuted
                                )
                            }
                            BasicTextField(
                                value = draft,
                                onValueChange = { draft = it.take(ChatboxConstants.MAX_MESSAGE_BODY_LENGTH) },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = DmInkDark),
                                cursorBrush = SolidColor(DmLavenderAccent),
                                maxLines = 4,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("chatbox_message_input")
                            )
                        }
                        IconButton(
                            onClick = { showStickerTray = !showStickerTray },
                            modifier = Modifier.testTag("chatbox_sticker_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SentimentSatisfiedAlt,
                                contentDescription = "Stickers and emoji",
                                tint = DmLavenderAccent
                            )
                        }
                    }
                }

                // Right action: Send when typing, Quick Reaction when empty
                if (draft.isNotBlank()) {
                    IconButton(
                        onClick = {
                            val text = draft.trim()
                            if (text.isNotEmpty()) {
                                onSendMessage(text)
                                draft = ""
                            }
                        },
                        modifier = Modifier.testTag("chatbox_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send",
                            tint = DmLavenderAccent
                        )
                    }
                } else {
                    IconButton(
                        onClick = { onSendMessage(quickReaction) },
                        modifier = Modifier.testTag("chatbox_quick_reaction_send")
                    ) {
                        Text(text = quickReaction, fontSize = 22.sp)
                    }
                }
            }
        }
    }

    // Photo Lightbox Dialog
    lightboxImagePath?.let { path ->
        Dialog(onDismissRequest = { lightboxImagePath = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(8.dp)) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = "Full size photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp)
                    )
                    IconButton(
                        onClick = { lightboxImagePath = null },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close photo", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerSearchResultCard(
    state: PeerSearchState,
    onOpen: (UserProfile) -> Unit,
    onRequestLogin: () -> Unit
) {
    val (title, subtitle, tag) = when (state) {
        PeerSearchState.Idle, PeerSearchState.Searching ->
            Triple("খোঁজা হচ্ছে…", "Chatbox-এ এই নম্বর/ID খুঁজছি", "chatbox_search_searching")
        is PeerSearchState.Found ->
            Triple(
                state.profile.displayName,
                "Chatbox ID ${state.profile.shortId} · মেসেজ পাঠাতে চাপুন",
                "chatbox_search_add_user_button"
            )
        is PeerSearchState.NotFound ->
            Triple("পাওয়া যায়নি", state.message, "chatbox_search_not_found")
        PeerSearchState.NeedsLogin ->
            Triple(
                "খুঁজতে login করুন",
                "মোবাইল নম্বর দিয়ে login করলে অন্যদের খুঁজে মেসেজ পাঠাতে পারবেন। চাপুন।",
                "chatbox_search_login_required"
            )
        is PeerSearchState.Failed ->
            Triple("খোঁজা যায়নি", state.message, "chatbox_search_failed")
    }
    val clickable = state is PeerSearchState.Found || state is PeerSearchState.NeedsLogin
    val isNegative = state is PeerSearchState.NotFound || state is PeerSearchState.Failed
    Surface(
        onClick = {
            when (state) {
                is PeerSearchState.Found -> onOpen(state.profile)
                PeerSearchState.NeedsLogin -> onRequestLogin()
                else -> Unit
            }
        },
        enabled = clickable,
        shape = RoundedCornerShape(16.dp),
        color = if (isNegative) Color(0xFFFFF4F4) else DmLavenderSoft.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, DmHairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state is PeerSearchState.Found) {
                MessengerAvatar(label = state.profile.displayName, size = 42.dp, showOnlineDot = false)
            } else {
                Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(42.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        if (state is PeerSearchState.Searching || state is PeerSearchState.Idle) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = if (isNegative) Color(0xFFC62828) else DmLavenderAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isNegative) Color(0xFFC62828) else DmInkDark
                )
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = DmInkMuted)
            }
        }
    }
}
