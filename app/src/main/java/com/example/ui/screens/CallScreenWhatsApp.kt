package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PersonAddAlt1
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.call.CallEngine
import com.example.ui.components.MessengerAvatar
import com.example.ui.i18n.Text

private val CallBg = Color(0xFF0B141A)
private val PanelBg = Color(0xFF1B2329)
private val ButtonBg = Color(0xFF2A343B)
private val EndRed = Color(0xFFEA0038)

/** A press longer than this on "End" is a cheek / palm resting on the screen, not a tap. */
private const val END_MAX_PRESS = 800L

/**
 * Full-screen WhatsApp-style call screen (audio and video):
 * minimise ⌄ · name + "End-to-end encrypted" · add person, big round photo in the middle,
 * and a rounded control panel at the bottom: Speaker · Video · Mute / More · Share · End.
 *
 * Only a short, single-finger tap on End ends the call (cheek / palm / back / outside touches never do).
 */
@Composable
internal fun WhatsAppCallScreen(
    peerName: String,
    isVideo: Boolean,
    statusText: String,
    isConnected: Boolean,
    engine: CallEngine?,
    onEnd: () -> Unit,
    onMinimize: () -> Unit,
    onOpenChat: () -> Unit,
    onMarkMissed: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
            decorFitsSystemWindows = false
        )
    ) {
        if (engine != null) KeepScreenOn()
        val nearEar = if (engine != null && !isVideo) rememberNearEar() else false
        var endGuardUntil by remember { mutableLongStateOf(System.currentTimeMillis() + 800) }
        LaunchedEffect(nearEar) {
            if (!nearEar) endGuardUntil = maxOf(endGuardUntil, System.currentTimeMillis() + 1_500)
        }
        val remote = engine?.remoteVideo?.collectAsState()?.value
        val local = engine?.localVideo?.collectAsState()?.value
        val cameraOff = engine?.cameraOff?.collectAsState()?.value ?: false

        Box(
            modifier = Modifier
                .fillMaxSize()
                .singleTouchOnly()
                .background(CallBg)
                .pointerInput(nearEar) {
                    // Phone at the ear: the whole screen ignores touches (cheek can't press anything).
                    if (nearEar) awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
                .drawWithContent {
                    drawContent()
                    if (nearEar) drawRect(Color.Black)
                }
                .testTag("calls_active_call_dialog")
        ) {
            val showRemoteVideo = isVideo && engine != null && remote != null
            if (showRemoteVideo) {
                WebRtcVideoView(track = remote, egl = engine!!.eglBase, mirror = false, onTop = false, modifier = Modifier.fillMaxSize())
            } else {
                CallDoodleBackground(Modifier.fillMaxSize())
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                CallTopBar(peerName = peerName, statusText = statusText, onMinimize = onMinimize)

                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (!showRemoteVideo) {
                        MessengerAvatar(label = peerName, size = 168.dp, modifier = Modifier.testTag("call_peer_photo"))
                    }
                    if (isVideo && engine != null && local != null && !cameraOff) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 16.dp, top = 8.dp)
                                .size(width = 108.dp, height = 156.dp)
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            WebRtcVideoView(track = local, egl = engine.eglBase, mirror = true, onTop = true, modifier = Modifier.fillMaxSize())
                        }
                    }
                }

                CallControlPanel(
                    engine = engine,
                    isVideo = isVideo,
                    isConnected = isConnected,
                    onEnd = onEnd,
                    endBlocked = { nearEar || System.currentTimeMillis() < endGuardUntil },
                    onOpenChat = onOpenChat,
                    onMarkMissed = onMarkMissed
                )
            }
        }
    }
}

@Composable
private fun CallTopBar(peerName: String, statusText: String, onMinimize: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        RoundIconButton(Icons.Rounded.CloseFullscreen, "Minimise call", onMinimize, testTag = "call_minimize")
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = peerName,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("End-to-end encrypted", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = statusText,
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("call_status_text")
            )
        }
        RoundIconButton(
            Icons.Rounded.PersonAddAlt1, "Add person",
            onClick = { Toast.makeText(context, "গ্রুপ কল শীঘ্রই আসছে", Toast.LENGTH_SHORT).show() }
        )
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, label: String, onClick: () -> Unit, testTag: String = "") {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.10f),
        modifier = Modifier.size(52.dp).then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label, tint = Color.White) }
    }
}

