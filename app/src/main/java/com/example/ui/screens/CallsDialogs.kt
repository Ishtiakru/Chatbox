package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.example.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.example.data.model.ChatboxConstants
import com.example.data.model.ChatboxThread
import com.example.ui.components.ChatboxSearchField
import com.example.ui.components.MessengerAvatar
import com.example.ui.theme.ChatboxPrimary
import com.example.ui.theme.DmHairlineBorder
import com.example.ui.theme.DmInkDark
import com.example.ui.theme.DmInkMuted
import com.example.ui.theme.DmLavenderAccent
import com.example.ui.theme.DmLavenderSoft
import kotlinx.coroutines.delay
import java.util.Locale

internal data class ActiveCallTarget(
    val threadId: String,
    val peerName: String,
    val isVideo: Boolean
)

@Composable
internal fun CallsActiveCallDialog(
    target: ActiveCallTarget,
    /** false = still ringing; true = callee accepted, timer runs from [connectedAtMs]. */
    isConnected: Boolean,
    connectedAtMs: Long,
    /** Real-time media session; null = signaling-only dialog (local threads / tests). */
    engine: com.example.call.CallEngine? = null,
    onMarkMissed: () -> Unit,
    onEndCall: (durationLabel: String) -> Unit,
    onDismiss: () -> Unit
) {
    // Tick every second: drives the Connected timer and the End-button duration.
    var tick by remember(target.threadId, connectedAtMs) { mutableIntStateOf(0) }
    LaunchedEffect(target.threadId, connectedAtMs) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    // Read tick during composition so every tick recomposes the dialog.
    val now = remember(tick) { System.currentTimeMillis() }
    val statusText = if (!isConnected) {
        "Ringing…"
    } else {
        val totalSec = ((now - connectedAtMs) / 1000L)
            .toInt().coerceAtLeast(0)
        val mins = totalSec / 60
        val secs = totalSec % 60
        val timer = String.format(Locale.US, "Connected · %d:%02d", mins, secs)
        if (engine != null) {
            val media by engine.mediaState.collectAsState()
            mediaStatusLabel(media, timer)
        } else timer
    }
    val endNow = {
        val totalSec = if (isConnected) {
            ((System.currentTimeMillis() - connectedAtMs) / 1000L).toInt().coerceAtLeast(1)
        } else 1
        onEndCall(String.format(Locale.US, "%d:%02d", totalSec / 60, totalSec % 60))
    }

    if (engine != null && target.isVideo) {
        InCallVideoScreen(
            engine = engine,
            peerName = target.peerName,
            statusText = if (!isConnected) "Ringing…" else statusText,
            onEnd = endNow,
            onDismiss = {}
        )
        return
    }

    // A stray touch outside the card (cheek, finger) must never end the call — only the red button does.
    // Only the red button ends a call — never back, an edge swipe or a touch outside the card.
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false)
    ) {
        if (engine != null) KeepScreenOn()
        // Phone held to the ear → controls ignore touches (and the screen turns off where allowed),
        // so the cheek can't press "End".
        val nearEar = if (engine != null && !target.isVideo) rememberNearEar() else false
        // Taking the phone away from the ear: the cheek / fingers often brush the screen — ignore "End"
        // for a moment (also right after the screen opens).
        var endGuardUntil by remember { mutableLongStateOf(System.currentTimeMillis() + 800) }
        LaunchedEffect(nearEar) {
            if (!nearEar) endGuardUntil = maxOf(endGuardUntil, System.currentTimeMillis() + 1_500)
        }
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1E1930),
            modifier = Modifier
                .fillMaxWidth()
                .singleTouchOnly()
                .testTag("calls_active_call_dialog")
        ) {
            Column(
                modifier = Modifier
                    .pointerInput(nearEar) {
                        if (nearEar) awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        }
                    }
                    .drawWithContent {
                        drawContent()
                        if (nearEar) drawRect(Color.Black)
                    }
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MessengerAvatar(
                    label = target.peerName,
                    size = 84.dp,
                    showOnlineDot = true
                )
                Text(
                    text = target.peerName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${if (target.isVideo) "Video call" else "Audio call"} · $statusText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
                if (engine != null) {
                    Box(modifier = Modifier.padding(top = 12.dp)) {
                        CallControlsRow(
                            engine = engine, showVideoControls = false, onEnd = endNow,
                            endBlocked = { nearEar || System.currentTimeMillis() < endGuardUntil }
                        )
                    }
                } else Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onMarkMissed,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Mark missed")
                    }
                    Surface(
                        onClick = {
                            val totalSec = if (isConnected) {
                                ((System.currentTimeMillis() - connectedAtMs) / 1000L)
                                    .toInt().coerceAtLeast(1)
                            } else {
                                1
                            }
                            val dur = String.format(Locale.US, "%d:%02d", totalSec / 60, totalSec % 60)
                            onEndCall(dur)
                        },
                        shape = CircleShape,
                        color = Color(0xFFE53935),
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("calls_dialog_end")
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

@Composable
internal fun CallsAddedUserDialog(
    thread: ChatboxThread,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onStartAudioCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onOpenChat: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calls_added_user_dialog"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MessengerAvatar(label = thread.name, size = 64.dp, showOnlineDot = true)
                Text(
                    text = thread.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DmInkDark
                )
                val subtitle = buildString {
                    if (thread.shortId.isNotBlank()) append("ID: ${thread.shortId}")
                    if (thread.phoneNumber.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(thread.phoneNumber)
                    }
                    if (isEmpty()) append("Added to your contacts")
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = DmInkMuted
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onStartAudioCall,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DmLavenderAccent),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("calls_added_audio_button")
                    ) {
                        Icon(Icons.Rounded.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Audio")
                    }
                    Button(
                        onClick = onStartVideoCall,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ChatboxPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("calls_added_video_button")
                    ) {
                        Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Video")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenChat,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Rounded.ChatBubbleOutline,
                            contentDescription = null,
                            tint = ChatboxPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Message", color = ChatboxPrimary)
                    }
                    OutlinedButton(
                        onClick = onToggleFavorite,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                            tint = ChatboxPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (isFavorite) "Favorited" else "Favorite", color = ChatboxPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = ChatboxPrimary)
            }
        }
    )
}

