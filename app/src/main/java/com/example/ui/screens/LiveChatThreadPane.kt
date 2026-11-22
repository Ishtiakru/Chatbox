package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.data.model.Conversation
import com.example.data.model.LiveMessage
import com.example.data.model.UserProfile
import com.example.media.VoicePlaybackState
import com.example.ui.components.ChatboxNoticeBanner
import com.example.ui.components.ConversationSearchBar
import com.example.ui.components.MessengerAvatar
import com.example.ui.components.highlightSearchMatch
import com.example.ui.theme.ChatboxLiveIncomingBubble
import com.example.ui.theme.ChatboxLiveOutgoingBubble
import com.example.ui.theme.ChatboxOnlineDot
import com.example.ui.viewmodel.LiveThreadState
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal val QUICK_REACTION_OPTIONS = listOf("💜", "👍", "❤️", "😂", "😮", "🔥", "🎉", "🙏")
private const val GROUP_TIME_GAP_MS = 15 * 60 * 1000L

internal fun resolvePeerDisplayName(peerUid: String, profile: UserProfile?): String {
    val name = profile?.displayName?.trim().orEmpty()
    if (name.isNotEmpty()) return name
    return if (peerUid.length > 10) "User ${peerUid.take(6)}…" else peerUid.ifEmpty { "Chatbox user" }
}

