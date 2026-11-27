package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.call.CallEngine
import com.example.ui.components.MessengerAvatar
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/** Human status for the media path of a connected call. */
internal fun mediaStatusLabel(mediaState: String, timer: String): String = when (mediaState) {
    "connected" -> timer
    "reconnecting" -> "আবার সংযোগ হচ্ছে…"
    "failed" -> "সংযোগ হয়নি — ইন্টারনেট চেক করুন"
    else -> "সংযোগ হচ্ছে…"
}

/** Keeps the screen awake while a call UI is visible. */
@Composable
internal fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Turns the screen off while the phone is near the ear (audio calls), like the phone app. */
@Composable
internal fun ProximityScreenOff() {
    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(Unit) {
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        val lock = runCatching {
            if (pm != null && pm.isWakeLockLevelSupported(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                pm.newWakeLock(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "chatbox:call-proximity")
                    .apply { setReferenceCounted(false); acquire(4 * 60 * 60 * 1000L) }
            } else null
        }.getOrNull()
        onDispose {
            runCatching {
                if (lock?.isHeld == true) lock.release(android.os.PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
            }
        }
    }
}

@Composable
internal fun WebRtcVideoView(
    track: VideoTrack?,
    egl: EglBase,
    mirror: Boolean,
    onTop: Boolean,
    modifier: Modifier = Modifier
) {
    val holder = remember { arrayOfNulls<SurfaceViewRenderer>(1) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(egl.eglBaseContext, null)
                setMirror(mirror)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                if (onTop) setZOrderMediaOverlay(true)
                holder[0] = this
            }
        },
        onRelease = { view ->
            holder[0] = null
            runCatching { view.release() }
        }
    )
    DisposableEffect(track) {
        val renderer = holder[0]
        if (track != null && renderer != null) runCatching { track.addSink(renderer) }
        onDispose {
            if (track != null && renderer != null) runCatching { track.removeSink(renderer) }
        }
    }
}

@Composable
internal fun CallControlButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    background: Color = if (active) Color.White else Color.White.copy(alpha = 0.18f),
    tint: Color = if (active) Color(0xFF1E1930) else Color.White,
    testTag: String = ""
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = background,
        modifier = Modifier
            .size(52.dp)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = tint)
        }
    }
}

/** Mic / speaker (+ camera controls for video) for a live call. */
@Composable
internal fun CallControlsRow(
    engine: CallEngine,
    showVideoControls: Boolean,
    onEnd: () -> Unit
) {
    val muted by engine.micMuted.collectAsState()
    val speaker by engine.speakerOn.collectAsState()
    val cameraOff by engine.cameraOff.collectAsState()
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CallControlButton(
            icon = if (muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
            label = if (muted) "Unmute" else "Mute",
            active = muted,
            onClick = engine::toggleMic,
            testTag = "call_toggle_mic"
        )
        CallControlButton(
            icon = if (speaker) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
            label = "Speaker",
            active = speaker,
            onClick = engine::toggleSpeaker,
            testTag = "call_toggle_speaker"
        )
        if (showVideoControls) {
            CallControlButton(
                icon = if (cameraOff) Icons.Rounded.VideocamOff else Icons.Rounded.Videocam,
                label = "Camera",
                active = cameraOff,
                onClick = engine::toggleCamera,
                testTag = "call_toggle_camera"
            )
            CallControlButton(
                icon = Icons.Rounded.Cameraswitch,
                label = "Switch camera",
                active = false,
                onClick = engine::switchCamera,
                testTag = "call_switch_camera"
            )
        }
        CallControlButton(
            icon = Icons.Rounded.CallEnd,
            label = "End call",
            active = false,
            onClick = onEnd,
            background = Color(0xFFE53935),
            tint = Color.White,
            testTag = "calls_dialog_end"
        )
    }
}

/** Full-screen WhatsApp-style video call: peer full screen, own camera in a corner. */
@Composable
internal fun InCallVideoScreen(
    engine: CallEngine,
    peerName: String,
    statusText: String,
    onEnd: () -> Unit,
    onDismiss: () -> Unit
) {
    val remote by engine.remoteVideo.collectAsState()
    val local by engine.localVideo.collectAsState()
    val cameraOff by engine.cameraOff.collectAsState()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        KeepScreenOn()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E0B18))
                .testTag("calls_active_call_dialog")
        ) {
            if (remote != null) {
                WebRtcVideoView(
                    track = remote,
                    egl = engine.eglBase,
                    mirror = false,
                    onTop = false,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MessengerAvatar(label = peerName, size = 96.dp, showOnlineDot = false)
                }
            }
            if (local != null && !cameraOff) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 16.dp)
                        .size(width = 110.dp, height = 160.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    WebRtcVideoView(
                        track = local,
                        egl = engine.eglBase,
                        mirror = true,
                        onTop = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 20.dp, top = 52.dp)
            ) {
                Text(
                    text = peerName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = statusText,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                CallControlsRow(engine = engine, showVideoControls = true, onEnd = onEnd)
            }
        }
    }
}
