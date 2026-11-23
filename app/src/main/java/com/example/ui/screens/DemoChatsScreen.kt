package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.DemoCatalog
import com.example.data.model.ChatboxConstants
import com.example.data.model.DemoContact
import com.example.data.model.DemoMessage
import com.example.data.model.DemoModal
import com.example.ui.components.ChatboxLogoWithUpdateBadge
import com.example.ui.components.ChatboxSearchField
import com.example.ui.components.DemoAvatar
import com.example.ui.components.NoticeBanner
import com.example.ui.theme.ChatboxPrimary
import com.example.ui.theme.ChatboxSoftLavender
import com.example.ui.theme.LocalChatboxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DemoChatsScreen(
    publicWelcomeBanner: String,
    userDisplayName: String?,
    userEmail: String?,
    isOnline: Boolean,
    appUpdateActive: Boolean,
    messagesByContact: Map<String, List<DemoMessage>>,
    onSendDemoMessage: (contactId: String, body: String, timeStr: String) -> Unit,
    onAppUpdateClick: () -> Unit,
    onSignOut: () -> Unit,
    onOpenCalls: () -> Unit
) {
    val colors = LocalChatboxColors.current
    var selectedContactId by remember { mutableStateOf("saira") }
    var searchQuery by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var showMobileThread by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var activeModal by remember { mutableStateOf<DemoModal?>(null) }

    val selectedContact = remember(selectedContactId) {
        DemoCatalog.contacts.firstOrNull { it.id == selectedContactId } ?: DemoCatalog.contacts.first()
    }

    val filteredContacts = remember(searchQuery, messagesByContact) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) DemoCatalog.contacts
        else DemoCatalog.contacts.filter { contact ->
            val msgs = messagesByContact[contact.id].orEmpty()
            contact.name.lowercase().contains(q) ||
                contact.role.lowercase().contains(q) ||
                msgs.any { it.text.lowercase().contains(q) }
        }
    }

    val activeMessages = remember(messagesByContact, selectedContact.id) {
        messagesByContact[selectedContact.id].orEmpty()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
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
                DemoListPane(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight(),
                    publicWelcomeBanner = publicWelcomeBanner,
                    userDisplayName = userDisplayName,
                    userEmail = userEmail,
                    isOnline = isOnline,
                    appUpdateActive = appUpdateActive,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    filteredContacts = filteredContacts,
                    selectedContactId = selectedContact.id,
                    messagesByContact = messagesByContact,
                    onSelectContact = { id -> selectedContactId = id },
                    onNewChatClick = { activeModal = DemoModal.NEW_CHAT },
                    onAboutClick = { activeModal = DemoModal.ABOUT },
                    onAppUpdateClick = onAppUpdateClick,
                    onSignOut = onSignOut
                )
                HorizontalDivider(modifier = Modifier.width(1.dp).fillMaxHeight(), color = colors.border)
                DemoThreadPane(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    selectedContact = selectedContact,
                    messages = activeMessages,
                    draft = draft,
                    onDraftChange = { draft = it },
                    showBack = false,
                    onBack = {},
                    onCallClick = { activeModal = DemoModal.CALL },
                    onVideoClick = { activeModal = DemoModal.VIDEO },
                    onAttachClick = { activeModal = DemoModal.ATTACH },
                    onToggleDetails = { showDetails = !showDetails },
                    onSend = {
                        val t = draft.trim()
                        if (t.isNotEmpty()) {
                            val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                            onSendDemoMessage(selectedContact.id, t, now)
                            draft = ""
                        }
                    }
                )
                if (showDetails) {
                    DemoDetailsPane(
                        selectedContact = selectedContact,
                        onSelectSaira = {
                            selectedContactId = "saira"
                            showDetails = false
                        }
                    )
                }
            }
        } else {
            if (!showMobileThread) {
                DemoListPane(
                    modifier = Modifier.fillMaxSize(),
                    publicWelcomeBanner = publicWelcomeBanner,
                    userDisplayName = userDisplayName,
                    userEmail = userEmail,
                    isOnline = isOnline,
                    appUpdateActive = appUpdateActive,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    filteredContacts = filteredContacts,
                    selectedContactId = selectedContact.id,
                    messagesByContact = messagesByContact,
                    onSelectContact = { id ->
                        selectedContactId = id
                        showMobileThread = true
                    },
                    onNewChatClick = { activeModal = DemoModal.NEW_CHAT },
                    onAboutClick = { activeModal = DemoModal.ABOUT },
                    onAppUpdateClick = onAppUpdateClick,
                    onSignOut = onSignOut
                )
            } else {
                DemoThreadPane(
                    modifier = Modifier.fillMaxSize(),
                    selectedContact = selectedContact,
                    messages = activeMessages,
                    draft = draft,
                    onDraftChange = { draft = it },
                    showBack = true,
                    onBack = { showMobileThread = false },
                    onCallClick = { activeModal = DemoModal.CALL },
                    onVideoClick = { activeModal = DemoModal.VIDEO },
                    onAttachClick = { activeModal = DemoModal.ATTACH },
                    onToggleDetails = { showDetails = !showDetails },
                    onSend = {
                        val t = draft.trim()
                        if (t.isNotEmpty()) {
                            val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                            onSendDemoMessage(selectedContact.id, t, now)
                            draft = ""
                        }
                    }
                )
            }
        }
    }

    activeModal?.let { modal ->
        DemoInfoModalDialog(
            modal = modal,
            contactName = selectedContact.name,
            onDismiss = { activeModal = null },
            onOpenCalls = {
                activeModal = null
                onOpenCalls()
            }
        )
    }
}

