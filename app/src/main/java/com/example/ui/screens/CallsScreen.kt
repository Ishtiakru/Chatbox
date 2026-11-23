package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.ChatboxLogoWithUpdateBadge
import com.example.ui.components.NoticeBanner
import com.example.ui.theme.ChatboxPrimary
import com.example.ui.theme.ChatboxSoftLavender
import com.example.ui.theme.LocalChatboxColors

@Composable
fun CallsScreen(
    isOnline: Boolean,
    appUpdateActive: Boolean,
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
                text = "Offline · Voice and video calls require internet connection.",
                isError = true,
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .padding(bottom = 12.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(156.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(144.dp)
                        .scale(pulse)
                        .border(2.dp, ChatboxPrimary.copy(alpha = 0.25f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(106.dp)
                        .border(1.5.dp, ChatboxPrimary.copy(alpha = 0.18f), CircleShape)
                )
                Surface(
                    color = ChatboxSoftLavender,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, ChatboxPrimary.copy(alpha = 0.2f)),
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Videocam,
                            contentDescription = "Planned Video Calls",
                            tint = ChatboxPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            Text(
                text = "A LITTLE SOMETHING TO LOOK FORWARD TO",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkSubtle,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Closer than a message.",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.ink,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Voice and video calling are planned, but not connected yet. Return to Live chats for real-time messages, compressed images, and voice notes.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkMuted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onBackToConversations,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ChatboxPrimary),
                modifier = Modifier.testTag("calls_back_to_conversations")
            ) {
                Text("Back to conversations")
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
