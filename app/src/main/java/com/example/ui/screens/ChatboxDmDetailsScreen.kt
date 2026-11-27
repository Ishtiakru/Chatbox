package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Switch
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import com.example.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatboxCatalog
import com.example.data.model.ChatboxThread
import com.example.data.model.DisappearingOption
import com.example.ui.components.MessengerAvatar
import com.example.ui.components.NoticeBanner
import com.example.ui.theme.ChatboxErrorInk
import com.example.ui.theme.ChatboxUpdateGreen
import com.example.ui.theme.DmHairlineBorder
import com.example.ui.theme.DmInkDark
import com.example.ui.theme.DmInkMuted
import com.example.ui.theme.DmLavenderAccent
import com.example.ui.theme.DmLavenderDeep
import com.example.ui.theme.DmLavenderSoft
import com.example.ui.theme.DmLavenderSoftStrong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DETAILS_QUICK_REACTIONS = listOf("👍", "💜", "❤️", "😂", "😮", "🔥", "🎉", "🙏")
private val REPORT_REASONS = listOf(
    "Spam or unwanted messages",
    "Harassment or bullying",
    "Fake profile / impersonation",
    "Scam or suspicious link",
    "Something else"
)

@Composable
fun ChatboxDmDetailsScreen(
    thread: ChatboxThread,
    allThreads: List<ChatboxThread>,
    peerNickname: String?,
    isMuted: Boolean,
    isBlocked: Boolean,
    effectiveQuickReaction: String,
    onClose: () -> Unit,
    onStartAudioCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onOpenPeerProfile: () -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onMarkAsUnread: () -> Unit,
    onCreateGroup: (groupName: String, members: List<String>) -> Unit,
    onOpenSearch: () -> Unit,
    onQuickReactionChange: (String) -> Unit,
    onUpdateNickname: (String) -> Unit,
    onToggleE2eeVerified: (Boolean) -> Unit,
    onDisappearingChange: (String) -> Unit,
    onToggleReadReceipts: (Boolean) -> Unit,
    onToggleBlock: () -> Unit,
    onSubmitReport: (reason: String, note: String) -> Unit,
    sairaEnabled: Boolean = true,
    onSairaEnabledChange: (Boolean) -> Unit = {}
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val shownName = peerNickname?.takeIf { it.isNotBlank() } ?: thread.name
    val firstName = shownName.substringBefore(" ").ifBlank { shownName }
    val showOnlineDot = thread.online && !isBlocked
    val disappearingOption = DisappearingOption.fromKey(thread.disappearingKey)

    var notice by remember { mutableStateOf<String?>(null) }
    var showReactionOptions by remember { mutableStateOf(false) }
    var showNicknameDialog by remember { mutableStateOf(false) }
    var showVerifyE2eeDialog by remember { mutableStateOf(false) }
    var showDisappearingDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    fun showTempNotice(msg: String) {
        notice = msg
        scope.launch {
            delay(2400)
            if (notice == msg) notice = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .testTag("chatbox_dm_details_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
        ) {
            // Sticky top bar with < back
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("chatbox_details_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to chat",
                        tint = DmLavenderAccent
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 28.dp)
            ) {

            // Centered Avatar + Name + End-to-end encrypted pill
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MessengerAvatar(
                    label = shownName,
                    size = 88.dp,
                    showOnlineDot = showOnlineDot
                )

                Text(
                    text = shownName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = DmInkDark,
                    textAlign = TextAlign.Center
                )

                if (!peerNickname.isNullOrBlank() && peerNickname != thread.name) {
                    Text(
                        text = thread.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = DmInkMuted
                    )
                }

                Surface(
                    color = Color(0xFFF0F2F5),
                    shape = CircleShape,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { showVerifyE2eeDialog = true }
                        .testTag("chatbox_details_e2ee_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = if (thread.e2eeVerified) ChatboxUpdateGreen else DmLavenderAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (thread.e2eeVerified) "End-to-end encrypted · Verified" else "End-to-end encrypted",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = DmInkDark
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // 4 Circular Quick Actions: Call, Video Chat, Profile, Mute
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ChatboxQuickCircleButton(
                        icon = Icons.Rounded.Phone,
                        label = "Call",
                        onClick = onStartAudioCall,
                        testTag = "chatbox_details_call"
                    )
                    ChatboxQuickCircleButton(
                        icon = Icons.Rounded.Videocam,
                        label = "Video Chat",
                        onClick = onStartVideoCall,
                        testTag = "chatbox_details_video"
                    )
                    ChatboxQuickCircleButton(
                        icon = Icons.Rounded.Person,
                        label = "Profile",
                        onClick = onOpenPeerProfile,
                        testTag = "chatbox_details_profile"
                    )
                    ChatboxQuickCircleButton(
                        icon = if (isMuted) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                        label = if (isMuted) "Muted" else "Mute",
                        highlighted = isMuted,
                        onClick = {
                            val next = !isMuted
                            onToggleMute(next)
                            showTempNotice(
                                if (next) "Notifications muted for $shownName"
                                else "Notifications unmuted"
                            )
                        },
                        testTag = "chatbox_details_mute"
                    )
                }
            }

            notice?.let { msg ->
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    NoticeBanner(text = msg)
                }
            } ?: Spacer(Modifier.height(12.dp))

            HorizontalDivider(color = DmHairlineBorder)

            // Actions section
            ChatboxDetailsSectionHeader("Actions")

            ChatboxDetailsActionRow(
                icon = Icons.Rounded.MarkChatUnread,
                title = "Mark as unread",
                onClick = onMarkAsUnread,
                testTag = "chatbox_details_mark_unread"
            )

            ChatboxDetailsActionRow(
                icon = Icons.Rounded.Share,
                title = "Share contact",
                onClick = {
                    val shareText = "Chat with ${thread.name} on Chatbox! ID: ${thread.shortId.ifBlank { thread.id }}"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(intent, "Share contact"))
                    }.onFailure {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("Chatbox Contact", shareText))
                        showTempNotice("Contact details copied to clipboard")
                    }
                },
                testTag = "chatbox_details_share_contact"
            )

            if (!thread.isGroup) {
                ChatboxDetailsActionRow(
                    icon = Icons.Rounded.GroupAdd,
                    title = "Create group with $firstName",
                    onClick = { showCreateGroupDialog = true },
                    testTag = "chatbox_details_create_group"
                )
            }

            ChatboxDetailsActionRow(
                icon = Icons.Rounded.Search,
                title = "Search in conversation",
                onClick = {
                    onClose()
                    onOpenSearch()
                },
                testTag = "chatbox_details_search"
            )

            // Saira auto-reply for THIS chat
            if (!thread.isGroup) {
                ChatboxDetailsSectionHeader("Saira Assistant")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSairaEnabledChange(!sairaEnabled) }
                        .testTag("chatbox_details_saira_row")
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(shape = CircleShape, color = DmLavenderSoft, modifier = Modifier.size(38.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = DmLavenderAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Saira auto-reply",
                            style = MaterialTheme.typography.bodyLarge,
                            color = DmInkDark
                        )
                        Text(
                            text = if (sairaEnabled) "আপনি line-এ না থাকলে Saira $firstName-কে উত্তর দেবে"
                            else "$firstName-কে Saira উত্তর দেবে না",
                            style = MaterialTheme.typography.bodySmall,
                            color = DmInkDark.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = sairaEnabled,
                        onCheckedChange = onSairaEnabledChange,
                        modifier = Modifier.testTag("chatbox_details_saira_switch")
                    )
                }
            }

            // Customization section
            ChatboxDetailsSectionHeader("Customization")

            ChatboxDetailsActionRow(
                icon = null,
                emojiLeading = effectiveQuickReaction,
                title = "Quick reaction",
                trailingText = "Change",
                onClick = { showReactionOptions = !showReactionOptions },
                testTag = "chatbox_details_quick_reaction"
            )

            AnimatedVisibility(visible = showReactionOptions) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DETAILS_QUICK_REACTIONS.forEach { emoji ->
                        val selected = emoji == effectiveQuickReaction
                        Surface(
                            onClick = {
                                onQuickReactionChange(emoji)
                                showReactionOptions = false
                                showTempNotice("Quick reaction updated to $emoji")
                            },
                            shape = CircleShape,
                            color = if (selected) DmLavenderSoftStrong else DmLavenderSoft,
                            border = if (selected) BorderStroke(1.5.dp, DmLavenderAccent) else null,
                            modifier = Modifier.testTag("quick_reaction_option_$emoji")
                        ) {
                            Text(
                                text = emoji,
                                fontSize = 20.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            ChatboxDetailsActionRow(
                icon = null,
                badgeTextLeading = "Aa",
                title = "Nicknames",
                trailingText = peerNickname?.takeIf { it.isNotBlank() } ?: "Set",
                onClick = { showNicknameDialog = true },
                testTag = "chatbox_details_nicknames"
            )

            // Privacy & support section
            ChatboxDetailsSectionHeader("Privacy & support")

            ChatboxDetailsActionRow(
                icon = Icons.Rounded.Lock,
                title = "Verify end-to-end encryption",
                trailingText = if (thread.e2eeVerified) "Verified" else null,
                onClick = { showVerifyE2eeDialog = true },
                testTag = "chatbox_details_verify_e2ee"
            )

            ChatboxDetailsActionRow(
                icon = Icons.Rounded.Timer,
                title = "Disappearing messages",
                trailingText = disappearingOption.shortLabel,
                onClick = { showDisappearingDialog = true },
                testTag = "chatbox_details_disappearing"
            )

            ChatboxDetailsActionRow(
                icon = if (thread.readReceiptsEnabled) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                title = "Read receipts",
                trailingText = if (thread.readReceiptsEnabled) "On" else "Off",
                onClick = {
                    val next = !thread.readReceiptsEnabled
                    onToggleReadReceipts(next)
                    showTempNotice(
                        if (next) "Read receipts turned On"
                        else "Read receipts turned Off"
                    )
                },
                testTag = "chatbox_details_read_receipts"
            )

            ChatboxDetailsActionRow(
                icon = Icons.Rounded.Block,
                title = if (isBlocked) "Unblock $firstName" else "Block $firstName",
                tint = if (isBlocked) DmLavenderDeep else ChatboxErrorInk,
                onClick = {
                    onToggleBlock()
                    showTempNotice(
                        if (isBlocked) "$firstName unblocked"
                        else "$firstName blocked"
                    )
                },
                testTag = "chatbox_details_block"
            )

            ChatboxDetailsActionRow(
                icon = Icons.Rounded.ReportProblem,
                title = "Report",
                subtitle = "Give feedback and report conversation",
                onClick = { showReportDialog = true },
                testTag = "chatbox_details_report"
            )
            }
        }
    }

    // Nickname Dialog
    if (showNicknameDialog) {
        var nickDraft by remember(peerNickname) { mutableStateOf(peerNickname.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showNicknameDialog = false },
            containerColor = Color.White,
            title = { Text("Nickname for ${thread.name}", color = DmInkDark) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Customize how ${thread.name} appears in your Chatbox inbox and conversation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DmInkMuted
                    )
                    OutlinedTextField(
                        value = nickDraft,
                        onValueChange = { nickDraft = it.take(48) },
                        singleLine = true,
                        placeholder = { Text(thread.name) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nickname_dialog_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = DmLavenderAccent),
                    onClick = {
                        onUpdateNickname(nickDraft.trim())
                        showNicknameDialog = false
                    },
                    modifier = Modifier.testTag("nickname_dialog_save")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                    if (!peerNickname.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = {
                                onUpdateNickname("")
                                showNicknameDialog = false
                            }
                        ) {
                            Text("Reset")
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    TextButton(onClick = { showNicknameDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Verify End-to-End Encryption Dialog
    if (showVerifyE2eeDialog) {
        val safetyBlocks = remember(thread.id, thread.name) {
            ChatboxCatalog.buildSafetyNumberBlocks(thread.id, thread.name)
        }
        AlertDialog(
            onDismissRequest = { showVerifyE2eeDialog = false },
            containerColor = Color.White,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (thread.e2eeVerified) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = if (thread.e2eeVerified) ChatboxUpdateGreen else DmLavenderAccent
                    )
                    Text("End-to-end encryption", color = DmInkDark)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "To verify that messages and calls with $shownName are end-to-end encrypted, compare these 60-digit safety numbers on both devices:",
                        style = MaterialTheme.typography.bodySmall,
                        color = DmInkMuted
                    )
                    Surface(
                        color = DmLavenderSoft,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            safetyBlocks.chunked(4).forEach { row ->
                                Text(
                                    text = row.joinToString("  "),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = DmInkDark,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (thread.e2eeVerified) ChatboxUpdateGreen else DmLavenderAccent
                    ),
                    onClick = {
                        val next = !thread.e2eeVerified
                        onToggleE2eeVerified(next)
                        showTempNotice(
                            if (next) "Marked $shownName as verified"
                            else "Verification cleared"
                        )
                        showVerifyE2eeDialog = false
                    },
                    modifier = Modifier.testTag("e2ee_verify_confirm_button")
                ) {
                    Text(if (thread.e2eeVerified) "Verified (Tap to clear)" else "Mark as verified")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVerifyE2eeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Disappearing Messages Dialog
    if (showDisappearingDialog) {
        AlertDialog(
            onDismissRequest = { showDisappearingDialog = false },
            containerColor = Color.White,
            title = { Text("Disappearing messages", color = DmInkDark) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "New messages sent in this chat will disappear after the selected duration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DmInkMuted,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    DisappearingOption.entries.forEach { option ->
                        val selected = option == disappearingOption
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onDisappearingChange(option.key)
                                    showTempNotice("Disappearing messages: ${option.label}")
                                    showDisappearingDialog = false
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("disappearing_option_${option.key}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    onDisappearingChange(option.key)
                                    showTempNotice("Disappearing messages: ${option.label}")
                                    showDisappearingDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = DmInkDark
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDisappearingDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Create Group with <FirstName> Dialog
    if (showCreateGroupDialog) {
        val candidatePeers = remember(allThreads, thread.id) {
            allThreads.filter { !it.isGroup && it.id != thread.id && it.id != "saira" }
        }
        val selectedNames = remember(thread.id) { mutableStateListOf<String>() }
        var groupName by remember(thread.id) { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            containerColor = Color.White,
            title = { Text("Create group with $firstName", color = DmInkDark) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it.take(60) },
                        label = { Text("Group name") },
                        placeholder = { Text("$firstName & Friends") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_group_name_input")
                    )
                    Text(
                        text = "Add members (besides $shownName):",
                        style = MaterialTheme.typography.labelMedium,
                        color = DmInkMuted
                    )
                    candidatePeers.forEach { peer ->
                        val checked = peer.name in selectedNames
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (checked) selectedNames.remove(peer.name)
                                    else selectedNames.add(peer.name)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (checked) selectedNames.remove(peer.name)
                                    else selectedNames.add(peer.name)
                                }
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(peer.name, style = MaterialTheme.typography.bodyMedium, color = DmInkDark)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = DmLavenderAccent),
                    onClick = {
                        onCreateGroup(groupName, selectedNames.toList())
                        showCreateGroupDialog = false
                    },
                    modifier = Modifier.testTag("create_group_confirm_button")
                ) {
                    Text("Create group")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Report Conversation Dialog
    if (showReportDialog) {
        var selectedReason by remember { mutableStateOf(REPORT_REASONS.first()) }
        var reportNote by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            containerColor = Color.White,
            title = { Text("Report conversation", color = DmInkDark) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Select a reason to report or send feedback about this chat with $shownName:",
                        style = MaterialTheme.typography.bodySmall,
                        color = DmInkMuted
                    )
                    REPORT_REASONS.forEach { reason ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedReason = reason }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason }
                            )
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = DmInkDark
                            )
                        }
                    }
                    OutlinedTextField(
                        value = reportNote,
                        onValueChange = { reportNote = it.take(400) },
                        label = { Text("Additional feedback (optional)") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = ChatboxErrorInk),
                    onClick = {
                        onSubmitReport(selectedReason, reportNote)
                        showReportDialog = false
                        showTempNotice("Thank you — your report has been recorded.")
                    },
                    modifier = Modifier.testTag("report_submit_button")
                ) {
                    Text("Submit report")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ChatboxQuickCircleButton(
    icon: ImageVector,
    label: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(testTag)
    ) {
        Surface(
            shape = CircleShape,
            color = if (highlighted) DmLavenderAccent else DmLavenderSoft,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (highlighted) Color.White else DmLavenderAccent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = DmInkDark,
            maxLines = 1
        )
    }
}

@Composable
private fun ChatboxDetailsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = DmInkDark,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun ChatboxDetailsActionRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    trailingText: String? = null,
    emojiLeading: String? = null,
    badgeTextLeading: String? = null,
    tint: Color = DmInkDark,
    onClick: () -> Unit,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clickable(onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (tint == ChatboxErrorInk) Color(0xFFFFF1ED) else DmLavenderSoft,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                when {
                    emojiLeading != null -> Text(text = emojiLeading, fontSize = 18.sp)
                    badgeTextLeading != null -> Text(
                        text = badgeTextLeading,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = DmLavenderAccent
                    )
                    icon != null -> Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (tint == ChatboxErrorInk) ChatboxErrorInk else DmLavenderAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = DmInkMuted
                )
            }
        }

        if (!trailingText.isNullOrBlank()) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = DmInkMuted
            )
        }
    }
}
