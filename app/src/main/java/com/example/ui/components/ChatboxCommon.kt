package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.model.ChatboxConstants
import com.example.data.model.CustomMenuIconName
import com.example.data.model.LocalUserProfile
import com.example.data.model.SparkAppUpdate
import com.example.data.model.SparkCustomMenu
import com.example.data.model.UserProfile
import com.example.data.repository.LiveChatRepository
import com.example.ui.theme.ChatboxAccentViolet
import com.example.ui.theme.ChatboxBorder
import com.example.ui.theme.ChatboxErrorBg
import com.example.ui.theme.ChatboxErrorBorder
import com.example.ui.theme.ChatboxErrorInk
import com.example.ui.theme.ChatboxPrimary
import com.example.ui.theme.ChatboxSoftLavender
import com.example.ui.theme.ChatboxUpdateGreen
import com.example.ui.theme.ChatboxUpdateGreenSoft
import com.example.ui.theme.LocalChatboxColors
import com.example.ui.theme.MetaOrbCyan
import com.example.ui.theme.MetaOrbIndigo
import com.example.ui.theme.MetaOrbPink
import com.example.ui.theme.MetaOrbPurple
import com.example.util.friendlyFirestoreMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun customMenuIconVector(iconName: CustomMenuIconName): ImageVector = when (iconName) {
    CustomMenuIconName.BELL -> Icons.Filled.Notifications
    CustomMenuIconName.MEGAPHONE -> Icons.Rounded.Campaign
    CustomMenuIconName.BOOK -> Icons.Rounded.Book
    CustomMenuIconName.STAR -> Icons.Rounded.Star
    CustomMenuIconName.INFO -> Icons.Rounded.Info
    CustomMenuIconName.GRID -> Icons.Rounded.GridView
    CustomMenuIconName.SPARKLES -> Icons.Rounded.AutoAwesome
}

@Composable
fun ChatboxLogoWithUpdateBadge(
    appUpdateActive: Boolean,
    onUpdateClick: () -> Unit,
    modifier: Modifier = Modifier,
    logoSize: Dp = 36.dp
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(logoSize)
                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.35f), CircleShape),
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 1.dp
        ) {
            Image(
                painter = painterResource(R.drawable.chatbox_logo_lavender),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        ChatboxUpdateBadge(
            appUpdateActive = appUpdateActive,
            onUpdateClick = onUpdateClick,
            modifier = Modifier
        )
    }
}

@Composable
fun ChatboxUpdateBadge(
    appUpdateActive: Boolean,
    onUpdateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (appUpdateActive) {
        Surface(
            onClick = onUpdateClick,
            shape = CircleShape,
            color = ChatboxUpdateGreen,
            shadowElevation = 2.dp,
            modifier = modifier
                .defaultMinSize(minHeight = 32.dp)
                .testTag("green_update_badge")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = stringResource(R.string.update_badge),
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.update_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun SairaMetaOrb(
    size: Dp = 32.dp,
    animate: Boolean = false,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "saira_orb")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (animate) 1800 else 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )
    Canvas(modifier = modifier.size(size)) {
        val strokeWidth = (this.size.minDimension * 0.14f).coerceAtLeast(2.5f)
        val radius = (this.size.minDimension - strokeWidth) / 2f
        rotate(degrees = rotation) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        MetaOrbPurple,
                        MetaOrbIndigo,
                        MetaOrbPink,
                        MetaOrbCyan,
                        MetaOrbPurple
                    )
                ),
                radius = radius,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFA78BFA), MetaOrbIndigo, MetaOrbCyan),
                center = Offset(this.size.width * 0.42f, this.size.height * 0.42f),
                radius = this.size.minDimension * 0.34f
            ),
            radius = this.size.minDimension * 0.25f
        )
    }
}

private val messengerPalettes = listOf(
    listOf(Color(0xFFEDE9FE), Color(0xFFC4B5FD)) to Color(0xFF4C1D95),
    listOf(Color(0xFFF1D9CC), Color(0xFFD5A897)) to Color(0xFF865E4C),
    listOf(Color(0xFFF9E8C5), Color(0xFFE4C78E)) to Color(0xFF8B6F3D),
    listOf(Color(0xFFDCE9E4), Color(0xFFB7D4CC)) to Color(0xFF3F6257),
    listOf(Color(0xFFE0E7FF), Color(0xFFA5B4FC)) to Color(0xFF3730A3)
)

