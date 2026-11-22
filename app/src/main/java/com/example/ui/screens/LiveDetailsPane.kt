package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.Conversation
import com.example.data.model.UserProfile
import com.example.ui.components.MessengerAvatar

@Composable
internal fun LiveDetailsPane(
    conversation: Conversation,
    currentUserId: String,
    peerProfile: UserProfile?,
    isBlocked: Boolean,
    busy: Boolean,
    quickReaction: String,
    onBackToThread: () -> Unit,
    onOpenCalls: () -> Unit,
    onSearchInConversation: () -> Unit,
    onQuickReactionChange: (String) -> Unit,
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
    var showReactionPicker by remember { mutableStateOf(false) }
    var idCopied by remember(peerUid) { mutableStateOf(false) }

    val copyPeerId: () -> Unit = {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("Peer Chatbox ID", peerUid))
        idCopied = true
    }

    val sharePeerId: () -> Unit = {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$peerName on Chatbox · Chatbox ID: $peerUid")
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share Chatbox ID"))
    }

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

    if (showReactionPicker) {
        AlertDialog(
            onDismissRequest = { showReactionPicker = false },
            title = { Text("Choose quick reaction") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Shown beside the message bar when your draft is empty.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QUICK_REACTION_OPTIONS.forEachIndexed { idx, emoji ->
                            val selected = emoji == quickReaction
                            Surface(
                                onClick = {
                                    onQuickReactionChange(emoji)
                                    showReactionPicker = false
                                },
                                shape = CircleShape,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                border = if (selected) {
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    null
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("quick_reaction_option_$idx")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = emoji, fontSize = 22.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReactionPicker = false }) {
                    Text("Close")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackToThread,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("live_back_to_thread_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(id = R.string.cd_back)
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                MessengerAvatar(label = peerName, size = 88.dp)
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = when {
                            isBlocked -> "Blocked"
                            conversation.status == "active" -> "Active"
                            conversation.status == "pending" -> "Pending"
                            else -> "Closed"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = peerName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
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
                    Icon(
                        imageVector = Icons.Rounded.LockOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Not end-to-end encrypted",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Text(
                text = "Display names are not verified",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DetailsQuickActionButton(
                icon = Icons.Rounded.Call,
                label = "Audio",
                contentDescription = stringResource(id = R.string.cd_voice_call),
                onClick = onOpenCalls,
                testTag = "live_details_call_button"
            )
            DetailsQuickActionButton(
                icon = Icons.Rounded.Videocam,
                label = "Video",
                contentDescription = stringResource(id = R.string.cd_video_call),
                onClick = onOpenCalls,
                testTag = "live_details_video_button"
            )
            DetailsQuickActionButton(
                icon = if (idCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                label = if (idCopied) "Copied" else "Copy ID",
                contentDescription = stringResource(id = R.string.cd_copy_chatbox_id),
                onClick = copyPeerId,
                testTag = "live_copy_peer_id_button"
            )
            DetailsQuickActionButton(
                icon = Icons.Rounded.Share,
                label = "Share",
                contentDescription = stringResource(id = R.string.cd_share_chatbox_id),
                onClick = sharePeerId,
                testTag = "live_share_peer_id_button"
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DetailsSectionTitle("Actions")
            DetailsMenuRow(
                icon = Icons.Rounded.Search,
                title = "Search in conversation",
                subtitle = "Find messages by keyword",
                onClick = onSearchInConversation,
                testTag = "live_details_search_button"
            )
            DetailsMenuRow(
                icon = Icons.Rounded.ContentCopy,
                title = "Copy Chatbox ID",
                subtitle = if (idCopied) "Copied to clipboard" else peerUid,
                onClick = copyPeerId,
                testTag = "live_details_copy_id_row"
            )
            DetailsMenuRow(
                icon = Icons.Rounded.Share,
                title = "Share Chatbox ID",
                subtitle = "Send via Android share sheet",
                onClick = sharePeerId,
                testTag = "live_details_share_id_row"
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DetailsSectionTitle("Customization")
            Surface(
                onClick = { showReactionPicker = true },
                shape = MaterialTheme.shapes.medium,
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("live_quick_reaction_row")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = quickReaction, fontSize = 20.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Quick reaction",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tap to change default emoji",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = quickReaction,
                        fontSize = 18.sp
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailsSectionTitle("Conversation")
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
            DetailsSectionTitle("Privacy & support")
            Text(
                text = "Messages are stored in Firestore and are not end-to-end encrypted. Closing a conversation does not block future requests.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isBlocked) {
                DetailsMenuRow(
                    icon = Icons.Rounded.Block,
                    title = "Unblock $peerName",
                    subtitle = "Allow new conversation requests",
                    tint = MaterialTheme.colorScheme.primary,
                    enabled = !busy,
                    onClick = onUnblockPeer,
                    testTag = "live_unblock_button"
                )
            } else {
                DetailsMenuRow(
                    icon = Icons.Rounded.Block,
                    title = "Block $peerName",
                    subtitle = "Stop messages and close this conversation",
                    tint = MaterialTheme.colorScheme.error,
                    enabled = !busy,
                    onClick = { showBlockConfirm = true },
                    testTag = "live_block_button"
                )
            }
        }
    }
}

@Composable
private fun DetailsQuickActionButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .size(48.dp)
                .testTag(testTag)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DetailsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
private fun DetailsMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = tint
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
