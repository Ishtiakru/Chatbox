package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DemoContact
import com.example.data.model.DemoMessage
import com.example.ui.components.ConversationSearchBar
import com.example.ui.components.DemoAvatar
import com.example.ui.components.DemoInfoModalDialog
import com.example.ui.components.MessengerAvatar
import com.example.ui.components.highlightSearchMatch
import com.example.ui.theme.ChatboxIncomingBubble
import com.example.ui.theme.ChatboxIncomingText
import com.example.ui.theme.ChatboxOnlineDot
import com.example.ui.theme.ChatboxOutgoingBubble
import com.example.ui.theme.ChatboxOutgoingText
import com.example.ui.viewmodel.DemoModal
import com.example.ui.viewmodel.DemoUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoChatsScreen(
    uiState: DemoUiState,
    publicWelcomeBanner: String,
    userDisplayName: String?,
    userEmail: String?,
    isSignedIn: Boolean,
    authBusy: Boolean,
    onSelectContact: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onDraftChange: (String) -> Unit,
    onAppendEmoji: () -> Unit,
    onSendMessage: () -> Unit,
    onShowModal: (DemoModal?) -> Unit,
    onAccountAction: () -> Unit
) {
    var showingMobileThread by rememberSaveable { mutableStateOf(false) }
    var detailsSheetOpen by rememberSaveable { mutableStateOf(false) }

    uiState.activeModal?.let { modal ->
        DemoInfoModalDialog(modal = modal, onDismiss = { onShowModal(null) })
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTwoPane = maxWidth >= 600.dp
        val isThreePane = maxWidth >= 960.dp

        if (!isTwoPane) {
            BackHandler(enabled = showingMobileThread) {
                showingMobileThread = false
            }
        }

        if (isTwoPane) {
            Row(modifier = Modifier.fillMaxSize()) {
                DemoConversationListPane(
                    uiState = uiState,
                    publicWelcomeBanner = publicWelcomeBanner,
                    userDisplayName = userDisplayName,
                    userEmail = userEmail,
                    isSignedIn = isSignedIn,
                    authBusy = authBusy,
                    onSelectContact = { onSelectContact(it) },
                    onSearchChange = onSearchChange,
                    onNewChat = { onShowModal(DemoModal.NEW_CHAT) },
                    onAccountAction = onAccountAction,
                    modifier = Modifier
                        .width(if (isThreePane) 300.dp else 280.dp)
                        .fillMaxHeight()
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outline)
                DemoThreadPane(
                    uiState = uiState,
                    showBackButton = false,
                    onBack = {},
                    onDraftChange = onDraftChange,
                    onAppendEmoji = onAppendEmoji,
                    onSendMessage = onSendMessage,
                    onVoiceClick = { onShowModal(DemoModal.VOICE) },
                    onVideoClick = { onShowModal(DemoModal.VIDEO) },
                    onAttachClick = { onShowModal(DemoModal.ATTACH) },
                    onInfoClick = {
                        if (isThreePane) onShowModal(DemoModal.ABOUT)
                        else detailsSheetOpen = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                if (isThreePane) {
                    VerticalDivider(color = MaterialTheme.colorScheme.outline)
                    DemoDetailsPane(
                        contact = uiState.activeContact,
                        onTalkToSaira = { onSelectContact("saira") },
                        onAboutClick = { onShowModal(DemoModal.ABOUT) },
                        modifier = Modifier
                            .width(276.dp)
                            .fillMaxHeight()
                    )
                }
            }
        } else {
            if (!showingMobileThread) {
                DemoConversationListPane(
                    uiState = uiState,
                    publicWelcomeBanner = publicWelcomeBanner,
                    userDisplayName = userDisplayName,
                    userEmail = userEmail,
                    isSignedIn = isSignedIn,
                    authBusy = authBusy,
                    onSelectContact = { contactId ->
                        onSelectContact(contactId)
                        showingMobileThread = true
                    },
                    onSearchChange = onSearchChange,
                    onNewChat = { onShowModal(DemoModal.NEW_CHAT) },
                    onAccountAction = onAccountAction,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                DemoThreadPane(
                    uiState = uiState,
                    showBackButton = true,
                    onBack = { showingMobileThread = false },
                    onDraftChange = onDraftChange,
                    onAppendEmoji = onAppendEmoji,
                    onSendMessage = onSendMessage,
                    onVoiceClick = { onShowModal(DemoModal.VOICE) },
                    onVideoClick = { onShowModal(DemoModal.VIDEO) },
                    onAttachClick = { onShowModal(DemoModal.ATTACH) },
                    onInfoClick = { detailsSheetOpen = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (detailsSheetOpen && !isThreePane) {
            ModalBottomSheet(
                onDismissRequest = { detailsSheetOpen = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                DemoDetailsPane(
                    contact = uiState.activeContact,
                    onTalkToSaira = {
                        detailsSheetOpen = false
                        onSelectContact("saira")
                        showingMobileThread = true
                    },
                    onAboutClick = {
                        detailsSheetOpen = false
                        onShowModal(DemoModal.ABOUT)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun DemoConversationListPane(
    uiState: DemoUiState,
    publicWelcomeBanner: String,
    userDisplayName: String?,
    userEmail: String?,
    isSignedIn: Boolean,
    authBusy: Boolean,
    onSelectContact: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onNewChat: () -> Unit,
    onAccountAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contacts = uiState.filteredContacts
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.chatbox_logo),
                    contentDescription = stringResource(id = R.string.cd_chatbox_logo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                )
                Column {
                    Text(
                        text = "YOUR SPACE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Messages ✳",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Surface(
                onClick = onNewChat,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("demo_new_chat_button")
            ) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EditNote,
                        contentDescription = stringResource(id = R.string.cd_new_conversation),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ConversationSearchBar(
            query = uiState.searchQuery,
            onQueryChange = onSearchChange,
            testTag = "demo_search_input"
        )

        if (publicWelcomeBanner.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = publicWelcomeBanner,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (uiState.searchQuery.trim().isNotEmpty()) "MATCHING CHATS" else "YOUR CONVERSATIONS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = contacts.size.toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (contacts.isEmpty()) {
                item {
                    val trimmedQuery = uiState.searchQuery.trim()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = if (trimmedQuery.isNotEmpty()) {
                                "No chats match \"$trimmedQuery\""
                            } else {
                                "No conversations found."
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (trimmedQuery.isNotEmpty()) {
                            Text(
                                text = "Try searching another name or message keyword.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { onSearchChange("") },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("demo_clear_search_empty_button")
                            ) {
                                Text("Clear search")
                            }
                        }
                    }
                }
            } else {
                items(contacts, key = { it.id }) { contact ->
                    DemoContactRow(
                        contact = contact,
                        preview = uiState.previewFor(contact),
                        searchQuery = uiState.searchQuery,
                        selected = contact.id == uiState.activeContactId,
                        onClick = { onSelectContact(contact.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MessengerAvatar(label = userDisplayName ?: "Y", size = 38.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userDisplayName ?: "Your space",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = userEmail ?: "Demo mode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = onAccountAction,
                enabled = !authBusy,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("demo_account_button")
            ) {
                Text(
                    text = when {
                        authBusy -> "Please wait"
                        isSignedIn -> "Sign out"
                        else -> "Sign in"
                    }
                )
            }
        }
    }
}

@Composable
private fun DemoContactRow(
    contact: DemoContact,
    preview: String,
    searchQuery: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    val displayName = if (contact.id == "saira") "${contact.name} ✦" else contact.name

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("demo_contact_${contact.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DemoAvatar(contact = contact, size = 44.dp, showOnlineDot = true)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = highlightSearchMatch(
                        text = displayName,
                        query = searchQuery,
                        highlightColor = highlightColor,
                        highlightBackground = highlightBg
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = highlightSearchMatch(
                        text = preview,
                        query = searchQuery,
                        highlightColor = highlightColor,
                        highlightBackground = highlightBg
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = if (contact.id == "saira") "GUIDE" else "DEMO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun DemoThreadPane(
    uiState: DemoUiState,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onAppendEmoji: () -> Unit,
    onSendMessage: () -> Unit,
    onVoiceClick: () -> Unit,
    onVideoClick: () -> Unit,
    onAttachClick: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contact = uiState.activeContact
    val messages = uiState.messagesFor(contact.id)
    val listState = rememberLazyListState()

    LaunchedEffect(contact.id, messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("demo_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(id = R.string.cd_back)
                    )
                }
            }
            DemoAvatar(contact = contact, size = 38.dp, showOnlineDot = true)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (contact.id == "saira") "${contact.name} ✦" else contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (contact.id == "saira") "Offline app guide" else "Illustrative conversation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onVoiceClick,
                modifier = Modifier.testTag("demo_voice_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription = stringResource(id = R.string.cd_voice_call),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onVideoClick,
                modifier = Modifier.testTag("demo_video_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Videocam,
                    contentDescription = stringResource(id = R.string.cd_video_call),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.testTag("demo_info_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreHoriz,
                    contentDescription = stringResource(id = R.string.cd_conversation_info),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "JUST A LITTLE CLOSER",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (contact.id == "saira") {
                                    "Saira is a simple offline guide here. Use Saira AI for Gemini answers."
                                } else {
                                    "This is a sample conversation. Messages stay on this device."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(messages) { message ->
                DemoMessageBubble(message = message, contact = contact)
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onAttachClick,
                    modifier = Modifier.testTag("demo_attach_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(id = R.string.cd_attach_file)
                    )
                }
                OutlinedTextField(
                    value = uiState.draft,
                    onValueChange = onDraftChange,
                    placeholder = { Text("Write a little something...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSendMessage() }),
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("demo_message_input")
                )
                IconButton(
                    onClick = onAppendEmoji,
                    modifier = Modifier.testTag("demo_emoji_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SentimentSatisfied,
                        contentDescription = stringResource(id = R.string.cd_add_emoji)
                    )
                }
                Surface(
                    onClick = onSendMessage,
                    enabled = uiState.draft.trim().isNotEmpty(),
                    shape = CircleShape,
                    color = if (uiState.draft.trim().isNotEmpty()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("demo_send_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = stringResource(id = R.string.cd_send),
                            tint = if (uiState.draft.trim().isNotEmpty()) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Local demo · not sent to anyone",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Made for meaningful moments ✳",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DemoMessageBubble(
    message: DemoMessage,
    contact: DemoContact
) {
    val isMe = message.from == "me"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMe) {
            DemoAvatar(contact = contact, size = 28.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMe) 16.dp else 4.dp,
                    bottomEnd = if (isMe) 4.dp else 16.dp
                ),
                color = if (isMe) ChatboxOutgoingBubble else ChatboxIncomingBubble
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isMe) ChatboxOutgoingText else ChatboxIncomingText,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = message.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isMe) {
                    Icon(
                        imageVector = Icons.Rounded.DoneAll,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoDetailsPane(
    contact: DemoContact,
    onTalkToSaira: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "A LITTLE MORE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onAboutClick, modifier = Modifier.testTag("demo_details_about_button")) {
                Icon(
                    imageVector = Icons.Rounded.MoreHoriz,
                    contentDescription = stringResource(id = R.string.cd_about)
                )
            }
        }

        DemoAvatar(contact = contact, size = 72.dp, showOnlineDot = true)
        Text(
            text = if (contact.id == "saira") "${contact.name} ✦" else contact.name,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (contact.id == "saira") "Your little guide to this space." else "A little demo, just for you.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(ChatboxOnlineDot)
                )
                Text(
                    text = if (contact.id == "saira") "OFFLINE GUIDE" else "SAMPLE PROFILE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "A NOTE FROM US ✳",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Good conversations make the world feel a little smaller.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "✳ MEET YOUR GUIDE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Hey, I’m Saira ✦",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Curious about this space? I’m here to help you find your way.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onTalkToSaira)
                        .minimumInteractiveComponentSize()
                        .padding(vertical = 4.dp)
                        .testTag("demo_say_hello_saira_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Say hello to Saira",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Rounded.ArrowOutward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "DESIGNED FOR CONNECTION · Made with ♥",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
