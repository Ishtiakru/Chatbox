package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.media.VoicePlaybackState
import java.util.Locale
import kotlin.math.abs

private val WAVEFORM_PATTERN = floatArrayOf(
    0.35f, 0.65f, 0.9f, 0.5f, 0.8f, 0.4f, 0.95f, 0.7f,
    0.45f, 0.85f, 0.6f, 1.0f, 0.55f, 0.75f, 0.4f, 0.85f,
    0.65f, 0.5f, 0.9f, 0.45f, 0.7f, 0.55f, 0.4f, 0.35f
)

internal fun formatAudioDuration(durationMs: Long): String {
    val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format(Locale.ENGLISH, "%d:%02d", mins, secs)
}

@Composable
internal fun LiveVoiceRecordingBar(
    elapsedMs: Long,
    amplitude: Float,
    onCancel: () -> Unit,
    onStopToPreview: () -> Unit,
    onSendNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_rec_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("live_voice_cancel_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = stringResource(id = R.string.cd_cancel_voice_recording),
                tint = MaterialTheme.colorScheme.error
            )
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .alpha(pulseAlpha)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                )
                Text(
                    text = "${formatAudioDuration(elapsedMs)} / 1:00",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally)
                ) {
                    val activeAmp = amplitude.coerceIn(0.15f, 1f)
                    for (idx in 0 until 12) {
                        val waveFactor = if (idx % 2 == 0) activeAmp else (activeAmp * 0.65f + 0.15f)
                        val barHeightDp = (6f + 14f * ((waveFactor + (idx % 3) * 0.15f).coerceAtMost(1f))).dp
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(barHeightDp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onStopToPreview,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("live_voice_stop_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Stop,
                contentDescription = stringResource(id = R.string.cd_stop_voice_recording),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Surface(
            onClick = onSendNow,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(48.dp)
                .testTag("live_voice_send_now_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = stringResource(id = R.string.cd_send),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
internal fun LivePendingVoicePreviewCard(
    voicePath: String,
    durationMs: Long,
    uploadingVoice: Boolean,
    uploadProgress: Float,
    playbackState: VoicePlaybackState,
    onTogglePlay: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = playbackState.activeKey == "pending_voice"
    val isPlaying = isActive && playbackState.isPlaying
    val isBuffering = isActive && playbackState.isBuffering
    val shownDuration = if (isActive && playbackState.isPlaying) {
        playbackState.positionMs
    } else {
        durationMs
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = onTogglePlay,
                    enabled = !uploadingVoice,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(44.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("live_pending_voice_play_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        when {
                            isBuffering -> CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            isPlaying -> Icon(
                                imageVector = Icons.Rounded.Pause,
                                contentDescription = stringResource(id = R.string.cd_pause_voice_note),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                            else -> Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(id = R.string.cd_play_voice_note),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (uploadingVoice) {
                            "Uploading voice note… ${(uploadProgress * 100).toInt()}%"
                        } else {
                            "Voice note · ${formatAudioDuration(shownDuration)}"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (uploadingVoice) {
                            "Hosting audio securely in Firebase Storage"
                        } else {
                            "Tap Play to preview or tap Send"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!uploadingVoice) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("live_remove_voice_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(id = R.string.cd_remove_voice_note),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (uploadingVoice) {
                LinearProgressIndicator(
                    progress = { uploadProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
internal fun LiveVoiceNoteBubblePlayer(
    messageId: String,
    audioUrl: String,
    durationMs: Long?,
    isOwn: Boolean,
    playbackState: VoicePlaybackState,
    onTogglePlay: () -> Unit
) {
    val isActive = playbackState.activeKey == messageId
    val isBuffering = isActive && playbackState.isBuffering
    val isPlaying = isActive && playbackState.isPlaying
    val effectiveDuration = (if (isActive && playbackState.durationMs > 0L) playbackState.durationMs else durationMs ?: 0L)
        .coerceAtLeast(1L)
    val progress = if (isActive && effectiveDuration > 0L) {
        (playbackState.positionMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val barSeed = remember(messageId) { abs(messageId.hashCode()) % WAVEFORM_PATTERN.size }
    val primaryTint = if (isOwn) Color.White else MaterialTheme.colorScheme.primary
    val trackTint = if (isOwn) Color.White.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val labelTint = if (isOwn) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant

    val shownMs = when {
        isActive && (isPlaying || playbackState.positionMs > 0L) -> playbackState.positionMs
        durationMs != null && durationMs > 0L -> durationMs
        isActive && playbackState.durationMs > 0L -> playbackState.durationMs
        else -> 0L
    }

    Row(
        modifier = Modifier
            .widthIn(min = 210.dp, max = 265.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            onClick = onTogglePlay,
            shape = CircleShape,
            color = if (isOwn) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .size(40.dp)
                .minimumInteractiveComponentSize()
                .testTag("live_voice_play_$messageId")
        ) {
            Box(contentAlignment = Alignment.Center) {
                when {
                    isBuffering -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = primaryTint
                    )
                    isPlaying -> Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = stringResource(id = R.string.cd_pause_voice_note),
                        tint = primaryTint
                    )
                    else -> Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(id = R.string.cd_play_voice_note),
                        tint = primaryTint
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val barCount = 20
                for (i in 0 until barCount) {
                    val sample = WAVEFORM_PATTERN[(i + barSeed) % WAVEFORM_PATTERN.size]
                    val filled = isActive && (i.toFloat() / barCount.toFloat()) <= progress
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height((6f + 15f * sample).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (filled) primaryTint else trackTint)
                    )
                }
            }
            Text(
                text = formatAudioDuration(shownMs),
                style = MaterialTheme.typography.labelSmall,
                color = labelTint
            )
        }
    }
}