@Composable
private fun DemoListPane(
    modifier: Modifier,
    publicWelcomeBanner: String,
    userDisplayName: String?,
    userEmail: String?,
    isOnline: Boolean,
    appUpdateActive: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    filteredContacts: List<DemoContact>,
    selectedContactId: String,
    messagesByContact: Map<String, List<DemoMessage>>,
    onSelectContact: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onAboutClick: () -> Unit,
    onAppUpdateClick: () -> Unit,
    onSignOut: () -> Unit
) {
    val colors = LocalChatboxColors.current
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
                            text = "DEMO EXPLORER",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkSubtle
                        )
                        Text(
                            text = "Demo Chats",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink
                        )
                    }
                }
                Row {
                    IconButton(onClick = onNewChatClick, modifier = Modifier.testTag("demo_new_chat_button")) {
                        Icon(Icons.Rounded.Add, contentDescription = "New conversation", tint = ChatboxPrimary)
                    }
                    IconButton(onClick = onAboutClick, modifier = Modifier.testTag("demo_about_button")) {
                        Icon(Icons.Rounded.HelpOutline, contentDescription = "About Chatbox", tint = colors.inkSubtle)
                    }
                }
            }

            ChatboxSearchField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = "Search demo chats…",
                testTag = "demo_search_input"
            )

            if (publicWelcomeBanner.isNotBlank()) {
                NoticeBanner(text = publicWelcomeBanner)
            }
            if (!isOnline) {
                NoticeBanner(text = "Offline · Demo chats stay saved locally on device.", isError = true)
            }
        }

        HorizontalDivider(color = colors.border)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (filteredContacts.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No conversations match \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.ink
                        )
                        TextButton(onClick = { onSearchChange("") }) {
                            Text("Clear search")
                        }
                    }
                }
            } else {
                items(filteredContacts, key = { it.id }) { contact ->
                    val msgs = messagesByContact[contact.id].orEmpty()
                    val preview = msgs.lastOrNull()
                    val selected = contact.id == selectedContactId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) colors.selectedBg else Color.Transparent)
                            .clickable { onSelectContact(contact.id) }
                            .padding(12.dp)
                            .testTag("demo_contact_${contact.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DemoAvatar(label = contact.avatar, style = contact.style, online = contact.online)
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = contact.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                contact.label?.let { badge ->
                                    Surface(
                                        color = ChatboxSoftLavender,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ChatboxPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = preview?.let {
                                    if (it.from == "me") "You: ${it.text}" else it.text
                                } ?: contact.role,
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

        // Account footer
        HorizontalDivider(color = colors.border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.fieldBg)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userDisplayName ?: "Chatbox User",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!userEmail.isNullOrBlank()) {
                    Text(
                        text = userEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkSubtle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            OutlinedButton(
                onClick = onSignOut,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("demo_sign_out_button")
            ) {
                Icon(Icons.Rounded.Logout, contentDescription = "Sign out", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Sign out")
            }
        }
    }
}

@Composable
private fun DemoThreadPane(
    modifier: Modifier,
    selectedContact: DemoContact,
    messages: List<DemoMessage>,
    draft: String,
    onDraftChange: (String) -> Unit,
    showBack: Boolean,
    onBack: () -> Unit,
    onCallClick: () -> Unit,
    onVideoClick: () -> Unit,
    onAttachClick: () -> Unit,
    onToggleDetails: () -> Unit,
    onSend: () -> Unit
) {
    val colors = LocalChatboxColors.current
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, selectedContact.id) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    Column(modifier = modifier.background(colors.background)) {
        // Thread Header
        Surface(color = colors.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showBack) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("demo_thread_back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = ChatboxPrimary)
                    }
                }
                DemoAvatar(
                    label = selectedContact.avatar,
                    style = selectedContact.style,
                    online = selectedContact.online,
                    size = 38.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedContact.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = selectedContact.role,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkSubtle,
                        maxLines = 1
                    )
                }
                IconButton(onClick = onCallClick) {
                    Icon(Icons.Rounded.Phone, contentDescription = "Voice call", tint = ChatboxPrimary)
                }
                IconButton(onClick = onVideoClick) {
                    Icon(Icons.Rounded.Videocam, contentDescription = "Video call", tint = ChatboxPrimary)
                }
                IconButton(onClick = onToggleDetails) {
                    Icon(Icons.Rounded.Info, contentDescription = "Conversation details", tint = ChatboxPrimary)
                }
            }
        }
        HorizontalDivider(color = colors.border)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                NoticeBanner(
                    text = "Local Demo preview — messages stay saved in DataStore. Use Live chats for real-time Firestore messaging."
                )
            }
            items(messages) { msg ->
                val isMe = msg.from == "me"
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
                        modifier = Modifier.widthIn(max = 310.dp)
                    ) {
                        Text(
                            text = msg.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isMe) colors.outgoingText else colors.incomingText,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                    Text(
                        text = msg.time,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkSubtle,
                        modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = colors.border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(onClick = onAttachClick) {
                Icon(Icons.Rounded.AttachFile, contentDescription = "Attach", tint = ChatboxPrimary)
            }
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text("Message ${selectedContact.name}…") },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier
                    .weight(1f)
                    .testTag("demo_message_input")
            )
            IconButton(onClick = { onDraftChange(draft + " 😊") }) {
                Icon(Icons.Rounded.SentimentSatisfiedAlt, contentDescription = "Add smile", tint = ChatboxPrimary)
            }
            Surface(
                onClick = onSend,
                enabled = draft.isNotBlank(),
                shape = CircleShape,
                color = if (draft.isNotBlank()) ChatboxPrimary else ChatboxSoftLavender,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("demo_send_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = "Send",
                        tint = if (draft.isNotBlank()) Color.White else colors.inkSubtle
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoDetailsPane(
    selectedContact: DemoContact,
    onSelectSaira: () -> Unit
) {
    val colors = LocalChatboxColors.current
    HorizontalDivider(modifier = Modifier.width(1.dp).fillMaxHeight(), color = colors.border)
    Column(
        modifier = Modifier
            .width(260.dp)
            .fillMaxHeight()
            .background(colors.surface)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DemoAvatar(
            label = selectedContact.avatar,
            style = selectedContact.style,
            online = selectedContact.online,
            size = 64.dp
        )
        Text(text = selectedContact.name, style = MaterialTheme.typography.titleMedium, color = colors.ink)
        Text(text = selectedContact.role, style = MaterialTheme.typography.bodySmall, color = colors.inkSubtle)
        HorizontalDivider(color = colors.border)
        Text(
            text = "This is a local Demo workspace. Switch to Saira for an offline guide or open Live chats to message friends in real time.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkMuted
        )
        if (selectedContact.id != "saira") {
            Button(onClick = onSelectSaira, modifier = Modifier.fillMaxWidth()) {
                Text("Say hello to Saira")
            }
        }
    }
}

@Composable
private fun DemoInfoModalDialog(
    modal: DemoModal,
    contactName: String,
    onDismiss: () -> Unit,
    onOpenCalls: () -> Unit
) {
    val (title, body) = when (modal) {
        DemoModal.CALL -> "Voice call with $contactName" to
            "Voice calling preview is ready in the Calls tab. Live calling signaling is not connected in this build."
        DemoModal.VIDEO -> "Video call with $contactName" to
            "Video calling preview is ready in the Calls tab. Use Live chats for real-time messaging, images, and voice notes."
        DemoModal.ATTACH -> "Demo attachments" to
            "Demo chats save local text messages on this device. Open Live chats to share compressed images and recorded voice notes."
        DemoModal.NEW_CHAT -> "Start a live conversation" to
            "Demo contacts are fixed sample threads. Open the Live tab and enter a friend's 6-digit Chatbox ID or phone number to start chatting in real time."
        DemoModal.ABOUT -> "About Chatbox v${ChatboxConstants.APP_VERSION}" to
            "Chatbox — WhatsApp-style 6-digit ID profiles, Live Firestore messaging, Meta AI-inspired Saira AI, and Admin Dashboard."
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFECE9F3))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = body, style = MaterialTheme.typography.bodyMedium, color = LocalChatboxColors.current.inkMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (modal == DemoModal.CALL || modal == DemoModal.VIDEO) {
                        Button(onClick = onOpenCalls) {
                            Text("Open Calls tab")
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
