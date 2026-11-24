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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Conversation
import com.example.data.model.UserProfile
import com.example.ui.components.MessengerAvatar
import com.example.ui.components.NoticeBanner
import com.example.ui.theme.ChatboxErrorInk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val DmLavenderAccent = Color(0xFF7C5CE6)
internal val DmLavenderDeep = Color(0xFF5D3FD3)
internal val DmLavenderSoft = Color(0xFFF3EEFD)
internal val DmLavenderSoftStrong = Color(0xFFE6DCFA)
internal val DmIncomingBubbleBg = Color(0xFFF4F1FA)
internal val DmHairlineBorder = Color(0xFFEDE8F7)
internal val DmInkDark = Color(0xFF1E1930)
internal val DmInkMuted = Color(0xFF6B657D)

private val DM_QUICK_REACTIONS = listOf("💜", "👍", "❤️", "😂", "😮", "🔥", "🎉", "🙏")

@Composable
fun LiveChatDetailsScreen(
    conversation: Conversation,
    peerUid: String,
    peerProfile: UserProfile?,
    peerNickname: String?,
    isOnline: Boolean,
    isMuted: Boolean,
    isBlockedByMe: Boolean,
    liveLocationEnabled: Boolean,
    peerLocationAllowed: Boolean,
    quickReaction: String,
    onClose: () -> Unit,
    onOpenCalls: () -> Unit,
    onOpenSearch: () -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onUpdateNickname: (String) -> Unit,
    onQuickReactionChange: (String) -> Unit,
    onTogglePeerLocation: (Boolean) -> Unit,
    onCloseChat: () -> Unit,
    onToggleBlock: () -> Unit
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val baseName = peerProfile?.displayName?.takeIf { it.isNotBlank() } ?: "Chatbox User"
    val shownName = peerNickname?.takeIf { it.isNotBlank() } ?: baseName
    val rawShortId = peerProfile?.shortId?.trim().orEmpty()
    val hasShortId = rawShortId.isNotEmpty()
    val shortId = rawShortId.ifEmpty { "------" }
    val showActiveDot = isOnline && conversation.status == "active" && !isBlockedByMe

    var notice by remember { mutableStateOf<String?>(null) }
    var showReactionOptions by remember { mutableStateOf(false) }
    var showNicknameDialog by remember { mutableStateOf(false) }

    fun showTempNotice(msg: String) {
        notice = msg
        scope.launch {
            delay(2200)
            if (notice == msg) notice = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .testTag("dm_details_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("dm_details_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to chat",
                        tint = DmInkDark
                    )
                }
            }

            // Centered Avatar + Name + Protected DM Badge
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MessengerAvatar(
                    label = shownName,
                    size = 86.dp,
                    showOnlineDot = showActiveDot
                )

                Text(
                    text = shownName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = DmInkDark,
                    textAlign = TextAlign.Center
                )

                if (!peerNickname.isNullOrBlank() && peerNickname != baseName) {
                    Text(
                        text = baseName,
                        style = MaterialTheme.typography.bodySmall,
                        color = DmInkMuted
                    )
                }

                Surface(
                    color = DmLavenderSoft,
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = DmLavenderDeep,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Chatbox DM · ID $shortId",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = DmInkDark
                        )
                    }
                }

                if (!peerProfile?.about.isNullOrBlank()) {
                    Text(
                        text = peerProfile!!.about,
                        style = MaterialTheme.typography.bodySmall,
                        color = DmInkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(Modifier.height(6.dp))

                // 4 Circular Quick Actions (Call, Video Chat, Copy ID, Mute)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DmQuickCircleButton(
                        icon = Icons.Rounded.Phone,
                        label = "Call",
                        onClick = {
                            onClose()
                            onOpenCalls()
                        },
                        testTag = "dm_details_call"
                    )
                    DmQuickCircleButton(
                        icon = Icons.Rounded.Videocam,
                        label = "Video Chat",
                        onClick = {
                            onClose()
                            onOpenCalls()
                        },
                        testTag = "dm_details_video"
                    )
                    DmQuickCircleButton(
                        icon = Icons.Rounded.Person,
                        label = "Copy ID",
                        onClick = {
                            if (!hasShortId) {
                                showTempNotice("Chatbox ID is still syncing…")
                            } else {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("Chatbox ID", shortId))
                                showTempNotice("Copied $shownName's Chatbox ID: $shortId")
                            }
                        },
                        testTag = "dm_details_copy_id"
                    )
                    DmQuickCircleButton(
                        icon = if (isMuted) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                        label = if (isMuted) "Muted" else "Mute",
                        highlighted = isMuted,
                        onClick = {
                            val next = !isMuted
                            onToggleMute(next)
                            showTempNotice(if (next) "Notifications muted for $shownName" else "Notifications unmuted")
                        },
                        testTag = "dm_details_mute"
                    )
                }
            }

            notice?.let { msg ->
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    NoticeBanner(text = msg)
                }
            } ?: Spacer(Modifier.height(14.dp))

            HorizontalDivider(color = DmHairlineBorder)

            // Actions section
            DmDetailsSectionHeader("Actions")

            DmDetailsActionRow(
                icon = Icons.Rounded.Search,
                title = "Search in conversation",
                onClick = {
                    onClose()
                    onOpenSearch()
                }
            )

            DmDetailsActionRow(
                icon = Icons.Rounded.Share,
                title = "Share contact",
                onClick = {
                    if (!hasShortId) {
                        showTempNotice("Chatbox ID is still syncing…")
                    } else {
                        val shareText = "Chat with $baseName on Chatbox! 6-digit ID: $shortId"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(intent, "Share Chatbox contact"))
                        }
                    }
                }
            )

            DmDetailsActionRow(
                icon = Icons.Rounded.ContentCopy,
                title = "Copy Chatbox ID",
                trailingText = shortId,
                onClick = {
                    if (!hasShortId) {
                        showTempNotice("Chatbox ID is still syncing…")
                    } else {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("Chatbox ID", shortId))
                        showTempNotice("Copied ID: $shortId")
                    }
                }
            )

            // Customization section
            DmDetailsSectionHeader("Customization")

            DmDetailsActionRow(
                icon = null,
                emojiLeading = quickReaction,
                title = "Quick reaction",
                trailingText = "Change",
                onClick = { showReactionOptions = !showReactionOptions }
            )

            AnimatedVisibility(visible = showReactionOptions) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DM_QUICK_REACTIONS.forEach { emoji ->
                        val selected = emoji == quickReaction
                        Surface(
                            onClick = {
                                onQuickReactionChange(emoji)
                                showReactionOptions = false
                            },
                            shape = CircleShape,
                            color = if (selected) DmLavenderSoftStrong else DmLavenderSoft,
                            border = if (selected) BorderStroke(1.5.dp, DmLavenderAccent) else null
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

            DmDetailsActionRow(
                icon = Icons.Rounded.Title,
                title = "Nicknames",
                trailingText = peerNickname?.takeIf { it.isNotBlank() } ?: "Set",
                onClick = { showNicknameDialog = true }
            )

            // Privacy & support section
            DmDetailsSectionHeader("Privacy & support")

            DmDetailsActionRow(
                icon = Icons.Rounded.MyLocation,
                title = "Live location sharing",
                trailingText = if (liveLocationEnabled && peerLocationAllowed) "On" else "Off",
                onClick = {
                    if (!liveLocationEnabled) {
                        showTempNotice("Turn on Live Location Sharing in Saira → Auto-Reply first.")
                    } else {
                        val next = !peerLocationAllowed
                        onTogglePeerLocation(next)
                        showTempNotice(
                            if (next) "Live location sharing enabled for $shownName"
                            else "Live location sharing turned off for $shownName"
                        )
                    }
                }
            )

            if (conversation.status != "closed") {
                DmDetailsActionRow(
                    icon = Icons.Rounded.Close,
                    title = "Close conversation",
                    onClick = {
                        onCloseChat()
                        onClose()
                    }
                )
            }

            DmDetailsActionRow(
                icon = Icons.Rounded.Block,
                title = if (isBlockedByMe) "Unblock $shownName" else "Block $shownName",
                subtitle = if (isBlockedByMe) {
                    "Allow messages and calls from this user again"
                } else {
                    "Prevent new messages in this Chatbox thread"
                },
                tint = if (isBlockedByMe) DmLavenderDeep else ChatboxErrorInk,
                onClick = {
                    onToggleBlock()
                    onClose()
                },
                testTag = "dm_details_block_button"
            )
        }
    }

    if (showNicknameDialog) {
        var nickDraft by remember(peerNickname) { mutableStateOf(peerNickname.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showNicknameDialog = false },
            containerColor = Color.White,
            title = { Text("Nickname for $baseName", color = DmInkDark) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Customize how $baseName appears in your DM header and list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DmInkMuted
                    )
                    OutlinedTextField(
                        value = nickDraft,
                        onValueChange = { nickDraft = it.take(48) },
                        singleLine = true,
                        placeholder = { Text(baseName) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = DmLavenderAccent),
                    onClick = {
                        onUpdateNickname(nickDraft.trim())
                        showNicknameDialog = false
                    }
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
}

@Composable
private fun DmQuickCircleButton(
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
                    tint = if (highlighted) Color.White else DmLavenderDeep,
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
private fun DmDetailsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = DmInkDark,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun DmDetailsActionRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    trailingText: String? = null,
    emojiLeading: String? = null,
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
                if (emojiLeading != null) {
                    Text(text = emojiLeading, fontSize = 18.sp)
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (tint == ChatboxErrorInk) ChatboxErrorInk else DmLavenderDeep,
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
                fontWeight = FontWeight.SemiBold,
                color = DmLavenderDeep
            )
        }
    }
}
