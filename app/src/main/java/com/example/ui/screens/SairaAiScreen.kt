package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AiExchange
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
    appUpdateActive: Boolean = false,
    onAppUpdateClick: () -> Unit = {},
    onSignIn: () -> Unit,
    onOpenOfflineDemo: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSubmitQuestion: () -> Unit
) {
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
                            text = "YOUR APP COMPANION",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                        Text(
                            text = "Saira AI",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "EXPERIMENTAL",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                        Text(
                            text = "Answers based on reviewed app knowledge.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(12.dp))

            if (!isSignedIn) {
                SignInRequiredCard(
                    title = "Sign in to talk to Saira.",
                    body = "AI questions require a signed-in Google account. The offline Saira guide remains available in Demo.",
                    icon = Icons.Rounded.AutoAwesome,
                    isBusy = authBusy,
                    onSignIn = onSignIn,
                    secondaryButtonLabel = "Open offline guide in Demo",
                    onSecondaryClick = onOpenOfflineDemo,
                    testTagPrefix = "saira_ai"
                )
            } else {
                ChatboxNoticeBanner(
                    text = "Your question is sent to Google Gemini via Firebase AI Logic. Do not send private or sensitive information. Answers may be inaccurate."
                )

                if (uiState.error.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ChatboxNoticeBanner(text = uiState.error, isError = true)
                }

                Spacer(modifier = Modifier.height(12.dp))

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
                                    Text(
                                        text = "Ask about Chatbox features, development, or updates.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                                    enabled = !uiState.isBusy,
                                    placeholder = { Text("Ask Saira...") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = { onSubmitQuestion() }),
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
                                val canSend = !uiState.isBusy && uiState.draft.trim().isNotEmpty()
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
                                    "Saira is thinking…"
                                } else {
                                    "Only the question you submit is sent. Other chats are not shared."
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
