package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.PhoneMissed
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ChatboxCallEntry
import com.example.ui.components.ChatboxLogoWithUpdateBadge
import com.example.ui.components.MessengerAvatar
import com.example.ui.components.NoticeBanner
import com.example.ui.theme.ChatboxErrorInk
import com.example.ui.theme.ChatboxPrimary
import com.example.ui.theme.ChatboxSoftLavender
import com.example.ui.theme.LocalChatboxColors

@Composable
fun CallsScreen(
    isOnline: Boolean,
    appUpdateActive: Boolean,
    callLogs: List<ChatboxCallEntry> = emptyList(),
    onOpenThread: (String) -> Unit = {},
    onAppUpdateClick: () -> Unit,
    onBackToConversations: () -> Unit
) {
    val colors = LocalChatboxColors.current
    val pulse by rememberInfiniteTransition(label = "calls_ring").animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "ring_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ChatboxLogoWithUpdateBadge(
                    appUpdateActive = appUpdateActive,
                    onUpdateClick = onAppUpdateClick,
                    logoSize = 34.dp
                )
                Column {
                    Text(
                        text = "STAY CLOSE, FROM ANYWHERE",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkSubtle
                    )
                    Text(
                        text = stringResource(R.string.calls_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.ink
                    )
                }
            }
            Surface(
                color = ChatboxSoftLavender,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PhoneInTalk, contentDescription = "Calls", tint = ChatboxPrimary)
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .padding(vertical = 12.dp),
            color = colors.border
        )

        if (!isOnline) {
            NoticeBanner(
                text = "Offline · Internet is paused, recent device call logs stay saved.",
                isError = false,
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .padding(bottom = 12.dp)
            )
        }

        if (callLogs.isNotEmpty()) {
            Column(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Recent calls",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                callLogs.forEach { call ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onOpenThread(call.threadId) }
                            .testTag("call_log_${call.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MessengerAvatar(label = call.peerName, size = 44.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = call.peerName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (call.isMissed) ChatboxErrorInk else colors.ink
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = when {
                                            call.isMissed -> Icons.AutoMirrored.Rounded.PhoneMissed
                                            call.isVideo -> Icons.Rounded.Videocam
                                            else -> Icons.Rounded.Phone
                                        },
                                        contentDescription = null,
                                        tint = if (call.isMissed) ChatboxErrorInk else ChatboxPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${if (call.isVideo) "Video" else "Audio"} · ${call.durationLabel} · ${call.timeLabel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.inkMuted
                                    )
                                }
                            }
                            Surface(
                                color = ChatboxSoftLavender,
                                shape = CircleShape,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (call.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Phone,
                                        contentDescription = "Open DM",
                                        tint = ChatboxPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        } else {
            Spacer(Modifier.height(28.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(136.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .scale(pulse)
                        .border(2.dp, ChatboxPrimary.copy(alpha = 0.25f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .border(1.5.dp, ChatboxPrimary.copy(alpha = 0.18f), CircleShape)
                )
                Surface(
                    color = ChatboxSoftLavender,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, ChatboxPrimary.copy(alpha = 0.2f)),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Videocam,
                            contentDescription = "Voice and Video Calls",
                            tint = ChatboxPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Text(
                text = "Start a voice or video call from any DM",
                style = MaterialTheme.typography.titleLarge,
                color = colors.ink,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Tap the Phone or Video icon inside any Chatbox conversation or Details screen to place or log a call.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkMuted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onBackToConversations,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ChatboxPrimary),
                modifier = Modifier.testTag("calls_back_to_conversations")
            ) {
                Text("Back to Chatbox")
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