@Composable
internal fun LiveThreadPane(
    conversation: Conversation,
    currentUserId: String,
    peerProfile: UserProfile?,
    threadState: LiveThreadState,
    voicePlaybackState: VoicePlaybackState,
    isBlocked: Boolean,
    profileReady: Boolean,
    quickReaction: String,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onOpenDetails: () -> Unit,
    onOpenCalls: () -> Unit,
    onCloseThreadSearch: () -> Unit,
    onThreadSearchQueryChange: (String) -> Unit,
    onAcceptRequest: () -> Unit,
    onCloseOrDecline: () -> Unit,
    onLoadOlder: () -> Unit,
    onMessageDraftChange: (String) -> Unit,
    onAppendEmoji: (String) -> Unit,
    onCreateCameraUri: () -> Uri?,
    onAttachImage: (String?) -> Unit,
    onClearAttachedImage: () -> Unit,
    onStartVoiceRecording: () -> Unit,
    onStopVoiceRecording: (Boolean) -> Unit,
    onCancelVoiceRecording: () -> Unit,
    onClearPendingVoice: () -> Unit,
    onToggleVoicePlayback: (String, String, Long) -> Unit,
    onAttachmentError: (String) -> Unit,
    onSendMessage: () -> Unit,
    onSendQuickReaction: (String) -> Unit,
    onUnblockPeer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val peerUid = conversation.peerUid(currentUserId)
    val peerName = resolvePeerDisplayName(peerUid, peerProfile)
    val busy = threadState.busy || threadState.isUploadingMedia || !profileReady
    val composerEnabled = !busy && conversation.status == "active" && !isBlocked
    val displayMessages = threadState.allDisplayMessages
    val listState = rememberLazyListState()

    var showEmojiBar by rememberSaveable { mutableStateOf(false) }
    var pendingCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var fullscreenViewerMessage by remember { mutableStateOf<LiveMessage?>(null) }

    val trimmedThreadSearch = threadState.threadSearchQuery.trim()
    val matchedMessageIndices = remember(displayMessages, trimmedThreadSearch) {
        if (trimmedThreadSearch.isEmpty()) {
            emptyList()
        } else {
            displayMessages.mapIndexedNotNull { index, msg ->
                if (msg.body.contains(trimmedThreadSearch, ignoreCase = true)) index else null
            }
        }
    }
    var activeMatchCursor by remember(trimmedThreadSearch, matchedMessageIndices.size) {
        mutableIntStateOf(if (matchedMessageIndices.isNotEmpty()) matchedMessageIndices.lastIndex else 0)
    }

    val headerItemsCount =
        (if (!threadState.isOnline || threadState.messagesFromCache) 1 else 0) +
            (if (threadState.hasOlder) 1 else 0)

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onAttachImage(uri.toString())
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val captured = pendingCameraUriString
        pendingCameraUriString = null
        if (saved && !captured.isNullOrBlank()) {
            onAttachImage(captured)
        }
    }

    val launchCameraCapture: () -> Unit = {
        val photoUri = onCreateCameraUri()
        if (photoUri != null) {
            pendingCameraUriString = photoUri.toString()
            takePictureLauncher.launch(photoUri)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            onAttachmentError("Camera permission is needed to take a photo. You can still attach images from your gallery.")
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartVoiceRecording()
        } else {
            onAttachmentError("Microphone permission is needed to record voice notes.")
        }
    }

    fullscreenViewerMessage?.let { viewerMsg ->
        val viewerUrl = viewerMsg.imageUrl
        if (!viewerUrl.isNullOrBlank()) {
            ImageViewerDialog(
                imageUrl = viewerUrl,
                caption = viewerMsg.body,
                onDismiss = { fullscreenViewerMessage = null }
            )
        }
    }

    LaunchedEffect(conversation.id, displayMessages.lastOrNull()?.id) {
        if (displayMessages.isNotEmpty() && !threadState.loadingOlder && !threadState.threadSearchOpen) {
            listState.animateScrollToItem(headerItemsCount + displayMessages.lastIndex)
        }
    }

    LaunchedEffect(trimmedThreadSearch, matchedMessageIndices) {
        if (matchedMessageIndices.isNotEmpty()) {
            val targetMessageIndex = matchedMessageIndices[activeMatchCursor.coerceIn(matchedMessageIndices.indices)]
            listState.animateScrollToItem(headerItemsCount + targetMessageIndex)
        }
    }

    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("live_back_to_list_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(id = R.string.cd_back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenDetails)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.size(40.dp)) {
                    MessengerAvatar(label = peerName, size = 40.dp)
                    if (conversation.status == "active" && !isBlocked) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(ChatboxOnlineDot)
                                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = peerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            isBlocked -> "Blocked account"
                            conversation.status == "active" -> "Direct conversation"
                            conversation.status == "pending" -> "Message request"
                            else -> "Closed conversation"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onOpenCalls,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("live_thread_voice_call_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription = stringResource(id = R.string.cd_voice_call),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onOpenCalls,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("live_thread_video_call_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Videocam,
                    contentDescription = stringResource(id = R.string.cd_video_call),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onOpenDetails,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("live_details_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(id = R.string.cd_conversation_details),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))

        AnimatedVisibility(visible = threadState.threadSearchOpen) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ConversationSearchBar(
                            query = threadState.threadSearchQuery,
                            onQueryChange = onThreadSearchQueryChange,
                            testTag = "live_thread_search_input",
                            placeholder = "Search in conversation…",
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onCloseThreadSearch,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("live_thread_search_close_button")
                        ) {
                            Text("Done")
                        }
                    }
                    if (trimmedThreadSearch.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (matchedMessageIndices.isEmpty()) {
                                    "No matching messages"
                                } else {
                                    "${activeMatchCursor + 1} of ${matchedMessageIndices.size} matches"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (matchedMessageIndices.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    IconButton(
                                        onClick = {
                                            val next = if (activeMatchCursor > 0) activeMatchCursor - 1 else matchedMessageIndices.lastIndex
                                            activeMatchCursor = next
                                            scope.launch {
                                                listState.animateScrollToItem(headerItemsCount + matchedMessageIndices[next])
                                            }
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowUp,
                                            contentDescription = "Previous match"
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val next = (activeMatchCursor + 1) % matchedMessageIndices.size
                                            activeMatchCursor = next
                                            scope.launch {
                                                listState.animateScrollToItem(headerItemsCount + matchedMessageIndices[next])
                                            }
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = "Next match"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (conversation.status != "active") {
            ConversationLifecycleBar(
                conversation = conversation,
                currentUserId = currentUserId,
                busy = busy,
                canInteract = !isBlocked,
                onAccept = onAcceptRequest,
                onCloseOrDecline = onCloseOrDecline,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }

        if (isBlocked) {
            ChatboxNoticeBanner(
                text = "You blocked this account. Unblock to allow new conversation requests.",
                actionLabel = "Unblock",
                onAction = onUnblockPeer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!threadState.isOnline || threadState.messagesFromCache) {
                item(key = "cache_hint") {
                    ChatboxNoticeBanner(
                        text = if (!threadState.isOnline) {
                            "You're offline. Viewing cached messages — new text messages will queue and send automatically when back online."
                        } else {
                            "Showing cached messages while syncing with the server…"
                        },
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .testTag("live_offline_cache_banner")
                    )
                }
            }
            if (threadState.hasOlder) {
                item(key = "load_older") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        OutlinedButton(
                            onClick = onLoadOlder,
                            enabled = !threadState.loadingOlder,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("live_load_older_button")
                        ) {
                            if (threadState.loadingOlder) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Loading…")
                            } else {
                                Text("Load earlier messages")
                            }
                        }
                    }
                }
            }

            if (displayMessages.isEmpty()) {
                item(key = "empty_messages") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MessengerAvatar(label = peerName, size = 64.dp)
                        Text(
                            text = peerName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                !threadState.isOnline -> "You're offline and no cached messages were saved for this conversation yet."
                                threadState.messagesFromCache -> "Waiting for a server update."
                                conversation.status == "active" -> "Say hello or send $quickReaction to get started."
                                else -> "No messages to show."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                itemsIndexed(displayMessages, key = { _, msg -> msg.id }) { index, message ->
                    val prev = displayMessages.getOrNull(index - 1)
                    val next = displayMessages.getOrNull(index + 1)
                    val isOwn = message.senderId == currentUserId

                    val showDayTimeHeader = shouldShowGroupTimestamp(prev, message)
                    val groupedWithPrev = !showDayTimeHeader && prev?.senderId == message.senderId
                    val groupedWithNext = next != null &&
                        !shouldShowGroupTimestamp(message, next) &&
                        next.senderId == message.senderId

                    val isSearchMatch = trimmedThreadSearch.isNotEmpty() &&
                        message.body.contains(trimmedThreadSearch, ignoreCase = true)

                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (showDayTimeHeader) {
                            val headerLabel = formatConversationGroupTime(message)
                            if (headerLabel.isNotEmpty()) {
                                Text(
                                    text = headerLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = if (index == 0) 2.dp else 12.dp, bottom = 8.dp)
                                )
                            }
                        }

                        LiveMessageBubble(
                            message = message,
                            peerName = peerName,
                            isOwn = isOwn,
                            isOnline = threadState.isOnline,
                            groupedWithPrev = groupedWithPrev,
                            groupedWithNext = groupedWithNext,
                            showIncomingAvatar = !isOwn && !groupedWithNext,
                            searchQuery = trimmedThreadSearch,
                            isHighlightedMatch = isSearchMatch,
                            voicePlaybackState = voicePlaybackState,
                            onImageClick = { fullscreenViewerMessage = message },
                            onToggleVoicePlay = {
                                val url = message.audioUrl
                                if (!url.isNullOrBlank()) {
                                    onToggleVoicePlayback(message.id, url, message.audioDurationMs ?: 0L)
                                }
                            }
                        )
                    }
                }
            }
        }

        val activeBannerError = threadState.error.ifBlank { voicePlaybackState.error.orEmpty() }
        if (activeBannerError.isNotBlank()) {
            ChatboxNoticeBanner(
                text = activeBannerError,
                isError = true,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        if (!threadState.pendingImageUri.isNullOrBlank()) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AsyncImage(
                            model = threadState.pendingImageUri,
                            contentDescription = stringResource(id = R.string.cd_attached_image),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (threadState.uploadingImage) {
                                    "Uploading photo… ${(threadState.uploadProgress * 100).toInt()}%"
                                } else {
                                    "Photo attached"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (threadState.uploadingImage) {
                                    "Hosting media securely in Firebase Storage"
                                } else {
                                    "Add a caption or tap Send"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!threadState.uploadingImage) {
                            IconButton(
                                onClick = onClearAttachedImage,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("live_remove_attachment_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(id = R.string.cd_remove_attachment),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (threadState.uploadingImage) {
                        LinearProgressIndicator(
                            progress = { threadState.uploadProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        val pendingVoicePath = threadState.pendingVoicePath
        if (!pendingVoicePath.isNullOrBlank()) {
            LivePendingVoicePreviewCard(
                voicePath = pendingVoicePath,
                durationMs = threadState.pendingVoiceDurationMs,
                uploadingVoice = threadState.uploadingVoice,
                uploadProgress = threadState.uploadProgress,
                playbackState = voicePlaybackState,
                onTogglePlay = {
                    onToggleVoicePlayback("pending_voice", pendingVoicePath, threadState.pendingVoiceDurationMs)
                },
                onClear = onClearPendingVoice
            )
        }

        AnimatedVisibility(visible = showEmojiBar && composerEnabled && !threadState.isRecordingVoice) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                QUICK_REACTION_OPTIONS.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onAppendEmoji(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 22.sp)
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))

        if (threadState.isRecordingVoice) {
            LiveVoiceRecordingBar(
                elapsedMs = threadState.recordingElapsedMs,
                amplitude = threadState.recordingAmplitude,
                onCancel = onCancelVoiceRecording,
                onStopToPreview = { onStopVoiceRecording(false) },
                onSendNow = { onStopVoiceRecording(true) }
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                IconButton(
                    onClick = {
                        galleryPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = composerEnabled,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("live_attach_gallery_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = stringResource(id = R.string.cd_choose_gallery),
                        tint = if (composerEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        }
                    )
                }
                IconButton(
                    onClick = {
                        val hasCameraPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCameraPermission) {
                            launchCameraCapture()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    enabled = composerEnabled,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("live_attach_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoCamera,
                        contentDescription = stringResource(id = R.string.cd_take_photo),
                        tint = if (composerEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        }
                    )
                }
                IconButton(
                    onClick = {
                        val hasAudioPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasAudioPermission) {
                            onStartVoiceRecording()
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    enabled = composerEnabled,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("live_voice_record_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = stringResource(id = R.string.cd_record_voice_note),
                        tint = if (composerEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        }
                    )
                }

                val hasSendContent = threadState.messageDraft.trim().isNotEmpty() ||
                    !threadState.pendingImageUri.isNullOrBlank() ||
                    !threadState.pendingVoicePath.isNullOrBlank()
                val canSend = composerEnabled && hasSendContent

                OutlinedTextField(
                    value = threadState.messageDraft,
                    onValueChange = onMessageDraftChange,
                    enabled = composerEnabled,
                    placeholder = {
                        Text(
                            text = if (!threadState.pendingImageUri.isNullOrBlank() || !threadState.pendingVoicePath.isNullOrBlank()) {
                                "Add a caption…"
                            } else {
                                "Message"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { showEmojiBar = !showEmojiBar },
                            enabled = composerEnabled,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("live_emoji_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SentimentSatisfied,
                                contentDescription = stringResource(id = R.string.cd_add_emoji),
                                tint = if (composerEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                }
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSendMessage() }),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("live_message_input")
                )

                Spacer(modifier = Modifier.width(4.dp))

                if (threadState.isUploadingMedia) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (hasSendContent) {
                    Surface(
                        onClick = onSendMessage,
                        enabled = canSend,
                        shape = CircleShape,
                        color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("live_send_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = stringResource(id = R.string.cd_send),
                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = { onSendQuickReaction(quickReaction) },
                        enabled = composerEnabled,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("live_quick_reaction_button")
                    ) {
                        Text(
                            text = quickReaction,
                            fontSize = 24.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ConversationLifecycleBar(
    conversation: Conversation,
    currentUserId: String,
    busy: Boolean,
    canInteract: Boolean,
    onAccept: () -> Unit,
    onCloseOrDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            conversation.status == "active" -> {
                OutlinedButton(
                    onClick = onCloseOrDecline,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("live_close_conversation_button")
                ) {
                    Text("Close conversation")
                }
            }
            conversation.status == "pending" && conversation.createdBy == currentUserId -> {
                ChatboxNoticeBanner(
                    text = "Waiting for the other person to accept. Messaging is unavailable until then."
                )
                OutlinedButton(
                    onClick = onCloseOrDecline,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("live_cancel_request_button")
                ) {
                    Text("Cancel request")
                }
            }
            conversation.status == "pending" -> {
                ChatboxNoticeBanner(
                    text = "This person requested a conversation. Accept only if you trust them."
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onAccept,
                        enabled = !busy && canInteract,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("live_accept_request_button")
                    ) {
                        Text("Accept request")
                    }
                    OutlinedButton(
                        onClick = onCloseOrDecline,
                        enabled = !busy,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("live_decline_request_button")
                    ) {
                        Text("Decline")
                    }
                }
            }
            else -> {
                ChatboxNoticeBanner(
                    text = "This conversation is closed or unavailable. Previous messages remain visible."
                )
            }
        }
    }
}

@Composable
private fun LiveMessageBubble(
    message: LiveMessage,
    peerName: String,
    isOwn: Boolean,
    isOnline: Boolean,
    groupedWithPrev: Boolean,
    groupedWithNext: Boolean,
    showIncomingAvatar: Boolean,
    searchQuery: String,
    isHighlightedMatch: Boolean,
    voicePlaybackState: VoicePlaybackState,
    onImageClick: () -> Unit,
    onToggleVoicePlay: () -> Unit
) {
    val timeText = remember(message.sentAt, message.pendingWrites, isOnline) {
        when {
            message.pendingWrites && !isOnline -> "Queued · offline"
            message.pendingWrites || message.sentAt == null -> "Sending…"
            else -> DateFormat.getTimeInstance(DateFormat.SHORT, Locale.ENGLISH).format(message.sentAt.toDate())
        }
    }

    val isPureQuickEmoji = remember(message.body, message.imageUrl, message.audioUrl) {
        val trimmed = message.body.trim()
        message.imageUrl.isNullOrBlank() &&
            message.audioUrl.isNullOrBlank() &&
            trimmed in QUICK_REACTION_OPTIONS
    }

    val bubbleShape = RoundedCornerShape(
        topStart = if (!isOwn && groupedWithPrev) 6.dp else 20.dp,
        topEnd = if (isOwn && groupedWithPrev) 6.dp else 20.dp,
        bottomStart = if (!isOwn && groupedWithNext) 6.dp else 20.dp,
        bottomEnd = if (isOwn && groupedWithNext) 6.dp else 20.dp
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (groupedWithPrev) 1.dp else 6.dp),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isOwn) {
            Box(
                modifier = Modifier.width(32.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                if (showIncomingAvatar) {
                    MessengerAvatar(label = peerName, size = 26.dp)
                }
            }
        }

        Column(
            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            if (isPureQuickEmoji) {
                Text(
                    text = message.body.trim(),
                    fontSize = 36.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            } else {
                Surface(
                    shape = bubbleShape,
                    color = if (isOwn) ChatboxLiveOutgoingBubble else ChatboxLiveIncomingBubble,
                    border = if (isHighlightedMatch) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    }
                ) {
                    Column {
                        val imageUrl = message.imageUrl
                        if (!imageUrl.isNullOrBlank()) {
                            SubcomposeAsyncImage(
                                model = imageUrl,
                                contentDescription = stringResource(id = R.string.cd_attached_image),
                                contentScale = ContentScale.Crop,
                                loading = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(190.dp)
                                            .background(Color.Black.copy(alpha = 0.08f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = if (isOwn) Color.White else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                error = {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.BrokenImage,
                                            contentDescription = null,
                                            tint = if (isOwn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Unable to load image",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isOwn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .widthIn(min = 190.dp, max = 260.dp)
                                    .heightIn(min = 130.dp, max = 280.dp)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(onClick = onImageClick)
                                    .testTag("live_message_image_${message.id}")
                            )
                        }

                        val audioUrl = message.audioUrl
                        if (!audioUrl.isNullOrBlank()) {
                            LiveVoiceNoteBubblePlayer(
                                messageId = message.id,
                                audioUrl = audioUrl,
                                durationMs = message.audioDurationMs,
                                isOwn = isOwn,
                                playbackState = voicePlaybackState,
                                onTogglePlay = onToggleVoicePlay
                            )
                        }

                        if (message.body.isNotBlank()) {
                            val textColor = if (isOwn) Color.White else MaterialTheme.colorScheme.onSurface
                            val highlightColor = if (isOwn) Color.White else MaterialTheme.colorScheme.primary
                            val highlightBg = if (isOwn) {
                                Color.White.copy(alpha = 0.28f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            }
                            Text(
                                text = highlightSearchMatch(
                                    text = message.body,
                                    query = searchQuery,
                                    highlightColor = highlightColor,
                                    highlightBackground = highlightBg
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
            if (!groupedWithNext || message.pendingWrites) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    if (message.pendingWrites) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = "Queued for delivery",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Normal,
                        color = if (message.pendingWrites) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

private fun shouldShowGroupTimestamp(previous: LiveMessage?, current: LiveMessage): Boolean {
    if (previous == null) return true
    val prevMillis = previous.sentAt?.toDate()?.time ?: return false
    val currMillis = current.sentAt?.toDate()?.time ?: return false
    if (currMillis - prevMillis >= GROUP_TIME_GAP_MS) return true
    val prevCal = Calendar.getInstance().apply { timeInMillis = prevMillis }
    val currCal = Calendar.getInstance().apply { timeInMillis = currMillis }
    return prevCal.get(Calendar.YEAR) != currCal.get(Calendar.YEAR) ||
        prevCal.get(Calendar.DAY_OF_YEAR) != currCal.get(Calendar.DAY_OF_YEAR)
}

private fun formatConversationGroupTime(message: LiveMessage): String {
    val date: Date = message.sentAt?.toDate() ?: return ""
    val now = System.currentTimeMillis()
    val ageDays = ((now - date.time) / (24L * 60 * 60 * 1000L)).coerceAtLeast(0L)
    val pattern = if (ageDays < 7L) "EEE HH:mm" else "MMM d, HH:mm"
    return SimpleDateFormat(pattern, Locale.ENGLISH).format(date)
}

@Composable
private fun ImageViewerDialog(
    imageUrl: String,
    caption: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .padding(16.dp)
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = stringResource(id = R.string.cd_attached_image),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .minimumInteractiveComponentSize()
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .testTag("live_image_viewer_close")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(id = R.string.cd_close_image_viewer),
                    tint = Color.White
                )
            }
            if (caption.isNotBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}