@Composable
internal fun CallsPickerDialog(
    threads: List<ChatboxThread>,
    favoriteThreadIds: Set<String>,
    onAddOrFindContact: (String) -> String,
    onStartCall: (threadId: String, peerName: String, isVideo: Boolean) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    val contacts = remember(threads, trimmed) {
        val direct = threads.filter { !it.isGroup }
        if (trimmed.isEmpty()) direct
        else direct.filter { t ->
            t.name.contains(trimmed, ignoreCase = true) ||
                t.shortId.contains(trimmed) ||
                t.phoneNumber.contains(trimmed)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("calls_picker_dialog")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Start a call",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DmInkDark
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = ChatboxPrimary)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChatboxSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Number or 6-digit ID…",
                        testTag = "calls_picker_input",
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (trimmed.isNotEmpty()) {
                                val id = onAddOrFindContact(trimmed)
                                if (id.isNotBlank()) {
                                    val label = if (ChatboxConstants.VALID_SHORT_ID_REGEX.matches(trimmed)) {
                                        "Chatbox Friend ($trimmed)"
                                    } else trimmed
                                    onStartCall(id, label, false)
                                }
                            }
                        },
                        enabled = trimmed.isNotEmpty(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DmLavenderAccent),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Rounded.Phone, contentDescription = "Call", modifier = Modifier.size(18.dp))
                    }
                }

                if (contacts.isEmpty()) {
                    Text(
                        text = if (trimmed.isEmpty()) {
                            "No added users yet. Enter a phone number or 6-digit user ID above to add & call."
                        } else {
                            "Tap the phone button to add & call \"$trimmed\"."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = DmInkMuted,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(contacts, key = { it.id }) { contact ->
                            ContactCallPickerRow(
                                contact = contact,
                                isFavorite = contact.id in favoriteThreadIds,
                                onToggleFavorite = { onToggleFavorite(contact.id) },
                                onAudioCall = { onStartCall(contact.id, contact.name, false) },
                                onVideoCall = { onStartCall(contact.id, contact.name, true) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CallsKeypadDialog(
    onAddOrFindContact: (String) -> String,
    onStartCall: (threadId: String, peerName: String, isVideo: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var dialInput by remember { mutableStateOf("") }
    val keypadRows = remember {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("*", "0", "#")
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("calls_keypad_dialog")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Keypad",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DmInkDark
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = ChatboxPrimary)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DmLavenderSoft,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = dialInput.ifEmpty { "Number or 6-digit ID" },
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (dialInput.isEmpty()) 14.sp else 20.sp,
                            color = if (dialInput.isEmpty()) DmInkMuted else DmInkDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("calls_keypad_display")
                        )
                        if (dialInput.isNotEmpty()) {
                            IconButton(
                                onClick = { dialInput = dialInput.dropLast(1) },
                                modifier = Modifier.testTag("calls_keypad_backspace")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Backspace,
                                    contentDescription = "Backspace",
                                    tint = ChatboxPrimary
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    keypadRows.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row.forEach { digit ->
                                Surface(
                                    onClick = { if (dialInput.length < 18) dialInput += digit },
                                    shape = CircleShape,
                                    color = DmLavenderSoft,
                                    modifier = Modifier
                                        .size(58.dp)
                                        .testTag("keypad_key_$digit")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = digit,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = DmInkDark
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        enabled = dialInput.isNotBlank(),
                        onClick = {
                            onAddOrFindContact(dialInput)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = ChatboxPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add", color = ChatboxPrimary)
                    }
                    Button(
                        enabled = dialInput.isNotBlank(),
                        onClick = {
                            val tid = onAddOrFindContact(dialInput)
                            if (tid.isNotBlank()) {
                                val label = if (ChatboxConstants.VALID_SHORT_ID_REGEX.matches(dialInput)) {
                                    "Chatbox Friend ($dialInput)"
                                } else dialInput
                                onStartCall(tid, label, false)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DmLavenderAccent),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("calls_keypad_audio_call")
                    ) {
                        Icon(Icons.Rounded.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Call")
                    }
                    Button(
                        enabled = dialInput.isNotBlank(),
                        onClick = {
                            val tid = onAddOrFindContact(dialInput)
                            if (tid.isNotBlank()) {
                                val label = if (ChatboxConstants.VALID_SHORT_ID_REGEX.matches(dialInput)) {
                                    "Chatbox Friend ($dialInput)"
                                } else dialInput
                                onStartCall(tid, label, true)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ChatboxPrimary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Video")
                    }
                }
            }
        }
    }
}

@Composable
internal fun CallsScheduleDialog(
    threads: List<ChatboxThread>,
    onAddOrFindContact: (String) -> String,
    onSchedule: (threadId: String, peerName: String, isVideo: Boolean, scheduledForLabel: String) -> Unit,
    onDismiss: () -> Unit
) {
    val directContacts = remember(threads) { threads.filter { !it.isGroup } }
    var targetInput by remember { mutableStateOf(directContacts.firstOrNull()?.name.orEmpty()) }
    var selectedThreadId by remember { mutableStateOf(directContacts.firstOrNull()?.id) }
    var isVideo by remember { mutableStateOf(false) }
    var timeLabel by remember { mutableStateOf("Today, 8:00 PM") }
    val presets = remember { listOf("Today, 8:00 PM", "Tomorrow, 10:00 AM", "Weekend, 5:00 PM") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = { Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = ChatboxPrimary) },
        title = { Text("Schedule call", color = DmInkDark) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calls_schedule_dialog"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (directContacts.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        directContacts.forEach { contact ->
                            FilterChip(
                                selected = selectedThreadId == contact.id,
                                onClick = {
                                    selectedThreadId = contact.id
                                    targetInput = contact.name
                                },
                                label = { Text(contact.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DmLavenderSoft,
                                    selectedLabelColor = ChatboxPrimary
                                )
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = targetInput,
                    onValueChange = {
                        targetInput = it
                        selectedThreadId = null
                    },
                    label = { Text("Name, number or 6-digit ID") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calls_schedule_target_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isVideo,
                        onClick = { isVideo = false },
                        label = { Text("Audio") },
                        leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DmLavenderSoft,
                            selectedLabelColor = ChatboxPrimary
                        )
                    )
                    FilterChip(
                        selected = isVideo,
                        onClick = { isVideo = true },
                        label = { Text("Video") },
                        leadingIcon = { Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DmLavenderSoft,
                            selectedLabelColor = ChatboxPrimary
                        )
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = timeLabel == preset,
                            onClick = { timeLabel = preset },
                            label = { Text(preset) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DmLavenderSoft,
                                selectedLabelColor = ChatboxPrimary
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = timeLabel,
                    onValueChange = { timeLabel = it.take(40) },
                    label = { Text("Time") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = targetInput.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = DmLavenderAccent),
                onClick = {
                    val clean = targetInput.trim()
                    val tid = selectedThreadId ?: onAddOrFindContact(clean)
                    if (tid.isNotBlank()) {
                        val resolvedName = directContacts.firstOrNull { it.id == tid }?.name
                            ?: if (ChatboxConstants.VALID_SHORT_ID_REGEX.matches(clean)) "Chatbox Friend ($clean)" else clean
                        onSchedule(tid, resolvedName, isVideo, timeLabel.ifBlank { "Today, 8:00 PM" })
                    }
                    onDismiss()
                },
                modifier = Modifier.testTag("calls_schedule_confirm_button")
            ) {
                Text("Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = ChatboxPrimary) }
        }
    )
}

@Composable
internal fun CallsFavoritesDialog(
    threads: List<ChatboxThread>,
    favoriteThreadIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onAddAndFavorite: (String) -> String,
    onStartCall: (threadId: String, peerName: String, isVideo: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var quickAddInput by remember { mutableStateOf("") }
    val direct = remember(threads) { threads.filter { !it.isGroup } }
    val favorites = remember(direct, favoriteThreadIds) { direct.filter { it.id in favoriteThreadIds } }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("calls_favorites_dialog")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Favorites",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DmInkDark
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = ChatboxPrimary)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChatboxSearchField(
                        value = quickAddInput,
                        onValueChange = { quickAddInput = it },
                        placeholder = "Number or 6-digit ID…",
                        testTag = "calls_favorites_add_input",
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        enabled = quickAddInput.trim().isNotEmpty(),
                        onClick = {
                            val tid = onAddAndFavorite(quickAddInput.trim())
                            if (tid.isNotBlank() && tid !in favoriteThreadIds) {
                                onToggleFavorite(tid)
                            }
                            quickAddInput = ""
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DmLavenderAccent),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }

                if (direct.isEmpty()) {
                    Text(
                        text = "No added users yet. Add a friend by number or 6-digit ID above to mark them as Favorite.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DmInkMuted
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val ordered = favorites + direct.filterNot { it.id in favoriteThreadIds }
                        items(ordered, key = { it.id }) { contact ->
                            ContactCallPickerRow(
                                contact = contact,
                                isFavorite = contact.id in favoriteThreadIds,
                                onToggleFavorite = { onToggleFavorite(contact.id) },
                                onAudioCall = { onStartCall(contact.id, contact.name, false) },
                                onVideoCall = { onStartCall(contact.id, contact.name, true) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactCallPickerRow(
    contact: ChatboxThread,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, DmHairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onAudioCall)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MessengerAvatar(label = contact.name, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DmInkDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = contact.phoneNumber.ifBlank {
                    if (contact.shortId.isNotBlank()) "ID: ${contact.shortId}" else "Chatbox contact"
                }
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = DmInkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = ChatboxPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onAudioCall) {
                Icon(Icons.Rounded.Phone, contentDescription = "Audio call", tint = DmLavenderAccent, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onVideoCall) {
                Icon(Icons.Rounded.Videocam, contentDescription = "Video call", tint = DmLavenderAccent, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Callee side: rings with accept/decline (accept → caller connects). */
@Composable
internal fun CallsIncomingCallDialog(
    peerName: String,
    isVideo: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    // Ringing: only the Accept / Decline buttons act; touches outside and Back are ignored.
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1E1930),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("calls_incoming_call_dialog")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MessengerAvatar(
                    label = peerName,
                    size = 84.dp,
                    showOnlineDot = true
                )
                Text(
                    text = peerName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (isVideo) "ভিডিও কল · ইনকামিং…" else "অডিও কল · ইনকামিং…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onDecline,
                        shape = CircleShape,
                        color = Color(0xFFE53935),
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("calls_decline_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.CallEnd,
                                contentDescription = "Decline",
                                tint = Color.White
                            )
                        }
                    }
                    Surface(
                        onClick = onAccept,
                        shape = CircleShape,
                        color = Color(0xFF2BB673),
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("calls_accept_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Call,
                                contentDescription = "Accept",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