/** Bottom rounded panel: Speaker · Video · Mute / More · Share · End (WhatsApp layout). */
@Composable
private fun CallControlPanel(
    engine: CallEngine?,
    isVideo: Boolean,
    isConnected: Boolean,
    onEnd: () -> Unit,
    endBlocked: () -> Boolean,
    onOpenChat: () -> Unit,
    onMarkMissed: () -> Unit
) {
    val context = LocalContext.current
    val muted = engine?.micMuted?.collectAsState()?.value ?: false
    val speaker = engine?.speakerOn?.collectAsState()?.value ?: false
    val cameraOff = engine?.cameraOff?.collectAsState()?.value ?: false
    var moreOpen by remember { mutableStateOf(false) }

    val endSource = remember { MutableInteractionSource() }
    var endPressedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(endSource) {
        endSource.interactions.collect { if (it is PressInteraction.Press) endPressedAt = System.currentTimeMillis() }
    }

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = PanelBg,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PanelButton(
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    label = "Speaker",
                    active = speaker,
                    enabled = engine != null,
                    onClick = { engine?.toggleSpeaker() },
                    testTag = "call_toggle_speaker"
                )
                if (isVideo) {
                    PanelButton(
                        icon = if (cameraOff) Icons.Rounded.VideocamOff else Icons.Rounded.Videocam,
                        label = "Video",
                        active = cameraOff,
                        enabled = engine != null,
                        onClick = { engine?.toggleCamera() },
                        testTag = "call_toggle_camera"
                    )
                } else {
                    // Switching an audio call to video isn't supported yet — shown dimmed like WhatsApp.
                    PanelButton(
                        icon = Icons.Rounded.Videocam,
                        label = "Video",
                        active = false,
                        enabled = false,
                        onClick = { Toast.makeText(context, "ভিডিও কলের জন্য নতুন করে ভিডিও কল দিন", Toast.LENGTH_SHORT).show() }
                    )
                }
                PanelButton(
                    icon = if (muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    label = "Mute",
                    active = muted,
                    enabled = engine != null,
                    onClick = { engine?.toggleMic() },
                    testTag = "call_toggle_mic"
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box {
                    PanelButton(
                        icon = Icons.Rounded.MoreHoriz,
                        label = "More",
                        active = moreOpen,
                        enabled = true,
                        onClick = { moreOpen = true },
                        testTag = "call_more"
                    )
                    DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Message") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null) },
                            onClick = { moreOpen = false; onOpenChat() }
                        )
                        if (isVideo && engine != null) {
                            DropdownMenuItem(
                                text = { Text("Flip camera") },
                                leadingIcon = { Icon(Icons.Rounded.Cameraswitch, contentDescription = null) },
                                onClick = { moreOpen = false; engine.switchCamera() }
                            )
                        }
                        if (engine == null && !isConnected) {
                            DropdownMenuItem(
                                text = { Text("Mark missed") },
                                onClick = { moreOpen = false; onMarkMissed() }
                            )
                        }
                    }
                }
                if (isVideo && engine != null) {
                    PanelButton(
                        icon = Icons.Rounded.Cameraswitch,
                        label = "Flip",
                        active = false,
                        enabled = true,
                        onClick = { engine.switchCamera() },
                        testTag = "call_switch_camera"
                    )
                } else {
                    PanelButton(
                        icon = Icons.Rounded.IosShare,
                        label = "Share",
                        active = false,
                        enabled = false,
                        onClick = { Toast.makeText(context, "স্ক্রিন শেয়ার শীঘ্রই আসছে", Toast.LENGTH_SHORT).show() }
                    )
                }
                PanelButton(
                    icon = Icons.Rounded.CallEnd,
                    label = "End",
                    active = false,
                    enabled = true,
                    background = EndRed,
                    onClick = {
                        val held = System.currentTimeMillis() - endPressedAt
                        if (!endBlocked() && held <= END_MAX_PRESS) onEnd()
                    },
                    interactionSource = endSource,
                    testTag = "calls_dialog_end"
                )
            }
        }
    }
}

@Composable
private fun PanelButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    background: Color? = null,
    interactionSource: MutableInteractionSource? = null,
    testTag: String = "",
    size: Dp = 62.dp
) {
    val bg = background ?: if (active) Color.White else ButtonBg
    val tint = when {
        background != null -> Color.White
        active -> Color(0xFF111B21)
        enabled -> Color.White
        else -> Color.White.copy(alpha = 0.35f)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(84.dp)) {
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            shape = CircleShape,
            color = bg,
            modifier = Modifier.size(size).then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = if (enabled || background != null) Color.White else Color.White.copy(alpha = 0.5f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Faint repeating doodles on the dark call background (like WhatsApp's chat wallpaper). */
@Composable
internal fun CallDoodleBackground(modifier: Modifier = Modifier) {
    val painters = listOf(
        rememberVectorPainter(Icons.Rounded.Favorite),
        rememberVectorPainter(Icons.Rounded.Star),
        rememberVectorPainter(Icons.Rounded.Phone),
        rememberVectorPainter(Icons.Rounded.MusicNote),
        rememberVectorPainter(Icons.Rounded.PhotoCamera),
        rememberVectorPainter(Icons.Rounded.WbSunny),
        rememberVectorPainter(Icons.AutoMirrored.Rounded.Chat)
    )
    val tint = ColorFilter.tint(Color.White.copy(alpha = 0.055f))
    Canvas(modifier = modifier.background(CallBg)) {
        val cell = 64.dp.toPx()
        val icon = 26.dp.toPx()
        val cols = (size.width / cell).toInt() + 2
        val rows = (size.height / cell).toInt() + 2
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val i = (r * 3 + c * 5) % painters.size
                val x = c * cell + (if (r % 2 == 0) 0f else cell / 2f) - cell / 4f
                val y = r * cell
                translate(x, y) {
                    rotate(degrees = ((r * 37 + c * 23) % 50 - 25).toFloat(), pivot = androidx.compose.ui.geometry.Offset(icon / 2, icon / 2)) {
                        with(painters[i]) { draw(androidx.compose.ui.geometry.Size(icon, icon), colorFilter = tint) }
                    }
                }
            }
        }
    }
}

/** Green bar while a call is minimised: tap to go back to the call screen. */
@Composable
internal fun MinimizedCallBar(peerName: String, statusText: String, onReturn: () -> Unit) {
    // Non-focusable popup: the rest of the app stays usable while the call runs in the background.
    androidx.compose.ui.window.Popup(alignment = Alignment.TopCenter) {
    Surface(
        onClick = onReturn,
        color = Color(0xFF1DAA61),
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("call_minimized_bar")
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Phone, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$peerName · $statusText",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text("ফিরে যান", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}
}
