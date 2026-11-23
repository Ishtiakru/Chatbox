package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.model.AiExchange
import com.example.notifications.SairaNotificationListenerService
import com.example.saira.SairaNotificationRule
import com.example.saira.SairaPlatform
import com.example.ui.components.ChatboxLogoWithUpdateBadge
import com.example.ui.components.ChatboxNoticeBanner
import com.example.ui.components.SignInRequiredCard
import com.example.ui.theme.ChatboxIncomingBubble
import com.example.ui.theme.ChatboxIncomingText
import com.example.ui.theme.ChatboxOutgoingBubble
import com.example.ui.theme.ChatboxOutgoingText
import com.example.ui.viewmodel.SairaAiUiState

@Composable
fun SairaAiScreen(
    isSignedIn: Boolean,
    authBusy: Boolean,
    uiState: SairaAiUiState,
    isOnline: Boolean = true,
    appUpdateActive: Boolean = false,
    onAppUpdateClick: () -> Unit = {},
    onSignIn: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSubmitQuestion: () -> Unit,
    onResetChat: () -> Unit = {},
    onToggleChatboxOneMinAutoReply: (Boolean) -> Unit = {},
    onToggleNotificationAutoReply: (Boolean) -> Unit = {},
    onRuleContactChange: (String) -> Unit = {},
    onRulePlatformChange: (SairaPlatform) -> Unit = {},
    onRuleGuidanceChange: (String) -> Unit = {},
    onAddNotificationRule: () -> Unit = {},
    onToggleNotificationRule: (String, Boolean) -> Unit = { _, _ -> },
    onDeleteNotificationRule: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var notificationAccessGranted by remember {
        mutableStateOf(SairaNotificationListenerService.Helper.isNotificationAccessGranted(context))
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccessGranted =
                    SairaNotificationListenerService.Helper.isNotificationAccessGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChatboxLogoWithUpdateBadge(
                        appUpdateActive = appUpdateActive,
                        onUpdateClick = onAppUpdateClick,
                        logoSize = 34.dp,
                        badgeTestTag = "saira_app_update_badge"
                    )
                    Column {
                        Text(
                            text = "FREE UNLIMITED ASSISTANT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Saira Assistant",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "UNLIMITED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text(
                            text = "Replies after your message · Time-aware greetings & auto-reply",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isSignedIn && uiState.exchanges.isNotEmpty() && selectedTab == 0) {
                    IconButton(
                        onClick = onResetChat,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("saira_reset_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(id = R.string.cd_saira_reset_chat),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!isSignedIn) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(12.dp))
                SignInRequiredCard(
                    title = "Sign in to talk to Saira Assistant.",
                    body = "AI questions require a signed-in Google account.",
                    icon = Icons.Rounded.AutoAwesome,
                    isBusy = authBusy,
                    onSignIn = onSignIn,
                    testTagPrefix = "saira_ai"
                )
            } else {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Assistant Chat") },
                        icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("saira_tab_chat")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            val count = uiState.notificationRules.count { it.enabled }
                            Text(if (count > 0) "Auto-Reply ($count)" else "Auto-Reply")
                        },
                        icon = { Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("saira_tab_auto_reply")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedTab == 0) {
                    SairaChatTab(
                        uiState = uiState,
                        isOnline = isOnline,
                        onDraftChange = onDraftChange,
                        onSubmitQuestion = onSubmitQuestion,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                } else {
                    SairaAutoReplyTab(
                        uiState = uiState,
                        notificationAccessGranted = notificationAccessGranted,
                        onOpenNotificationSettings = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        onToggleChatboxOneMinAutoReply = onToggleChatboxOneMinAutoReply,
                        onToggleNotificationAutoReply = onToggleNotificationAutoReply,
                        onRuleContactChange = onRuleContactChange,
                        onRulePlatformChange = onRulePlatformChange,
                        onRuleGuidanceChange = onRuleGuidanceChange,
                        onAddNotificationRule = onAddNotificationRule,
                        onToggleNotificationRule = onToggleNotificationRule,
                        onDeleteNotificationRule = onDeleteNotificationRule,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SairaChatTab(
    uiState: SairaAiUiState,
    isOnline: Boolean,
    onDraftChange: (String) -> Unit,
    onSubmitQuestion: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (!isOnline) {
            ChatboxNoticeBanner(
                text = "Offline · Saira Assistant is disabled without internet. Offline mode supports text SMS in Messages only.",
                isError = true,
                modifier = Modifier.testTag("saira_offline_banner")
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        ChatboxNoticeBanner(
            text = "Free lifetime AI via Firebase AI Logic (Gemini). Saira never sends a message first — she replies only after you write."
        )

        if (uiState.error.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            ChatboxNoticeBanner(text = uiState.error, isError = true)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val listState = rememberLazyListState()
                LaunchedEffect(uiState.exchanges.size, uiState.exchanges.lastOrNull()?.answer?.length) {
                    if (uiState.exchanges.isNotEmpty()) {
                        listState.animateScrollToItem(uiState.exchanges.lastIndex)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (uiState.exchanges.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp)
                                )
                                Text(
                                    text = "Saira Assistant is quiet until you message first.",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Send any question in Bangla or English. Saira will greet you by time of day (Morning, Afternoon, Evening) and say Good Night when wrapping up at night.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(uiState.exchanges, key = { it.id }) { exchange ->
                            SairaExchangeCard(exchange = exchange)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.draft,
                            onValueChange = onDraftChange,
                            enabled = isOnline && !uiState.isBusy,
                            placeholder = {
                                Text(if (isOnline) "Message Saira Assistant…" else "Offline · Saira disabled")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = { if (isOnline) onSubmitQuestion() }
                            ),
                            shape = RoundedCornerShape(22.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("saira_ai_input")
                        )
                        val canSend = isOnline && !uiState.isBusy && uiState.draft.trim().isNotEmpty()
                        Surface(
                            onClick = onSubmitQuestion,
                            enabled = canSend,
                            shape = CircleShape,
                            color = if (canSend) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("saira_ai_send_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (uiState.isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowUpward,
                                        contentDescription = stringResource(id = R.string.cd_send),
                                        tint = if (canSend) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = if (uiState.isBusy) {
                            "Saira Assistant is typing…"
                        } else {
                            "Unlimited free chat · Only messages you send here go to Gemini."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SairaAutoReplyTab(
    uiState: SairaAiUiState,
    notificationAccessGranted: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onToggleChatboxOneMinAutoReply: (Boolean) -> Unit,
    onToggleNotificationAutoReply: (Boolean) -> Unit,
    onRuleContactChange: (String) -> Unit,
    onRulePlatformChange: (SairaPlatform) -> Unit,
    onRuleGuidanceChange: (String) -> Unit,
    onAddNotificationRule: () -> Unit,
    onToggleNotificationRule: (String, Boolean) -> Unit,
    onDeleteNotificationRule: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Chatbox 1-Min Absence Auto-Reply",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "If a friend hasn't replied in Chatbox for 1 minute, Saira greets you by name and assists while you wait.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.chatboxOneMinAutoReplyEnabled,
                    onCheckedChange = onToggleChatboxOneMinAutoReply,
                    modifier = Modifier.testTag("saira_one_min_switch")
                )
            }
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "WhatsApp & Messenger Auto-Reply",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Replies ONLY to contact names you teach below — never to strangers or unlisted chats.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.notificationAutoReplyEnabled,
                        onCheckedChange = onToggleNotificationAutoReply,
                        modifier = Modifier.testTag("saira_notif_reply_switch")
                    )
                }

                if (!notificationAccessGranted) {
                    ChatboxNoticeBanner(
                        text = "Notification access is currently off. Grant Android Notification Access so Saira can read & reply to approved WhatsApp/Messenger notifications.",
                        isError = false
                    )
                    OutlinedButton(
                        onClick = onOpenNotificationSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("saira_grant_notif_access_button")
                    ) {
                        Text("Open Android Notification Access")
                    }
                } else {
                    ChatboxNoticeBanner(
                        text = "Notification Access granted · Listening for taught WhatsApp & Messenger contacts."
                    )
                }
            }
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Teach Saira a Contact Rule",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedTextField(
                    value = uiState.ruleContactDraft,
                    onValueChange = onRuleContactChange,
                    label = { Text("Contact name (e.g., Rahima)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("saira_rule_contact_input")
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SairaPlatform.entries.forEach { platform ->
                        FilterChip(
                            selected = uiState.rulePlatformDraft == platform,
                            onClick = { onRulePlatformChange(platform) },
                            label = { Text(platform.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = uiState.ruleGuidanceDraft,
                    onValueChange = onRuleGuidanceChange,
                    label = { Text("What should Saira reply when they message?") },
                    placeholder = { Text("e.g., Tell them I am busy right now and will call back soon.") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("saira_rule_guidance_input")
                )
                val canAddRule = uiState.ruleContactDraft.trim().isNotEmpty() &&
                    uiState.ruleGuidanceDraft.trim().isNotEmpty()
                Button(
                    onClick = onAddNotificationRule,
                    enabled = canAddRule,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("saira_add_rule_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Save Reply Rule for This Contact")
                }
            }
        }

        if (uiState.notificationRules.isNotEmpty()) {
            Text(
                text = "TAUGHT CONTACT RULES (${uiState.notificationRules.size})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            uiState.notificationRules.forEach { rule ->
                SairaRuleCard(
                    rule = rule,
                    onToggle = { enabled -> onToggleNotificationRule(rule.id, enabled) },
                    onDelete = { onDeleteNotificationRule(rule.id) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SairaRuleCard(
    rule: SairaNotificationRule,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = rule.contactName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = rule.platform.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = rule.taughtReplyGuidance,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggle
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(id = R.string.cd_saira_delete_rule),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SairaExchangeCard(exchange: AiExchange) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                color = ChatboxOutgoingBubble,
                modifier = Modifier.widthIn(max = 460.dp)
            ) {
                Text(
                    text = exchange.question,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChatboxOutgoingText,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                color = ChatboxIncomingBubble,
                modifier = Modifier.widthIn(max = 520.dp)
            ) {
                Text(
                    text = if (exchange.answer.isBlank() && exchange.isStreaming) "…" else exchange.answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChatboxIncomingText,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}
