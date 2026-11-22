package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AvatarStyle
import com.example.data.model.DemoCatalog
import com.example.data.model.DemoContact
import com.example.ui.theme.AvatarBlueEnd
import com.example.ui.theme.AvatarBlueInk
import com.example.ui.theme.AvatarBlueStart
import com.example.ui.theme.AvatarLavenderEnd
import com.example.ui.theme.AvatarLavenderInk
import com.example.ui.theme.AvatarLavenderStart
import com.example.ui.theme.AvatarPeachEnd
import com.example.ui.theme.AvatarPeachInk
import com.example.ui.theme.AvatarPeachStart
import com.example.ui.theme.AvatarYellowEnd
import com.example.ui.theme.AvatarYellowInk
import com.example.ui.theme.AvatarYellowStart
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.SparkAppUpdate
import com.example.ui.theme.ChatboxButtonPurple
import com.example.ui.theme.ChatboxOnlineDot
import com.example.ui.theme.ChatboxUpdateGreenBg
import com.example.ui.viewmodel.DemoModal

@Composable
fun DemoAvatar(
    contact: DemoContact,
    size: Dp,
    showOnlineDot: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (brush, inkColor) = when (contact.style) {
        AvatarStyle.SAIRA -> Brush.linearGradient(
            listOf(Color(0xFF7D68B8), ChatboxButtonPurple)
        ) to Color.White
        AvatarStyle.PEACH -> Brush.linearGradient(
            listOf(AvatarPeachStart, AvatarPeachEnd)
        ) to AvatarPeachInk
        AvatarStyle.LAVENDER -> Brush.linearGradient(
            listOf(AvatarLavenderStart, AvatarLavenderEnd)
        ) to AvatarLavenderInk
        AvatarStyle.YELLOW -> Brush.linearGradient(
            listOf(AvatarYellowStart, AvatarYellowEnd)
        ) to AvatarYellowInk
        AvatarStyle.BLUE -> Brush.linearGradient(
            listOf(AvatarBlueStart, AvatarBlueEnd)
        ) to AvatarBlueInk
    }

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(brush),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.avatar,
                color = inkColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.36f).sp
            )
        }
        if (showOnlineDot && contact.online) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size * 0.28f).coerceAtLeast(10.dp))
                    .clip(CircleShape)
                    .background(ChatboxOnlineDot)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
fun MessengerAvatar(
    label: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val initial = label.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(AvatarLavenderStart, MaterialTheme.colorScheme.secondaryContainer)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.42f).sp
        )
    }
}

@Composable
fun ChatboxNoticeBanner(
    text: String,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isError) Icons.Rounded.WarningAmber else Icons.Rounded.Info,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            if (actionLabel != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(actionLabel, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun SignInRequiredCard(
    title: String,
    body: String,
    icon: ImageVector,
    isBusy: Boolean,
    onSignIn: () -> Unit,
    secondaryButtonLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    testTagPrefix: String = "auth"
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 420.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onSignIn,
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("${testTagPrefix}_sign_in_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Signing in…")
                    } else {
                        Text("Sign in with Google")
                    }
                }
                if (secondaryButtonLabel != null && onSecondaryClick != null) {
                    TextButton(
                        onClick = onSecondaryClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("${testTagPrefix}_secondary_button")
                    ) {
                        Text(secondaryButtonLabel)
                    }
                }
            }
        }
    }
}

@Composable
fun DemoInfoModalDialog(
    modal: DemoModal,
    onDismiss: () -> Unit
) {
    val (title, body) = when (modal) {
        DemoModal.VOICE -> "Voice calls are on the way" to
            "Calling is not connected in this prototype. A real call requires authentication, signaling and a calling service."
        DemoModal.VIDEO -> "A little closer, soon" to
            "Video calling is not connected yet. We’ll need a secure calling service and camera permissions to make this work."
        DemoModal.ATTACH -> "Attachments are coming later" to
            "Files cannot be uploaded or sent in this local demo."
        DemoModal.NEW_CHAT -> "Your people, in one place" to
            "Adding real contacts requires Firebase authentication and a connected database. Switch to Live chats to connect with a Chatbox ID."
        DemoModal.ABOUT -> "A little note about this space" to
            "Chatbox (v${DemoCatalog.APP_VERSION}) includes local demo conversations, live Firestore chats, Saira AI via Firebase AI Logic, and Spark Admin studio."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(title) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("demo_modal_dismiss_button")
            ) {
                Text("Got it")
            }
        }
    )
}

@Composable
fun AuthErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("সাইন-ইন করা যায়নি") },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("auth_error_dismiss_button")
            ) {
                Text("Got it")
            }
        }
    )
}

@Composable
fun ConversationSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(id = R.string.search_conversations_placeholder)
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = {
            Text(
                text = placeholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = if (query.isNotEmpty()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("${testTag}_clear")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(id = R.string.cd_clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}

fun highlightSearchMatch(
    text: String,
    query: String,
    highlightColor: Color,
    highlightBackground: Color
): AnnotatedString {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        var startIndex = 0
        while (startIndex < text.length) {
            val matchIndex = text.indexOf(trimmed, startIndex = startIndex, ignoreCase = true)
            if (matchIndex < 0) break
            addStyle(
                style = SpanStyle(
                    color = highlightColor,
                    background = highlightBackground,
                    fontWeight = FontWeight.SemiBold
                ),
                start = matchIndex,
                end = matchIndex + trimmed.length
            )
            startIndex = matchIndex + trimmed.length
        }
    }
}

@Composable
fun AppUpdateBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "app_update_badge"
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = ChatboxUpdateGreenBg
        ) {
            Text(
                text = "update",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun ChatboxLogoWithUpdateBadge(
    appUpdateActive: Boolean,
    onUpdateClick: () -> Unit,
    modifier: Modifier = Modifier,
    logoSize: Dp = 34.dp,
    badgeTestTag: String = "app_update_badge"
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.chatbox_logo_lavender),
            contentDescription = stringResource(id = R.string.cd_chatbox_logo),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(logoSize)
                .clip(CircleShape)
        )
        if (appUpdateActive) {
            AppUpdateBadge(
                onClick = onUpdateClick,
                testTag = badgeTestTag
            )
        }
    }
}

@Composable
fun AppUpdateDetailsDialog(
    appUpdate: SparkAppUpdate,
    currentVersion: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hasSafeUrl = appUpdate.downloadUrl.startsWith("https://")
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ChatboxUpdateGreenBg
            ) {
                Text(
                    text = "UPDATE",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        },
        title = { Text("Chatbox v${appUpdate.version} available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Installed version: v$currentVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (appUpdate.note.isNotBlank()) {
                    Text(
                        text = appUpdate.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = "A newer version of Chatbox has been published.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            if (hasSafeUrl) {
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(appUpdate.downloadUrl))
                            )
                        }
                        onDismiss()
                    },
                    modifier = Modifier.testTag("app_update_open_link_button")
                ) {
                    Text("Get update")
                }
            } else {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("app_update_dismiss_button")
                ) {
                    Text("Got it")
                }
            }
        },
        dismissButton = if (hasSafeUrl) {
            {
                TextButton(onClick = onDismiss) {
                    Text("Later")
                }
            }
        } else {
            null
        }
    )
}
