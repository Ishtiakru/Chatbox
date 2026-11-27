package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.PhoneMissed
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddIcCall
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import com.example.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ChatboxCallEntry
import com.example.data.model.ChatboxConstants
import com.example.data.model.ChatboxThread
import com.example.data.model.ScheduledCallEntry
import com.example.ui.components.ChatboxLogoWithUpdateBadge
import com.example.ui.components.ChatboxSearchField
import com.example.ui.components.MessengerAvatar
import com.example.ui.components.NoticeBanner
import com.example.ui.theme.ChatboxErrorInk
import com.example.ui.theme.ChatboxPrimary
import com.example.ui.theme.DmHairlineBorder
import com.example.ui.theme.DmInkDark
import com.example.ui.theme.DmInkMuted
import com.example.ui.theme.DmLavenderAccent
import com.example.ui.theme.DmLavenderSoft

@Composable
fun CallsScreen(
    isOnline: Boolean,
    appUpdateActive: Boolean,
    callLogs: List<ChatboxCallEntry> = emptyList(),
    threads: List<ChatboxThread> = emptyList(),
    favoriteThreadIds: Set<String> = emptySet(),
    scheduledCalls: List<ScheduledCallEntry> = emptyList(),
    onOpenThread: (String) -> Unit = {},
    onAddOrFindContact: (String) -> String = { "" },
    onLogCall: (threadId: String, peerName: String, isVideo: Boolean, isMissed: Boolean, durationLabel: String) -> Unit = { _, _, _, _, _ -> },
    // 1.0.0.112: call buttons signal the peer through the global VM state.
    onStartCall: (threadId: String, peerName: String, isVideo: Boolean) -> Unit = { _, _, _ -> },
    onToggleFavorite: (String) -> Unit = {},
    onScheduleCall: (threadId: String, peerName: String, isVideo: Boolean, scheduledForLabel: String) -> Unit = { _, _, _, _ -> },
    onRemoveScheduledCall: (String) -> Unit = {},
    onClearCallLogs: () -> Unit = {},
    onAppUpdateClick: () -> Unit,
    onBackToConversations: () -> Unit
) {
    var searchVisible by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }

    var showCallPickerDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showKeypadDialog by remember { mutableStateOf(false) }
    var showFavoritesDialog by remember { mutableStateOf(false) }

    var addedUserPromptTarget by remember { mutableStateOf<ChatboxThread?>(null) }

    if (searchQuery.isNotEmpty()) {
        BackHandler { searchQuery = "" }
    }

    val trimmedQuery = searchQuery.trim()
    val directThreads = remember(threads) { threads.filter { !it.isGroup } }

    val matchingThreads = remember(directThreads, trimmedQuery) {
        if (trimmedQuery.isEmpty()) emptyList()
        else {
            val digits = trimmedQuery.filter { it.isDigit() }
            directThreads.filter { t ->
                t.name.contains(trimmedQuery, ignoreCase = true) ||
                    t.shortId.contains(trimmedQuery) ||
                    (digits.isNotEmpty() && t.phoneNumber.filter { it.isDigit() }.contains(digits))
            }
        }
    }

    val filteredCallLogs = remember(callLogs, threads, trimmedQuery) {
        if (trimmedQuery.isEmpty()) callLogs
        else {
            val digits = trimmedQuery.filter { it.isDigit() }
            val matchingThreadIds = threads.filter { t ->
                t.name.contains(trimmedQuery, ignoreCase = true) ||
                    t.shortId.contains(trimmedQuery) ||
                    (digits.isNotEmpty() && t.phoneNumber.filter { it.isDigit() }.contains(digits))
            }.map { it.id }.toSet()
            callLogs.filter { log ->
                log.peerName.contains(trimmedQuery, ignoreCase = true) ||
                    log.threadId in matchingThreadIds
            }
        }
    }

    val handleAddFromSearch: () -> Unit = {
        if (trimmedQuery.isNotEmpty()) {
            val createdId = onAddOrFindContact(trimmedQuery)
            if (createdId.isNotBlank()) {
                val resolved = threads.firstOrNull { it.id == createdId }
                    ?: ChatboxThread(
                        id = createdId,
                        name = if (ChatboxConstants.VALID_SHORT_ID_REGEX.matches(trimmedQuery)) {
                            "Chatbox Friend ($trimmedQuery)"
                        } else trimmedQuery,
                        shortId = if (ChatboxConstants.VALID_SHORT_ID_REGEX.matches(trimmedQuery)) trimmedQuery else "",
                        phoneNumber = if (trimmedQuery.count { it.isDigit() } >= 7) trimmedQuery else ""
                    )
                addedUserPromptTarget = resolved
                searchQuery = ""
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .testTag("calls_screen_root")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header matching WhatsApp Calls layout on pure white + lavender icons
            Row(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 6.dp),
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
                        logoSize = 32.dp
                    )
                    Text(
                        text = stringResource(R.string.calls_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = DmInkDark
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            searchVisible = !searchVisible
                            if (!searchVisible) searchQuery = ""
                        },
                        modifier = Modifier.testTag("calls_search_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = if (searchVisible) "Hide search bar" else "Search number or user ID",
                            tint = ChatboxPrimary
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.testTag("calls_more_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More options",
                                tint = ChatboxPrimary
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            containerColor = Color.White
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear call log", color = DmInkDark) },
                                enabled = callLogs.isNotEmpty(),
                                onClick = {
                                    showOverflowMenu = false
                                    onClearCallLogs()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Go to Chatbox", color = DmInkDark) },
                                onClick = {
                                    showOverflowMenu = false
                                    onBackToConversations()
                                }
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search Bar (number or user ID) + Small Add Button beside it
                AnimatedVisibility(visible = searchVisible) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChatboxSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Number or user ID…",
                            testTag = "calls_search_input",
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = handleAddFromSearch,
                            enabled = trimmedQuery.isNotEmpty(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DmLavenderAccent,
                                contentColor = Color.White,
                                disabledContainerColor = DmLavenderSoft,
                                disabledContentColor = ChatboxPrimary.copy(alpha = 0.55f)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("calls_add_user_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add user",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Add",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // 4 Quick Action Circular Buttons (Call, Schedule, Keypad, Favorites)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CallsQuickActionButton(
                        icon = Icons.Rounded.Phone,
                        label = "Call",
                        testTag = "calls_action_call",
                        onClick = { showCallPickerDialog = true }
                    )
                    CallsQuickActionButton(
                        icon = Icons.Rounded.CalendarToday,
                        label = "Schedule",
                        testTag = "calls_action_schedule",
                        onClick = { showScheduleDialog = true }
                    )
                    CallsQuickActionButton(
                        icon = Icons.Rounded.Dialpad,
                        label = "Keypad",
                        testTag = "calls_action_keypad",
                        onClick = { showKeypadDialog = true }
                    )
                    CallsQuickActionButton(
                        icon = if (favoriteThreadIds.isNotEmpty()) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        label = "Favorites",
                        testTag = "calls_action_favorites",
                        onClick = { showFavoritesDialog = true }
                    )
                }

                HorizontalDivider(color = DmHairlineBorder)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isOnline) {
                        NoticeBanner(
                            text = "Offline · Internet is paused, recent device call logs stay saved.",
                            isError = false,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    if (scheduledCalls.isNotEmpty()) {
                        Text(
                            text = "Scheduled",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DmInkDark,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        scheduledCalls.forEach { sched ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, DmHairlineBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("scheduled_call_${sched.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    MessengerAvatar(label = sched.peerName, size = 42.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = sched.peerName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = DmInkDark,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${if (sched.isVideo) "Video" else "Audio"} · ${sched.scheduledForLabel}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = DmInkMuted
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            onStartCall(sched.threadId, sched.peerName, sched.isVideo)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (sched.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Phone,
                                            contentDescription = "Start scheduled call",
                                            tint = ChatboxPrimary
                                        )
                                    }
                                    IconButton(onClick = { onRemoveScheduledCall(sched.id) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Remove scheduled call",
                                            tint = DmInkMuted
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (trimmedQuery.isNotEmpty() && matchingThreads.isNotEmpty()) {
                        Text(
                            text = "Added users",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DmInkDark,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        matchingThreads.forEach { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onOpenThread(contact.id) }
                                    .padding(horizontal = 4.dp, vertical = 8.dp)
                                    .testTag("calls_matching_user_${contact.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                MessengerAvatar(
                                    label = contact.name,
                                    size = 46.dp,
                                    showOnlineDot = contact.online
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DmInkDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sub = contact.phoneNumber.ifBlank {
                                        if (contact.shortId.isNotBlank()) "ID: ${contact.shortId}" else "Added contact"
                                    }
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DmInkMuted
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        onStartCall(contact.id, contact.name, false)
                                    }
                                ) {
                                    Icon(Icons.Rounded.Phone, contentDescription = "Audio call", tint = ChatboxPrimary)
                                }
                                IconButton(
                                    onClick = {
                                        onStartCall(contact.id, contact.name, true)
                                    }
                                ) {
                                    Icon(Icons.Rounded.Videocam, contentDescription = "Video call", tint = ChatboxPrimary)
                                }
                            }
                        }
                    }

                    Text(
                        text = "Recent",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DmInkDark,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    if (filteredCallLogs.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, DmHairlineBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("calls_empty_state")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = DmLavenderSoft,
                                    shape = CircleShape,
                                    modifier = Modifier.size(50.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Phone,
                                            contentDescription = "No calls yet",
                                            tint = ChatboxPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (trimmedQuery.isNotEmpty()) {
                                        "No recent calls matching \"$trimmedQuery\""
                                    } else {
                                        "No recent calls yet"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = DmInkDark,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = if (trimmedQuery.isNotEmpty()) {
                                        "Tap Add beside the search bar to add \"$trimmedQuery\" and start a voice or video call."
                                    } else {
                                        "Search by phone number or 6-digit user ID above and tap Add, or use Keypad to place a call."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DmInkMuted,
                                    textAlign = TextAlign.Center
                                )
                                OutlinedButton(
                                    onClick = onBackToConversations,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, DmHairlineBorder),
                                    modifier = Modifier.testTag("calls_back_to_conversations")
                                ) {
                                    Text("Back to Chatbox", color = ChatboxPrimary)
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = null,
                                        tint = ChatboxPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        filteredCallLogs.forEach { call ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onOpenThread(call.threadId) }
                                    .padding(horizontal = 4.dp, vertical = 8.dp)
                                    .testTag("call_log_${call.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                MessengerAvatar(label = call.peerName, size = 46.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = call.peerName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (call.isMissed) ChatboxErrorInk else DmInkDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                call.isMissed -> Icons.AutoMirrored.Rounded.PhoneMissed
                                                else -> Icons.AutoMirrored.Rounded.CallMade
                                            },
                                            contentDescription = null,
                                            tint = if (call.isMissed) ChatboxErrorInk else ChatboxPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "${call.timeLabel} · ${call.durationLabel}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = DmInkMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        onStartCall(call.threadId, call.peerName, call.isVideo)
                                    },
                                    modifier = Modifier.testTag("call_redial_${call.id}")
                                ) {
                                    Icon(
                                        imageVector = if (call.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Phone,
                                        contentDescription = if (call.isVideo) "Video call" else "Audio call",
                                        tint = ChatboxPrimary
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(
                                onClick = onBackToConversations,
                                modifier = Modifier.testTag("calls_back_to_conversations")
                            ) {
                                Text("Back to Chatbox", color = ChatboxPrimary)
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = ChatboxPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom-Right Lavender New Call Floating Action Button (matching screenshot)
        FloatingActionButton(
            onClick = { showCallPickerDialog = true },
            containerColor = DmLavenderAccent,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("calls_new_call_fab")
        ) {
            Icon(
                imageVector = Icons.Rounded.AddIcCall,
                contentDescription = "New call"
            )
        }
    }

    addedUserPromptTarget?.let { target ->
        CallsAddedUserDialog(
            thread = target,
            isFavorite = target.id in favoriteThreadIds,
            onToggleFavorite = { onToggleFavorite(target.id) },
            onStartAudioCall = {
                addedUserPromptTarget = null
                onStartCall(target.id, target.name, false)
            },
            onStartVideoCall = {
                addedUserPromptTarget = null
                onStartCall(target.id, target.name, true)
            },
            onOpenChat = {
                addedUserPromptTarget = null
                onOpenThread(target.id)
            },
            onDismiss = { addedUserPromptTarget = null }
        )
    }

    if (showCallPickerDialog) {
        CallsPickerDialog(
            threads = threads,
            favoriteThreadIds = favoriteThreadIds,
            onAddOrFindContact = onAddOrFindContact,
            onStartCall = { tid, peerName, isVideo ->
                showCallPickerDialog = false
                onStartCall(tid, peerName, isVideo)
            },
            onToggleFavorite = onToggleFavorite,
            onDismiss = { showCallPickerDialog = false }
        )
    }

    if (showKeypadDialog) {
        CallsKeypadDialog(
            onAddOrFindContact = onAddOrFindContact,
            onStartCall = { tid, peerName, isVideo ->
                showKeypadDialog = false
                onStartCall(tid, peerName, isVideo)
            },
            onDismiss = { showKeypadDialog = false }
        )
    }

    if (showScheduleDialog) {
        CallsScheduleDialog(
            threads = threads,
            onAddOrFindContact = onAddOrFindContact,
            onSchedule = onScheduleCall,
            onDismiss = { showScheduleDialog = false }
        )
    }

    if (showFavoritesDialog) {
        CallsFavoritesDialog(
            threads = threads,
            favoriteThreadIds = favoriteThreadIds,
            onToggleFavorite = onToggleFavorite,
            onAddAndFavorite = onAddOrFindContact,
            onStartCall = { tid, peerName, isVideo ->
                showFavoritesDialog = false
                onStartCall(tid, peerName, isVideo)
            },
            onDismiss = { showFavoritesDialog = false }
        )
    }

}

@Composable
private fun CallsQuickActionButton(
    icon: ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.widthIn(min = 68.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = DmLavenderSoft,
            modifier = Modifier
                .size(56.dp)
                .testTag(testTag)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = ChatboxPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = DmInkDark,
            maxLines = 1
        )
    }
}