@Composable
fun MessengerAvatar(
    label: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    showOnlineDot: Boolean = false
) {
    val clean = label.trim().ifEmpty { "U" }
    val initial = clean.take(1).uppercase()
    val palette = messengerPalettes[(clean.first().code) % messengerPalettes.size]
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Brush.linearGradient(palette.first)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = palette.second,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.4f).coerceAtLeast(12f).sp
            )
        }
        if (showOnlineDot) {
            val dotSize = (size * 0.28f).coerceIn(10.dp, 20.dp)
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Color(0xFF1E8E3E))
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}

@Composable
fun NoticeBanner(
    text: String,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalChatboxColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isError) ChatboxErrorBg else colors.noticeBg,
        border = BorderStroke(1.dp, if (isError) ChatboxErrorBorder else colors.border)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) ChatboxErrorInk else colors.noticeInk,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun ChatboxSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    testTag: String = "search_field"
) {
    val colors = LocalChatboxColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkSubtle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = placeholder,
                tint = colors.inkSubtle
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = { onValueChange("") },
                    modifier = Modifier.testTag("${testTag}_clear")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear search",
                        tint = colors.inkSubtle
                    )
                }
            }
        },
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}

@Composable
fun AppUpdateDetailsDialog(
    appUpdate: SparkAppUpdate,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val effectiveUrl = appUpdate.downloadUrl.ifBlank { ChatboxConstants.GITHUB_RELEASES_BASE_URL }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            color = ChatboxUpdateGreenSoft,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "NEW UPDATE AVAILABLE",
                                color = Color(0xFF15803D),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = "Chatbox v${appUpdate.version}",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF2A2340)
                        )
                        Text(
                            text = "Installed version: v${ChatboxConstants.APP_VERSION}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8F89A3)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                if (appUpdate.note.isNotBlank()) {
                    Surface(
                        color = Color(0xFFF7F5FB),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ChatboxBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = appUpdate.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF3A3347),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(effectiveUrl))
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ChatboxUpdateGreen),
                        modifier = Modifier.testTag("download_update_button")
                    ) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = "Download Update",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Download APK")
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileModalDialog(
    uid: String,
    email: String?,
    fallbackDisplayName: String?,
    localProfile: LocalUserProfile,
    isOnline: Boolean,
    liveChatRepo: LiveChatRepository,
    onDismiss: () -> Unit,
    onProfileUpdated: (UserProfile) -> Unit,
    onSaveLocalProfile: (displayName: String, about: String, phoneNumber: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasCloudUid = uid.isNotBlank()
    var loading by remember { mutableStateOf(hasCloudUid) }
    var saving by remember { mutableStateOf(false) }
    var shortId by remember { mutableStateOf(localProfile.shortId) }
    var displayName by remember { mutableStateOf(fallbackDisplayName ?: localProfile.displayName) }
    var about by remember { mutableStateOf(localProfile.about) }
    var phoneNumber by remember { mutableStateOf(localProfile.phoneNumber) }
    var savedPhone by remember { mutableStateOf(localProfile.phoneNumber) }
    var copied by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    val aboutPresets = remember {
        listOf(
            "Hey there! I am using Chatbox.",
            "Available",
            "Busy right now",
            "At work / studying",
            "Sleeping"
        )
    }

    LaunchedEffect(uid) {
        if (!hasCloudUid) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        liveChatRepo.loadOwnProfileDetails(uid, fallbackDisplayName)
            .onSuccess { (profile, phone) ->
                shortId = profile.shortId
                displayName = profile.displayName
                about = profile.about.ifEmpty { LiveChatRepository.DEFAULT_ABOUT_TEXT }
                phoneNumber = phone
                savedPhone = phone
            }
            .onFailure { err ->
                feedback = friendlyFirestoreMessage(err) to true
            }
        loading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // WhatsApp-style Purple Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(ChatboxPrimary, Color(0xFF7C66C4))
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Profile",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = "Your 6-digit ID & phone discovery",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close profile", tint = Color.White)
                    }
                }

                if (loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ChatboxPrimary)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MessengerAvatar(
                                label = displayName.ifBlank { email ?: "U" },
                                size = 68.dp
                            )
                            Text(
                                text = displayName.ifBlank { "Chatbox User" },
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF2A2340)
                            )
                            if (!email.isNullOrBlank()) {
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF8F89A3)
                                )
                            }
                        }

                        // 6-digit Chatbox ID card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFF7F5FB),
                            border = BorderStroke(1.dp, Color(0xFFE3DCF2)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(ChatboxSoftLavender),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.Tag,
                                            contentDescription = "6-Digit Chatbox ID",
                                            tint = ChatboxPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "6-Digit Chatbox ID",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(0xFF8F89A3)
                                        )
                                        Text(
                                            text = shortId.ifBlank { "------" },
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            letterSpacing = 3.sp,
                                            color = Color(0xFF2A2340),
                                            modifier = Modifier.testTag("profile_short_id_text")
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        clipboard?.setPrimaryClip(
                                            ClipData.newPlainText("Chatbox ID", shortId.ifBlank { uid })
                                        )
                                        copied = true
                                        scope.launch {
                                            delay(1800)
                                            copied = false
                                        }
                                    },
                                    modifier = Modifier.testTag("copy_short_id_button")
                                ) {
                                    Icon(
                                        imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                        contentDescription = "Copy ID",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (copied) "Copied" else "Copy")
                                }
                            }
                        }

                        feedback?.let { (msg, isErr) ->
                            NoticeBanner(text = msg, isError = isErr)
                        }

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it.take(ChatboxConstants.MAX_DISPLAY_NAME_LENGTH) },
                            label = { Text("Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_name_input")
                        )

                        OutlinedTextField(
                            value = about,
                            onValueChange = { about = it.take(ChatboxConstants.MAX_ABOUT_LENGTH) },
                            label = { Text("About (WhatsApp-style)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_about_input")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            aboutPresets.forEach { preset ->
                                FilterChip(
                                    selected = about == preset,
                                    onClick = { about = preset },
                                    label = { Text(preset, style = MaterialTheme.typography.labelMedium) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ChatboxSoftLavender,
                                        selectedLabelColor = ChatboxPrimary
                                    )
                                )
                            }
                        }

                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it.take(ChatboxConstants.MAX_PHONE_LENGTH) },
                            label = { Text("Phone number (optional)") },
                            placeholder = { Text("+8801XXXXXXXXX") },
                            leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_phone_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text("Close")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                enabled = !saving && (!hasCloudUid || isOnline) && displayName.isNotBlank(),
                                onClick = {
                                    if (!hasCloudUid) {
                                        onSaveLocalProfile(displayName, about, phoneNumber)
                                        feedback = "Profile saved locally on this device!" to false
                                    } else {
                                        scope.launch {
                                            saving = true
                                            feedback = null
                                            liveChatRepo.saveOwnWhatsAppProfile(
                                                uid = uid,
                                                currentShortId = shortId,
                                                displayName = displayName,
                                                about = about,
                                                phoneNumber = phoneNumber,
                                                previousPhoneNumber = savedPhone
                                            ).onSuccess { (updatedProfile, cleanPhone) ->
                                                shortId = updatedProfile.shortId
                                                phoneNumber = cleanPhone
                                                savedPhone = cleanPhone
                                                onProfileUpdated(updatedProfile)
                                                feedback = "Profile saved! Friends can add you with your 6-digit ID or phone number." to false
                                            }.onFailure { err ->
                                                feedback = friendlyFirestoreMessage(err) to true
                                            }
                                            saving = false
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("profile_save_button")
                            ) {
                                Text(if (saving) "Saving…" else "Save Profile")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomMenuContentScreen(
    menu: SparkCustomMenu,
    appUpdateActive: Boolean,
    onAppUpdateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalChatboxColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ChatboxLogoWithUpdateBadge(
                        appUpdateActive = appUpdateActive,
                        onUpdateClick = onAppUpdateClick,
                        logoSize = 36.dp
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ChatboxSoftLavender),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = customMenuIconVector(menu.iconEnum),
                            contentDescription = menu.title,
                            tint = ChatboxPrimary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = menu.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkSubtle
                        )
                        Text(
                            text = menu.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.ink
                        )
                    }
                }
                Text(
                    text = menu.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.inkMuted
                )
            }
        }
    }
}
