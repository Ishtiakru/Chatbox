package com.example.ui.components

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ChatboxErrorInk
import com.example.ui.theme.ChatboxPrimary
import com.example.ui.theme.ChatboxSoftLavender
import com.example.ui.theme.LocalChatboxColors
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

fun formatDurationMs(ms: Long?): String {
    val totalSec = ((ms ?: 0L).coerceAtLeast(0L) + 500L) / 1000L
    val m = totalSec / 60L
    val s = totalSec % 60L
    return String.format(Locale.US, "%d:%02d", m, s)
}

class VoiceNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startedAtMs: Long = 0L

    fun start(): File? {
        stop()
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        return try {
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(96_000)
            mr.setAudioSamplingRate(44_100)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            currentFile = file
            startedAtMs = System.currentTimeMillis()
            file
        } catch (_: Exception) {
            runCatching { file.delete() }
            null
        }
    }

    fun stop(): Pair<File?, Long> {
        val elapsed = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
        val file = currentFile
        runCatching {
            recorder?.stop()
        }
        runCatching {
            recorder?.release()
        }
        recorder = null
        currentFile = null
        return file to elapsed
    }
}

@Composable
fun VoiceRecordingBar(
    elapsedMs: Long,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = LocalChatboxColors.current
    Surface(
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth()
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
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(ChatboxErrorInk)
            )
            Text(
                text = "Recording voice note · ${formatDurationMs(elapsedMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCancel) {
                Icon(Icons.Rounded.Close, contentDescription = "Cancel recording", tint = colors.inkSubtle)
            }
            Surface(
                onClick = onStop,
                shape = CircleShape,
                color = ChatboxPrimary,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("voice_stop_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Stop, contentDescription = "Stop recording", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun VoiceNoteBubblePlayer(
    audioUrl: String,
    durationMs: Long?,
    isMe: Boolean
) {
    val colors = LocalChatboxColors.current
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(audioUrl) {
        onDispose {
            runCatching { mediaPlayer?.release() }
            mediaPlayer = null
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val mp = mediaPlayer
            if (mp != null && mp.isPlaying && mp.duration > 0) {
                progress = (mp.currentPosition.toFloat() / mp.duration.toFloat()).coerceIn(0f, 1f)
            }
            delay(150)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Surface(
            onClick = {
                if (isPlaying) {
                    runCatching { mediaPlayer?.pause() }
                    isPlaying = false
                } else {
                    val existing = mediaPlayer
                    if (existing != null) {
                        runCatching {
                            existing.start()
                            isPlaying = true
                        }
                    } else {
                        val mp = MediaPlayer()
                        mediaPlayer = mp
                        runCatching {
                            mp.setDataSource(audioUrl)
                            mp.setOnPreparedListener { prepared ->
                                prepared.start()
                                isPlaying = true
                            }
                            mp.setOnCompletionListener {
                                isPlaying = false
                                progress = 0f
                            }
                            mp.setOnErrorListener { _, _, _ ->
                                isPlaying = false
                                true
                            }
                            mp.prepareAsync()
                        }
                    }
                }
            },
            shape = CircleShape,
            color = if (isMe) Color.White else ChatboxSoftLavender,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause voice note" else "Play voice note",
                    tint = ChatboxPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        val fgTint = if (isMe) Color.White else ChatboxPrimary
        val trackTint = if (isMe) Color.White.copy(alpha = 0.32f) else colors.border
        val timeTint = if (isMe) Color.White.copy(alpha = 0.9f) else colors.inkMuted
        Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = fgTint,
            modifier = Modifier.size(18.dp)
        )
        LinearProgressIndicator(
            progress = { progress },
            color = fgTint,
            trackColor = trackTint,
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        )
        Text(
            text = formatDurationMs(durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = timeTint
        )
    }
}
