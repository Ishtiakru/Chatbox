package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ChatboxConstants
import com.example.data.model.ChatboxThread
import com.example.data.model.CoreAppTab
import com.example.data.model.LocalUserProfile
import com.example.data.model.SparkAppearance
import com.example.data.model.SparkAppUpdate
import com.example.data.model.UserProfile
import com.example.data.model.WorldLanguages
import com.example.data.repository.LiveChatRepository
import com.example.ui.components.ChatboxSearchField
import com.example.ui.components.MessengerAvatar
import com.example.ui.components.ProfileModalDialog
import com.example.ui.theme.ChatboxPrimary
import com.example.ui.theme.ChatboxSoftLavender
import com.example.ui.theme.ChatboxUpdateGreen
import com.example.ui.theme.LocalChatboxColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class ProfileSheet {
    NONE,
    EDIT_PROFILE,
    PRIVACY,
    CHATS,
    STORAGE,
    LANGUAGE,
    APPEARANCE,
    HELP
}

private data class ProfileMenuRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color? = null,
    val showGreenDot: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun ProfileScreen(
    currentUserId: String,
    userEmail: String?,
    fallbackDisplayName: String?,
    ownProfile: UserProfile?,
    localProfile: LocalUserProfile,
    blockedThreadIds: Set<String> = emptySet(),
    chatboxThreads: List<ChatboxThread> = emptyList(),
    avatarPath: String?,
    isOnline: Boolean,
    isAdmin: Boolean,
    loginLanguage: String,
    quickReaction: String,
    oneMinAutoReplyEnabled: Boolean,
    liveLocationEnabled: Boolean,
    appearance: SparkAppearance,
    appUpdate: SparkAppUpdate,
    liveChatRepo: LiveChatRepository,
    onPickAvatarUri: (Uri) -> Unit,
    onRemoveAvatar: () -> Unit,
    onProfileUpdated: (UserProfile) -> Unit,
    onSaveLocalProfile: (displayName: String, about: String, phoneNumber: String) -> Unit,
    onRetryEnsureProfile: () -> Unit,
    onUnblockThread: (String) -> Unit,
    onQuickReactionChange: (String) -> Unit,
    onChangeOneMinAutoReply: (Boolean) -> Unit,
    onChangeLiveLocation: (Boolean) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onAppUpdateClick: () -> Unit,
    onNavigateTab: (CoreAppTab) -> Unit
) {
    val colors = LocalChatboxColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var activeSheet by remember { mutableStateOf(ProfileSheet.NONE) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var copiedId by remember { mutableStateOf(false) }
    var cacheBytes by remember { mutableLongStateOf(0L) }

    val cloudBlockedIds by remember(currentUserId) {
        if (currentUserId.isNotBlank()) liveChatRepo.observeBlockedPeerIds(currentUserId)
        else flowOf(emptySet())
    }.collectAsState(initial = emptySet())

    val blockedIds = remember(cloudBlockedIds, blockedThreadIds) {
        cloudBlockedIds + blockedThreadIds
    }
    val blockedThreadNames = remember(chatboxThreads) {
        chatboxThreads.associate { it.id to it.name }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPickAvatarUri(uri)
        }
    }

    LaunchedEffect(currentUserId, isOnline) {
        if (currentUserId.isNotBlank() && ownProfile == null && isOnline) {
            onRetryEnsureProfile()
        }
        cacheBytes = withContext(Dispatchers.IO) {
            runCatching {
                context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }.getOrDefault(0L)
        }
    }

    if (searchOpen) {
        BackHandler {
            searchOpen = false
            searchQuery = ""
        }
    } else if (activeSheet != ProfileSheet.NONE) {
        BackHandler { activeSheet = ProfileSheet.NONE }
    }

    val effectiveName = ownProfile?.displayName?.ifBlank { null }
        ?: fallbackDisplayName?.ifBlank { null }
        ?: localProfile.displayName.ifBlank { "Chatbox User" }
    val effectiveAbout = ownProfile?.about?.ifBlank { null }
        ?: localProfile.about.ifBlank { LiveChatRepository.DEFAULT_ABOUT_TEXT }
    val effectiveShortId = ownProfile?.shortId?.ifBlank { null }
        ?: localProfile.shortId
    val hasLocalAvatar = remember(avatarPath) {
        !avatarPath.isNullOrBlank() && File(avatarPath).exists()
    }
    val showStickyTitle by remember {
        derivedStateOf { scrollState.value > 180 }
    }

    fun shareProfileInvite() {
        val shareText = if (effectiveShortId.isNotBlank()) {
            "Add me on Chatbox! My 6-digit Chatbox ID is $effectiveShortId ($effectiveName). Download: ${ChatboxConstants.GITHUB_RELEASES_BASE_URL}"
        } else {
            "Join me on Chatbox ($effectiveName)! Download: ${ChatboxConstants.GITHUB_RELEASES_BASE_URL}"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Share Chatbox profile"))
        }
    }

    val languageLabel = remember(loginLanguage) {
        WorldLanguages.displayLabel(loginLanguage)
    }

    val rows = remember(
        effectiveShortId,
        blockedIds.size,
        quickReaction,
        appearance,
        appUpdate,
        languageLabel,
        userEmail,
        isAdmin
    ) {
        listOf(
            ProfileMenuRow(
                id = "account",
                title = "Account",
                subtitle = if (effectiveShortId.isNotBlank()) {
                    "6-digit ID ($effectiveShortId), display name, phone number"
                } else {
                    "Display name, 6-digit Chatbox ID, phone discovery"
                },
                icon = Icons.Rounded.Key,
                onClick = { activeSheet = ProfileSheet.EDIT_PROFILE }
            ),
            ProfileMenuRow(
                id = "privacy",
                title = "Privacy",
                subtitle = "Blocked accounts (${blockedIds.size}), live location sharing",
                icon = Icons.Rounded.Lock,
                onClick = { activeSheet = ProfileSheet.PRIVACY }
            ),
            ProfileMenuRow(
                id = "chats",
                title = "Chats",
                subtitle = "Quick reaction ($quickReaction), Saira 10-second companion",
                icon = Icons.Rounded.ChatBubbleOutline,
                onClick = { activeSheet = ProfileSheet.CHATS }
            ),
            ProfileMenuRow(
                id = "notifications",
                title = "Notifications & Saira",
                subtitle = "Auto-reply rules, WhatsApp & Messenger assistant",
                icon = Icons.Rounded.NotificationsNone,
                onClick = { onNavigateTab(CoreAppTab.SAIRA) }
            ),
            ProfileMenuRow(
                id = "storage",
                title = "Storage and data",
                subtitle = "Compressed media & temporary cache",
                icon = Icons.Rounded.DataUsage,
                onClick = { activeSheet = ProfileSheet.STORAGE }
            ),
            ProfileMenuRow(
                id = "appearance",
                title = "Appearance",
                subtitle = "Theme: ${appearance.theme}, menu bar: ${appearance.menu}",
                icon = Icons.Rounded.Palette,
                onClick = { activeSheet = ProfileSheet.APPEARANCE }
            ),
            ProfileMenuRow(
                id = "language",
                title = "App language",
                subtitle = languageLabel,
                icon = Icons.Rounded.Language,
                onClick = { activeSheet = ProfileSheet.LANGUAGE }
            ),
            ProfileMenuRow(
                id = "help",
                title = "Help and feedback",
                subtitle = "About Chatbox, Saira AI & GitHub releases",
                icon = Icons.AutoMirrored.Rounded.HelpOutline,
                onClick = { activeSheet = ProfileSheet.HELP }
            ),
            ProfileMenuRow(
                id = "invite",
                title = "Invite a friend",
                subtitle = if (effectiveShortId.isNotBlank()) {
                    "Share your 6-digit ID ($effectiveShortId)"
                } else {
                    "Share Chatbox invite link"
                },
                icon = Icons.Rounded.GroupAdd,
                onClick = { shareProfileInvite() }
            ),
            ProfileMenuRow(
                id = "updates",
                title = "App updates",
                subtitle = if (appUpdate.active) {
                    "Update available: v${appUpdate.version} (installed v${ChatboxConstants.APP_VERSION})"
                } else {
                    "Chatbox v${ChatboxConstants.APP_VERSION} · Up to date"
                },
                icon = Icons.Rounded.SystemUpdate,
                showGreenDot = appUpdate.active,
                onClick = {
                    if (appUpdate.active) {
                        onAppUpdateClick()
                    } else {
                        activeSheet = ProfileSheet.HELP
                    }
                }
            )
        )
    }

    val filteredRows = remember(rows, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) rows
        else rows.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.subtitle.contains(q, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("profile_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Sticky WhatsApp-style top bar
        Surface(color = colors.surface) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showStickyTitle) effectiveName else "You",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                searchOpen = !searchOpen
                                if (!searchOpen) searchQuery = ""
                            },
                            modifier = Modifier.testTag("profile_search_button")
                        ) {
                            Icon(
                                imageVector = if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = "Search profile settings",
                                tint = colors.ink
                            )
                        }
                        IconButton(
                            onClick = { shareProfileInvite() },
                            modifier = Modifier.testTag("profile_share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Share profile",
                                tint = colors.ink
                            )
                        }
                        IconButton(
                            onClick = { activeSheet = ProfileSheet.EDIT_PROFILE },
                            modifier = Modifier.testTag("profile_edit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Edit profile",
                                tint = colors.ink
                            )
                        }
                    }
                }
                if (searchOpen) {
                    ChatboxSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search settings…",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        testTag = "profile_search_input"
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            if (!searchOpen) {
                // Hero Profile Header with subtle doodle backdrop, thought bubble, avatar + '+' badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    ChatboxSoftLavender.copy(alpha = 0.55f),
                                    colors.background
                                )
                            )
                        )
                        .padding(top = 14.dp, bottom = 18.dp, start = 20.dp, end = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ProfileDoodleBackdrop(
                        tint = ChatboxPrimary.copy(alpha = 0.08f),
                        modifier = Modifier.matchParentSize()
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Thought bubble above avatar
                        Surface(
                            onClick = { activeSheet = ProfileSheet.EDIT_PROFILE },
                            shape = RoundedCornerShape(20.dp),
                            color = colors.surface.copy(alpha = 0.94f),
                            border = BorderStroke(1.dp, colors.border),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .widthIn(max = 260.dp)
                                .testTag("profile_status_bubble")
                        ) {
                            Text(
                                text = effectiveAbout,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .offset(x = (-14).dp)
                                .clip(CircleShape)
                                .background(colors.surface.copy(alpha = 0.95f))
                                .border(1.dp, colors.border, CircleShape)
                        )

                        // Circular avatar with '+' add/change photo badge
                        Box(
                            modifier = Modifier.size(102.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                onClick = {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = CircleShape,
                                color = colors.surface,
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .size(94.dp)
                                    .border(3.dp, colors.surface, CircleShape)
                            ) {
                                if (hasLocalAvatar) {
                                    AsyncImage(
                                        model = File(avatarPath!!),
                                        contentDescription = "Profile photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    MessengerAvatar(label = effectiveName, size = 94.dp)
                                }
                            }

                            Surface(
                                onClick = {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = CircleShape,
                                color = ChatboxUpdateGreen,
                                border = BorderStroke(2.dp, colors.surface),
                                shadowElevation = 3.dp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(32.dp)
                                    .testTag("profile_pick_photo_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "Change profile photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        if (hasLocalAvatar) {
                            TextButton(
                                onClick = onRemoveAvatar,
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(
                                    text = "Remove photo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.inkSubtle
                                )
                            }
                        }

                        // Name + dropdown chevron (opens Edit Profile)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { activeSheet = ProfileSheet.EDIT_PROFILE }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = effectiveName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Edit profile",
                                tint = colors.inkSubtle,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Copyable 6-digit Chatbox ID pill
                        if (effectiveShortId.isNotBlank()) {
                            Surface(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("Chatbox ID", effectiveShortId))
                                    copiedId = true
                                    scope.launch {
                                        delay(1800)
                                        copiedId = false
                                    }
                                },
                                shape = CircleShape,
                                color = ChatboxSoftLavender,
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier.testTag("profile_copy_id_chip")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "ID: $effectiveShortId",
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ChatboxPrimary
                                    )
                                    Icon(
                                        imageVector = if (copiedId) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                        contentDescription = "Copy 6-digit Chatbox ID",
                                        tint = if (copiedId) ChatboxUpdateGreen else ChatboxPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        } else if (ownProfile == null) {
                            TextButton(onClick = onRetryEnsureProfile) {
                                Text(
                                    text = if (isOnline) "Creating your 6-digit ID…" else "Offline · Tap to sync profile",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ChatboxPrimary
                                )
                            }
                        }

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
                }
            }

            // Settings rows
            filteredRows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 62.dp)
                        .clickable(onClick = row.onClick)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .testTag("profile_row_${row.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Box {
                        Icon(
                            imageVector = row.icon,
                            contentDescription = row.title,
                            tint = row.tint ?: colors.inkMuted,
                            modifier = Modifier.size(24.dp)
                        )
                        if (row.showGreenDot) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(ChatboxUpdateGreen)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = row.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = row.tint ?: colors.ink
                        )
                        Text(
                            text = row.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkSubtle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (!searchOpen) {
                HorizontalDivider(
                    color = colors.border,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // "Also in Chatbox" quick shortcuts (like WhatsApp's "Also from Meta" footer)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Also in Chatbox",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.inkMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProfileShortcutBubble(
                            label = "Chatbox",
                            icon = Icons.Rounded.ChatBubbleOutline,
                            onClick = { onNavigateTab(CoreAppTab.CHATBOX) }
                        )
                        ProfileShortcutBubble(
                            label = "Saira AI",
                            icon = Icons.Rounded.AutoAwesome,
                            onClick = { onNavigateTab(CoreAppTab.SAIRA) }
                        )
                        ProfileShortcutBubble(
                            label = "Calls",
                            icon = Icons.Rounded.Phone,
                            onClick = { onNavigateTab(CoreAppTab.CALLS) }
                        )
                        if (isAdmin) {
                            ProfileShortcutBubble(
                                label = "Admin",
                                icon = Icons.Rounded.Shield,
                                onClick = { onNavigateTab(CoreAppTab.ADMIN) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    when (activeSheet) {
        ProfileSheet.NONE -> Unit
        ProfileSheet.EDIT_PROFILE -> ProfileModalDialog(
            uid = currentUserId,
            email = userEmail,
            fallbackDisplayName = effectiveName,
            localProfile = localProfile,
            isOnline = isOnline,
            liveChatRepo = liveChatRepo,
            onDismiss = { activeSheet = ProfileSheet.NONE },
            onProfileUpdated = onProfileUpdated,
            onSaveLocalProfile = onSaveLocalProfile
        )
        ProfileSheet.PRIVACY -> ProfilePrivacyDialog(
            currentUserId = currentUserId,
            blockedIds = blockedIds,
            blockedThreadNames = blockedThreadNames,
            onUnblockThread = onUnblockThread,
            liveLocationEnabled = liveLocationEnabled,
            onChangeLiveLocation = onChangeLiveLocation,
            liveChatRepo = liveChatRepo,
            onDismiss = { activeSheet = ProfileSheet.NONE }
        )
        ProfileSheet.CHATS -> ProfileChatsSettingsDialog(
            quickReaction = quickReaction,
            oneMinAutoReplyEnabled = oneMinAutoReplyEnabled,
            onQuickReactionChange = onQuickReactionChange,
            onChangeOneMinAutoReply = onChangeOneMinAutoReply,
            onDismiss = { activeSheet = ProfileSheet.NONE }
        )
        ProfileSheet.STORAGE -> ProfileStorageDialog(
            cacheBytes = cacheBytes,
            onClearCache = {
                scope.launch {
                    cacheBytes = withContext(Dispatchers.IO) {
                        runCatching {
                            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                            context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                        }.getOrDefault(0L)
                    }
                }
            },
            onDismiss = { activeSheet = ProfileSheet.NONE }
        )
        ProfileSheet.LANGUAGE -> ProfileLanguageDialog(
            selectedCode = loginLanguage,
            onSelect = onLanguageSelected,
            onDismiss = { activeSheet = ProfileSheet.NONE }
        )
        ProfileSheet.APPEARANCE -> ProfileAppearanceDialog(
            appearance = appearance,
            isAdmin = isAdmin,
            onOpenAdmin = { onNavigateTab(CoreAppTab.ADMIN) },
            onDismiss = { activeSheet = ProfileSheet.NONE }
        )
        ProfileSheet.HELP -> ProfileAboutHelpDialog(
            onDismiss = { activeSheet = ProfileSheet.NONE }
        )
    }
}

@Composable
private fun ProfileShortcutBubble(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val colors = LocalChatboxColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = colors.fieldBg,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = colors.ink,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkMuted,
            maxLines = 1
        )
    }
}

@Composable
private fun ProfileDoodleBackdrop(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stepX = 74.dp.toPx()
        val stepY = 64.dp.toPx()
        val stroke = Stroke(width = 1.4.dp.toPx())
        var row = 0
        var y = 18.dp.toPx()
        while (y < size.height) {
            var x = if (row % 2 == 0) 20.dp.toPx() else 52.dp.toPx()
            var col = 0
            while (x < size.width) {
                when ((row + col) % 3) {
                    0 -> drawCircle(color = tint, radius = 9.dp.toPx(), center = Offset(x, y), style = stroke)
                    1 -> drawLine(
                        color = tint,
                        start = Offset(x - 8.dp.toPx(), y + 5.dp.toPx()),
                        end = Offset(x + 8.dp.toPx(), y - 5.dp.toPx()),
                        strokeWidth = 1.4.dp.toPx()
                    )
                    else -> drawArc(
                        color = tint,
                        startAngle = 0f,
                        sweepAngle = 220f,
                        useCenter = false,
                        topLeft = Offset(x - 8.dp.toPx(), y - 8.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(16.dp.toPx(), 16.dp.toPx()),
                        style = stroke
                    )
                }
                x += stepX
                col++
            }
            y += stepY
            row++
        }
    }
}
