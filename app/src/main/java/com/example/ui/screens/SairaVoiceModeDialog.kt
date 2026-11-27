package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.saira.SairaAutomation
import com.example.saira.SairaNotificationListenerService
import com.example.saira.SairaPhoneActions
import com.example.saira.SairaVoiceMode
import com.example.saira.SairaVoiceprint
import com.example.ui.i18n.Text
import com.example.ui.theme.ChatboxPrimary
import com.example.ui.theme.ChatboxUpdateGreen
import com.example.ui.theme.LocalChatboxColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ENROLL_LINES = listOf(
    "Saira, আমি তোমার মালিক। এখন থেকে শুধু আমার কণ্ঠ শুনবে।",
    "Saira, আম্মুকে কল দাও। করিমকে WhatsApp-এ মেসেজ দাও যে আমি আসছি।",
    "আজকের আবহাওয়া কেমন? স্ক্রিন অন করো, তারপর ইউটিউব খোলো।",
    "Saira, call ta dhoro. Ekhon screen off koro. Thank you!"
)

/** Saira voice mode: teach her your voice (15 s), test it, and turn background listening on/off. */
@Composable
fun SairaVoiceModeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalChatboxColors.current
    val scope = rememberCoroutineScope()
    val running by SairaVoiceMode.running.collectAsState()
    val status by SairaVoiceMode.status.collectAsState()

    var voiceprint by remember { mutableStateOf(SairaVoiceMode.voiceprint(context)) }
    var sensitivity by remember { mutableIntStateOf(SairaVoiceMode.sensitivity(context)) }
    var recording by remember { mutableStateOf<String?>(null) } // "enroll" | "test"
    var elapsed by remember { mutableStateOf(0f) }
    var voiced by remember { mutableStateOf(0f) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // text, isError
    var refresh by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val full = remember { SairaAutomation.isAvailable(context) }
    @Suppress("UNUSED_VARIABLE") val tick = refresh
    val automationOn = full && SairaAutomation.isEnabled(context)
    val listenerOn = full && SairaNotificationListenerService.isNotificationAccessGranted(context)
    val micGranted = SairaPhoneActions.granted(context, Manifest.permission.RECORD_AUDIO)

    @SuppressLint("MissingPermission")
    fun record(mode: String) {
        if (recording != null) return
        val wasRunning = running
        if (wasRunning) SairaVoiceMode.stop(context)
        recording = mode
        elapsed = 0f
        voiced = 0f
        message = null
        scope.launch {
            val rate = SairaVoiceprint.SAMPLE_RATE
            val minSec = if (mode == "enroll") 15f else 4f
            val maxSec = if (mode == "enroll") 35f else 6f
            val needVoiced = if (mode == "enroll") 8f else 1.2f
            val pcm = withContext(Dispatchers.IO) {
                val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val rec = runCatching {
                    AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 6400)
                    )
                }.getOrNull()
                if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
                    rec?.release()
                    return@withContext null
                }
                val out = java.io.ByteArrayOutputStream()
                val frame = ShortArray(320)
                var samples = 0
                var voicedFrames = 0
                try {
                    rec.startRecording()
                    while (isActive) {
                        val n = rec.read(frame, 0, frame.size)
                        if (n <= 0) continue
                        for (i in 0 until n) { val v = frame[i].toInt(); out.write(v and 0xff); out.write((v shr 8) and 0xff) }
                        samples += n
                        if (SairaVoiceprint.rms(frame, n) > 450) voicedFrames++
                        val sec = samples / rate.toFloat()
                        val vs = voicedFrames * 0.02f
                        if (samples % 3200 < n) withContext(Dispatchers.Main) { elapsed = sec; voiced = vs }
                        if ((sec >= minSec && vs >= needVoiced) || sec >= maxSec) break
                    }
                } finally {
                    runCatching { rec.stop() }
                    rec.release()
                }
                val bytes = out.toByteArray()
                ShortArray(bytes.size / 2) { i -> ((bytes[2 * i].toInt() and 0xff) or (bytes[2 * i + 1].toInt() shl 8)).toShort() }
            }
            if (pcm == null) {
                message = "মাইক্রোফোন চালু করা যায়নি।" to true
            } else if (mode == "enroll") {
                val vp = withContext(Dispatchers.Default) { SairaVoiceprint.enroll(pcm, System.currentTimeMillis()) }
                if (vp == null) {
                    message = "যথেষ্ট কথা শুনতে পাইনি — একটু জোরে, টানা কথা বলে আবার চেষ্টা করুন।" to true
                } else {
                    SairaVoiceMode.saveVoiceprint(context, vp)
                    voiceprint = vp
                    message = "✅ আপনার কণ্ঠ শিখে নিয়েছি (${"%.0f".format(vp.voicedSeconds)} সেকেন্ড কথা)। এখন \"পরীক্ষা\" করে দেখুন।" to false
                }
            } else {
                val vp = voiceprint
                if (vp != null) {
                    val m = withContext(Dispatchers.Default) { SairaVoiceprint.match(vp, pcm, sensitivity) }
                    message = if (m.ok) "✅ মিলেছে — এটা আপনার কণ্ঠ।" to false
                    else (if (m.reason == "too short") "আরেকটু বেশি কথা বলুন।" else "❌ মেলেনি — Saira এই কণ্ঠ মানবে না।") to true
                }
            }
            recording = null
            if (wasRunning) SairaVoiceMode.start(context)
        }
    }

    val enrollPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) record("enroll") else message = "মাইক্রোফোনের অনুমতি ছাড়া কণ্ঠ শেখানো যাবে না।" to true
    }
    val startPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        if (SairaVoiceMode.start(context)) {
            message = "🎙️ ভয়েস মোড চালু। স্ক্রিন বন্ধ থাকলেও \"Saira, …\" বললে শুনবে।" to false
            if (full && !SairaNotificationListenerService.isBatteryOptimizationIgnored(context)) {
                SairaNotificationListenerService.requestIgnoreBatteryOptimization(context)
            }
        } else {
            message = "মাইক্রোফোনের অনুমতি লাগবে।" to true
        }
        refresh++
    }

    Dialog(onDismissRequest = { if (recording == null) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .testTag("saira_voice_mode_dialog")
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.example.ui.components.SairaMetaOrb(size = 32.dp, animate = running)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Saira ভয়েস মোড",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.ink,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { if (recording == null) onDismiss() },
                        modifier = Modifier.testTag("saira_voice_mode_close")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.inkMuted)
                    }
                }
                Text(
                    "Saira শুধু আপনার কণ্ঠ চিনবে। \"Saira, আম্মুকে কল দাও\", \"Saira, করিমকে WhatsApp-এ মেসেজ দাও যে আমি আসছি\", " +
                        "\"Saira, কল ধরো\", \"Saira, স্ক্রিন বন্ধ করো\" — ফোন পকেটে বা স্ক্রিন বন্ধ থাকলেও। কল আর মেসেজের আগে Saira " +
                        "জিজ্ঞেস করবে, আপনি \"হ্যাঁ\" বললে তবেই করবে।",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted
                )

                // Step 1 — voice
                Surface(shape = RoundedCornerShape(16.dp), color = colors.fieldBg, border = BorderStroke(1.dp, colors.border)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("১. আপনার কণ্ঠ শেখান", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.ink)
                        if (recording == "enroll") {
                            Text("নিচের লাইনগুলো স্বাভাবিকভাবে পড়ুন (কমপক্ষে ১৫ সেকেন্ড):", style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
                            ENROLL_LINES.forEach {
                                Text("• $it", style = MaterialTheme.typography.bodyMedium, color = colors.ink)
                            }
                            LinearProgressIndicator(
                                progress = { (elapsed / 15f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "${elapsed.toInt()} / 15 সেকেন্ড · কথা শোনা গেছে ${voiced.toInt()} সেকেন্ড",
                                style = MaterialTheme.typography.labelMedium,
                                color = ChatboxPrimary
                            )
                        } else {
                            Text(
                                if (voiceprint != null) "✅ কণ্ঠ শেখানো আছে। চাইলে আবার শেখাতে পারেন।"
                                else "Saira-র সাথে কমপক্ষে ১৫ সেকেন্ড কথা বলুন — ও আপনার কণ্ঠ মনে রাখবে (শুধু এই ফোনে, কোথাও পাঠানো হয় না)।",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkMuted
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    enabled = recording == null,
                                    onClick = {
                                        if (micGranted) record("enroll") else enrollPerm.launch(Manifest.permission.RECORD_AUDIO)
                                    },
                                    modifier = Modifier.testTag("saira_voice_enroll_button")
                                ) {
                                    Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (voiceprint == null) "কথা বলা শুরু" else "আবার শেখান")
                                }
                                if (voiceprint != null) {
                                    OutlinedButton(
                                        enabled = recording == null && micGranted,
                                        onClick = { record("test") },
                                        modifier = Modifier.testTag("saira_voice_test_button")
                                    ) { Text(if (recording == "test") "শুনছি…" else "পরীক্ষা") }
                                }
                            }
                        }
                    }
                }

                message?.let { (text, isError) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = if (isError) Color(0xFFD14343) else ChatboxUpdateGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text, style = MaterialTheme.typography.bodySmall, color = colors.ink)
                    }
                }

                // Step 2 — sensitivity
                if (voiceprint != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("কণ্ঠ মেলানোর কড়াকড়ি", style = MaterialTheme.typography.labelLarge, color = colors.ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(SairaVoiceprint.STRICT to "কড়া", SairaVoiceprint.NORMAL to "স্বাভাবিক", SairaVoiceprint.RELAXED to "ঢিলা")
                                .forEach { (v, label) ->
                                    FilterChip(
                                        selected = sensitivity == v,
                                        onClick = { sensitivity = v; SairaVoiceMode.setSensitivity(context, v) },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ChatboxPrimary.copy(alpha = 0.15f))
                                    )
                                }
                        }
                        Text(
                            "আপনার কমান্ড প্রায়ই না শুনলে \"ঢিলা\", অন্য কেউ চালাতে পারলে \"কড়া\" দিন।",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkSubtle
                        )
                    }
                }

                // Step 3 — on / off
                Surface(shape = RoundedCornerShape(16.dp), color = colors.fieldBg, border = BorderStroke(1.dp, colors.border)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("২. ব্যাকগ্রাউন্ডে শোনা", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.ink)
                            Text(
                                if (running) status.ifBlank { "শুনছি — \"Saira, …\" বলুন" }
                                else if (voiceprint == null) "আগে কণ্ঠ শেখান" else "বন্ধ",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (running) ChatboxUpdateGreen else colors.inkMuted,
                                maxLines = 2
                            )
                        }
                        Switch(
                            checked = running,
                            enabled = voiceprint != null && recording == null,
                            onCheckedChange = { on ->
                                if (on) startPerms.launch(SairaPhoneActions.voiceModePermissions(context).ifEmpty { listOf(Manifest.permission.RECORD_AUDIO) }.toTypedArray())
                                else { SairaVoiceMode.stop(context); message = null }
                            },
                            modifier = Modifier.testTag("saira_voice_mode_switch")
                        )
                    }
                }

                // Step 4 — powers that need system settings
                Text("৩. পুরো ক্ষমতা (একবার চালু করলেই হবে)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.ink)
                if (full) {
                    PowerRow(
                        title = "Saira Automation",
                        subtitle = "অন্য অ্যাপে Send / Call চাপা, স্ক্রিন বন্ধ করা, স্ক্রিন বন্ধ থাকা অবস্থায় অ্যাপ খোলা",
                        on = automationOn,
                        tag = "saira_voice_automation_button",
                        onClick = { SairaAutomation.openSettings(context) }
                    )
                    if (!automationOn) {
                        Text(
                            "Android 13+ এ চালু করা না গেলে: App info → ⋮ (ওপরে ডানে) → \"Allow restricted settings\" চাপুন, তারপর আবার চেষ্টা করুন।",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkSubtle
                        )
                        TextButton(onClick = { SairaNotificationListenerService.openAppDetailsSettings(context) }) { Text("App info খুলুন") }
                    }
                    PowerRow(
                        title = "Notification access",
                        subtitle = "WhatsApp / Messenger / ফোন কল ধরা ও কেটে দেওয়া",
                        on = listenerOn,
                        tag = "saira_voice_notification_button",
                        onClick = { SairaNotificationListenerService.openNotificationAccessForApp(context) }
                    )
                } else {
                    Text(
                        "এই (Standard) ভার্সনে Saira ফোন কল দিতে, কল ধরতে আর অ্যাপ খুলতে পারবে। স্ক্রিন বন্ধ করা, WhatsApp/Messenger-এ নিজে Send/Call চাপা " +
                            "আর সরাসরি SMS পাঠানোর জন্য Full edition লাগবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkMuted
                    )
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(SairaNotificationListenerService.FULL_EDITION_URL))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }) { Text("Full edition ডাউনলোড") }
                }

                if (voiceprint != null) {
                    TextButton(
                        enabled = recording == null,
                        onClick = {
                            SairaVoiceMode.stop(context)
                            SairaVoiceMode.saveVoiceprint(context, null)
                            voiceprint = null
                            message = "কণ্ঠের তথ্য মুছে ফেলেছি, ভয়েস মোড বন্ধ।" to false
                        }
                    ) { Text("কণ্ঠের তথ্য মুছুন", color = Color(0xFFD14343)) }
                }
                Text(
                    "দ্রষ্টব্য: কণ্ঠ মেলানো ফোনেই হয়; শুধু আপনার কণ্ঠ মিললে তবেই কথাটা লেখায় রূপান্তরের জন্য AI-তে যায়। খুব কাছাকাছি কণ্ঠ মাঝে মাঝে মিলে যেতে পারে। " +
                        "ফোন লক (PIN/প্যাটার্ন) থাকলে ফোন কল আর SMS হবে, কিন্তু WhatsApp-এর কাজের জন্য ফোন আনলক থাকতে হবে। ব্যাটারি একটু বেশি খরচ হবে।",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkSubtle
                )
            }
        }
    }
}

@Composable
private fun PowerRow(title: String, subtitle: String, on: Boolean, tag: String, onClick: () -> Unit) {
    val colors = LocalChatboxColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            if (on) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = if (on) ChatboxUpdateGreen else Color(0xFFE0A100),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.inkSubtle)
        }
        if (!on) {
            TextButton(onClick = onClick, modifier = Modifier.testTag(tag)) { Text("চালু করুন") }
        }
    }
}

/** True when the app has the permission (used by the Saira screen header). */
fun hasMicPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
