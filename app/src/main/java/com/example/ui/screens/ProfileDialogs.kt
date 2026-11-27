package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.example.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ChatboxConstants
import com.example.data.model.SparkAppearance
import com.example.data.model.WorldLanguages
import com.example.data.repository.LiveChatRepository
import com.example.ui.components.NoticeBanner
import com.example.ui.theme.ChatboxErrorInk
import com.example.ui.theme.ChatboxPrimary
import com.example.ui.theme.ChatboxSoftLavender
import com.example.ui.theme.LocalChatboxColors
import com.example.util.ChatboxSounds
import com.example.util.friendlyFirestoreMessage
import kotlinx.coroutines.launch

@Composable
fun ProfilePrivacyDialog(
    currentUserId: String,
    blockedIds: Set<String>,
    blockedThreadNames: Map<String, String> = emptyMap(),
    onUnblockThread: (String) -> Unit = {},
    liveLocationEnabled: Boolean,
    onChangeLiveLocation: (Boolean) -> Unit,
    liveChatRepo: LiveChatRepository,
    onDismiss: () -> Unit
) {
    val colors = LocalChatboxColors.current
    val scope = rememberCoroutineScope()
    var feedback by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Privacy", style = MaterialTheme.typography.titleLarge, color = colors.ink)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close privacy")
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.fieldBg,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                text = "Live location sharing",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.ink
                            )
                            Text(
                                text = "Allow Saira to include your city/area for permitted contacts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkMuted
                            )
                        }
                        Switch(
                            checked = liveLocationEnabled,
                            onCheckedChange = onChangeLiveLocation
                        )
                    }
                }

                feedback?.let { (msg, err) ->
                    NoticeBanner(text = msg, isError = err)
                }

                Text(
                    text = "Blocked contacts (${blockedIds.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.ink
                )

                if (blockedIds.isEmpty()) {
                    Text(
                        text = "You have not blocked anyone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkSubtle,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(blockedIds.toList(), key = { it }) { peerUid ->
                            val label = blockedThreadNames[peerUid] ?: peerUid.take(18)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = colors.fieldBg,
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.ink,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = {
                                            if (peerUid in blockedThreadNames) {
                                                onUnblockThread(peerUid)
                                                feedback = "Contact unblocked." to false
                                            } else if (currentUserId.isNotBlank()) {
                                                scope.launch {
                                                    liveChatRepo.unblockPeerUser(currentUserId, peerUid)
                                                        .onSuccess { feedback = "Contact unblocked." to false }
                                                        .onFailure { err ->
                                                            feedback = friendlyFirestoreMessage(err) to true
                                                        }
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Unblock")
                                    }
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}

@Composable
fun ProfileNotificationsDialog(
    onOpenSaira: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalChatboxColors.current
    val context = LocalContext.current
    // Bumped after every change so names/switches re-read the saved values.
    var revision by remember { mutableStateOf(0) }
    val msgTitle = remember(revision) { ChatboxSounds.title(context, ChatboxSounds.Kind.MESSAGE) }
    val callTitle = remember(revision) { ChatboxSounds.title(context, ChatboxSounds.Kind.CALL) }
    val msgVibrate = remember(revision) { ChatboxSounds.vibrate(context, ChatboxSounds.Kind.MESSAGE) }
    val callVibrate = remember(revision) { ChatboxSounds.vibrate(context, ChatboxSounds.Kind.CALL) }
    var pickingFor by remember { mutableStateOf<ChatboxSounds.Kind?>(null) }
    var playing by remember { mutableStateOf<android.media.Ringtone?>(null) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { runCatching { playing?.stop() } }
    }

    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val kind = pickingFor
        pickingFor = null
        if (kind != null && result.resultCode == android.app.Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            ChatboxSounds.setTone(context, kind, uri)   // null = "Silent"
            revision++
        }
    }

    fun openPicker(kind: ChatboxSounds.Kind) {
        runCatching { playing?.stop() }
        pickingFor = kind
        val type = if (kind == ChatboxSounds.Kind.MESSAGE) android.media.RingtoneManager.TYPE_NOTIFICATION
        else android.media.RingtoneManager.TYPE_RINGTONE
        val intent = Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, type)
            putExtra(
                android.media.RingtoneManager.EXTRA_RINGTONE_TITLE,
                if (kind == ChatboxSounds.Kind.MESSAGE) "Message tone" else "Call ringtone"
            )
            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, ChatboxSounds.systemDefault(kind))
            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ChatboxSounds.tone(context, kind))
        }
        runCatching { picker.launch(intent) }.onFailure { pickingFor = null }
    }

    @Composable
    fun ToneRow(label: String, value: String, kind: ChatboxSounds.Kind, tag: String) {
        Surface(
            onClick = { openPicker(kind) },
            shape = RoundedCornerShape(14.dp),
            color = colors.fieldBg,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth().testTag(tag)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.ink)
                    Text(value, style = MaterialTheme.typography.bodySmall, color = ChatboxPrimary)
                }
                TextButton(onClick = {
                    runCatching { playing?.stop() }
                    playing = ChatboxSounds.preview(context, kind)
                }) { Text("▶ শুনুন") }
            }
        }
    }

    @Composable
    fun VibrateRow(label: String, checked: Boolean, kind: ChatboxSounds.Kind) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.ink, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = {
                ChatboxSounds.setVibrate(context, kind, it)
                revision++
            })
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Notifications", style = MaterialTheme.typography.titleLarge, color = colors.ink)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close notifications settings")
                    }
                }

                Text("Messages", style = MaterialTheme.typography.titleSmall, color = colors.ink)
                ToneRow("Message tone", msgTitle, ChatboxSounds.Kind.MESSAGE, "notif_message_tone")
                VibrateRow("Vibrate for messages", msgVibrate, ChatboxSounds.Kind.MESSAGE)

                HorizontalDivider()
                Text("Calls", style = MaterialTheme.typography.titleSmall, color = colors.ink)
                ToneRow("Call ringtone", callTitle, ChatboxSounds.Kind.CALL, "notif_call_ringtone")
                VibrateRow("Vibrate for calls", callVibrate, ChatboxSounds.Kind.CALL)

                Text(
                    "ফোনের যেকোনো টোন বেছে নিতে পারবেন। \"Silent\" দিলে শব্দ হবে না।",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted
                )

                HorizontalDivider()
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ফোনের নোটিফিকেশন সেটিংস") }
                OutlinedButton(
                    onClick = { onDismiss(); onOpenSaira() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Saira auto-reply & assistant") }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}

@Composable
fun ProfileChatsSettingsDialog(
    quickReaction: String,
    oneMinAutoReplyEnabled: Boolean,
    onQuickReactionChange: (String) -> Unit,
    onChangeOneMinAutoReply: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalChatboxColors.current
    val emojiOptions = remember { listOf("💜", "❤️", "😂", "👍", "🔥", "✨") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Chats", style = MaterialTheme.typography.titleLarge, color = colors.ink)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close chats settings")
                    }
                }

                Text(
                    text = "Quick reaction emoji",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.ink
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    emojiOptions.forEach { emoji ->
                        FilterChip(
                            selected = quickReaction == emoji,
                            onClick = { onQuickReactionChange(emoji) },
                            label = { Text(emoji, fontSize = 18.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ChatboxSoftLavender
                            )
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.fieldBg,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                text = "Saira 10-second companion",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.ink
                            )
                            Text(
                                text = "Respond warmly if there is no reply within 10 seconds.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkMuted
                            )
                        }
                        Switch(
                            checked = oneMinAutoReplyEnabled,
                            onCheckedChange = onChangeOneMinAutoReply
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}

@Composable
fun ProfileLanguageDialog(
    selectedCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalChatboxColors.current
    val languages = remember { WorldLanguages.all }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("App language", style = MaterialTheme.typography.titleLarge, color = colors.ink)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close language picker")
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(languages, key = { it.code }) { lang ->
                        val isSelected = lang.code.equals(selectedCode, ignoreCase = true)
                        Surface(
                            onClick = {
                                onSelect(lang.code)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) ChatboxSoftLavender else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = lang.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isSelected) ChatboxPrimary else colors.ink
                                    )
                                    Text(
                                        text = lang.region,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.inkSubtle
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = ChatboxPrimary)
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
fun ProfileAppearanceDialog(
    appearance: SparkAppearance,
    isAdmin: Boolean,
    onOpenAdmin: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalChatboxColors.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Appearance", style = MaterialTheme.typography.titleLarge, color = colors.ink)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close appearance")
                    }
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.fieldBg,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Active Theme: ${appearance.theme.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.titleSmall, color = colors.ink)
                        Text("Navigation Bar Position: ${appearance.menu.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
                    }
                }
                if (isAdmin) {
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenAdmin()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("profile_open_admin_from_appearance")
                    ) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Customize in Admin Panel")
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
fun ProfileStorageDialog(
    cacheBytes: Long,
    onClearCache: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalChatboxColors.current
    val formatted = remember(cacheBytes) {
        when {
            cacheBytes < 1024L -> "$cacheBytes B"
            cacheBytes < 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f KB", cacheBytes / 1024.0)
            else -> String.format(java.util.Locale.US, "%.2f MB", cacheBytes / (1024.0 * 1024.0))
        }
    }
    var cleared by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Storage and data", style = MaterialTheme.typography.titleLarge, color = colors.ink)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close storage")
                    }
                }
                Text(
                    text = "Temporary local cache size: $formatted",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkMuted
                )
                if (cleared) {
                    NoticeBanner(text = "Local temporary cache cleared.")
                }
                OutlinedButton(
                    onClick = {
                        onClearCache()
                        cleared = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clear temporary cache")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
fun ProfileAboutHelpDialog(
    onDismiss: () -> Unit
) {
    val colors = LocalChatboxColors.current
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Help and feedback", style = MaterialTheme.typography.titleLarge, color = colors.ink)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close help")
                    }
                }
                Text(
                    text = "Chatbox v${ChatboxConstants.APP_VERSION}",
                    style = MaterialTheme.typography.titleSmall,
                    color = ChatboxPrimary
                )
                Text(
                    text = "• All user DMs, groups, voice notes, and photos stay ready instantly in Chatbox.\n" +
                        "• Customize quick reactions, nicknames, read receipts, disappearing messages, and PIN history restore.\n" +
                        "• Saira AI provides fast answers and optional auto-reply assistance.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkMuted
                )
                HorizontalDivider(color = colors.border)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(ChatboxConstants.GITHUB_RELEASES_BASE_URL))
                                )
                            }
                        }
                    ) {
                        Text("GitHub Releases")
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = ChatboxPrimary)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
