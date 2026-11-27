package com.example.ui.screens

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.example.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.agent.AgentAttachment
import com.example.agent.AgentHooks
import com.example.agent.AppSelfUpdater
import com.example.agent.ProBrain
import com.example.agent.ProKey
import com.example.agent.SairaAgent
import com.example.ui.theme.ChatboxPrimary
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_ATTACH_BYTES = 15L * 1024 * 1024

/** Admin-only: Saira's agent tab — full-screen command chat + side menu (Connected apps, quick actions). */
@Composable
fun SairaAgentPanel(
    isOnline: Boolean,
    firebaseUid: String,
    onApplyLiveConfig: suspend (String) -> Result<String>
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { SairaAgent.init(context) }
    // Opens full screen by default so the chat uses the whole display; back / the shrink icon returns to the tab.
    var fullScreen by rememberSaveable { mutableStateOf(true) }

    if (fullScreen) {
        Box(
            modifier = Modifier.fillMaxSize().testTag("saira_agent_panel"),
            contentAlignment = Alignment.Center
        ) {
            Button(onClick = { fullScreen = true }) { Text("Saira Agent পুরো স্ক্রিনে খুলুন") }
        }
        Dialog(
            onDismissRequest = { fullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(color = Color(0xFFFBFAFE), modifier = Modifier.fillMaxSize().testTag("saira_agent_fullscreen")) {
                SairaAgentWorkspace(
                    isOnline = isOnline,
                    firebaseUid = firebaseUid,
                    onApplyLiveConfig = onApplyLiveConfig,
                    fullScreen = true,
                    onToggleFullScreen = { fullScreen = false }
                )
            }
        }
    } else {
        Box(Modifier.fillMaxSize().testTag("saira_agent_panel")) {
            SairaAgentWorkspace(
                isOnline = isOnline,
                firebaseUid = firebaseUid,
                onApplyLiveConfig = onApplyLiveConfig,
                fullScreen = false,
                onToggleFullScreen = { fullScreen = true }
            )
        }
    }
}

@Composable
private fun SairaAgentWorkspace(
    isOnline: Boolean,
    firebaseUid: String,
    onApplyLiveConfig: suspend (String) -> Result<String>,
    fullScreen: Boolean,
    onToggleFullScreen: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val messages by SairaAgent.messages.collectAsState()
    val busy by SairaAgent.busy.collectAsState()
    val github by SairaAgent.github.collectAsState()
    val job by SairaAgent.job.collectAsState()
    val autoInstall by SairaAgent.autoInstall.collectAsState()
    val proKeys by SairaAgent.proKeys.collectAsState()
    val updateState by AppSelfUpdater.state.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    LaunchedEffect(github) { if (github == null && messages.size <= 1) drawerState.open() }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    var draft by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<AgentAttachment>>(emptyList()) }
    var attachError by remember { mutableStateOf<String?>(null) }
    var galleryGranted by remember { mutableStateOf(hasGalleryPermission(context)) }

    val hooks = remember {
        AgentHooks(
            applyLiveConfig = onApplyLiveConfig,
            recentGalleryPhotos = { n -> recentGalleryPhotos(context, n) }
        )
    }

    fun addUris(uris: List<Uri>) {
        scope.launch {
            val loaded = uris.take(6).mapNotNull { readAttachment(context, it) }
            attachError = if (loaded.size < uris.take(6).size) {
                "কিছু ফাইল পড়া যায়নি (খুব বড় বা অসমর্থিত — ছবি, PDF, টেক্সট, অডিও/ছোট ভিডিও চলে)।"
            } else null
            attachments = (attachments + loaded).take(6)
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) addUris(uris)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        if (uris.isNotEmpty()) addUris(uris)
    }
    val galleryPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        galleryGranted = hasGalleryPermission(context)
    }

    fun quick(command: String) {
        if (busy || !isOnline) return
        scope.launch { drawerState.close() }
        SairaAgent.send(command, emptyList(), hooks)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFFFBFAFE),
                modifier = Modifier.fillMaxWidth(0.88f).testTag("saira_agent_drawer")
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Saira Agent", fontWeight = FontWeight.Bold, color = ChatboxPrimary, style = MaterialTheme.typography.titleLarge)
                            Text("শুধু অ্যাডমিনের জন্য", style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B6480))
                        }
                        IconButton(onClick = { scope.launch { drawerState.close() } }) {
                            Icon(Icons.Rounded.Close, contentDescription = "মেনু বন্ধ", tint = ChatboxPrimary)
                        }
                    }

                    DrawerSectionTitle("⚡ দ্রুত কাজ")
                    DrawerAction("📊", "GitHub / বিল্ডের অবস্থা", enabled = !busy && isOnline) { quick("GitHub এর অবস্থা দেখাও") }
                    DrawerAction("🔥", "Firebase রিপোর্ট", enabled = !busy && isOnline) { quick("Firebase এর রিপোর্ট দেখাও") }
                    DrawerAction("⬆️", "অ্যাপ আপডেট চেক করো", enabled = !busy && isOnline) { quick("অ্যাপ আপডেট করো") }
                    DrawerAction("🖼️", "আমার শেষ ৫টা ছবি দেখো", enabled = !busy && isOnline && galleryGranted) { quick("আমার শেষ ৫টা ছবি দেখো") }
                    DrawerAction("📎", "Drive / ফাইল যোগ করুন", enabled = true) {
                        scope.launch { drawerState.close() }
                        filePicker.launch(arrayOf("*/*"))
                    }
                    DrawerAction(
                        if (fullScreen) "↙️" else "↗️",
                        if (fullScreen) "পুরো স্ক্রিন বন্ধ করুন" else "পুরো স্ক্রিনে খুলুন",
                        enabled = true
                    ) {
                        scope.launch { drawerState.close() }
                        onToggleFullScreen()
                    }

                    DrawerSectionTitle("🔗 Connected")
                    ConnectedSection(
                        githubLabel = github?.let { "${it.login} → ${it.repo}" },
                        firebaseUid = firebaseUid,
                        galleryGranted = galleryGranted,
                        autoInstall = autoInstall,
                        updateState = updateState,
                        proKeys = proKeys,
                        onAddProKey = { SairaAgent.addProKeys(it) },
                        onRemoveProKey = { SairaAgent.removeProKey(it) },
                        onTestProKeys = { SairaAgent.testProKeys() },
                        onConnectGitHub = { token, repo -> SairaAgent.connectGitHub(token, repo) },
                        onDisconnectGitHub = { SairaAgent.disconnectGitHub() },
                        onOpenDrive = {
                            scope.launch { drawerState.close() }
                            filePicker.launch(arrayOf("*/*"))
                        },
                        onGrantGallery = {
                            galleryPermLauncher.launch(
                                if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
                                else Manifest.permission.READ_EXTERNAL_STORAGE
                            )
                        },
                        onAutoInstall = SairaAgent::setAutoInstall,
                        onUpdateNow = { scope.launch { AppSelfUpdater.updateToLatest(context) } },
                        onClearChat = {
                            SairaAgent.clearChat()
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFFFBFAFE))) {
            // ---------------- Top bar ----------------
            Surface(color = Color(0xFFF4F1FB), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier.testTag("saira_agent_menu")
                    ) {
                        Icon(Icons.Rounded.Menu, contentDescription = "মেনু", tint = ChatboxPrimary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Saira Agent", fontWeight = FontWeight.SemiBold, color = ChatboxPrimary)
                        Text(
                            text = listOf(
                                if (github != null) "GitHub ✓" else "GitHub ✗",
                                if (firebaseUid.isNotBlank()) "Firebase ✓" else "Firebase ✗",
                                "Drive ✓",
                                if (galleryGranted) "Gallery ✓" else "Gallery ✗"
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF6B6480),
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = onToggleFullScreen, modifier = Modifier.testTag("saira_agent_fullscreen_toggle")) {
                        Icon(
                            if (fullScreen) Icons.Rounded.CloseFullscreen else Icons.Rounded.OpenInFull,
                            contentDescription = if (fullScreen) "ছোট করুন" else "পুরো স্ক্রিন",
                            tint = ChatboxPrimary
                        )
                    }
                }
            }

            job?.let { j ->
                Surface(color = Color(0xFFEFF8F1), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            "🛠️ ${SairaAgent.phaseLabel(j.phase)} — “${j.instruction.take(60)}”",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF166534)
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                }
            }

            // ---------------- Messages ----------------
            val listState = rememberLazyListState()
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { m ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (m.fromSaira) Arrangement.Start else Arrangement.End
                    ) {
                        Surface(
                            color = if (m.fromSaira) Color.White else ChatboxPrimary,
                            shape = RoundedCornerShape(16.dp),
                            border = if (m.fromSaira) BorderStroke(1.dp, Color(0xFFE6E1F2)) else null,
                            modifier = Modifier.fillMaxWidth(0.88f).wrapContentWidth(if (m.fromSaira) Alignment.Start else Alignment.End)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (m.attachmentNote.isNotBlank()) {
                                    Text(
                                        m.attachmentNote,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (m.fromSaira) Color(0xFF6B6480) else Color.White.copy(alpha = 0.85f)
                                    )
                                }
                                SelectionContainer {
                                    Text(
                                        m.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (m.fromSaira) Color(0xFF2A2340) else Color.White
                                    )
                                }
                                if (m.confirm != null && !m.resolved) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { SairaAgent.confirm(m.id, true) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                            modifier = Modifier.testTag("saira_agent_confirm_yes")
                                        ) { Text("হ্যাঁ, করো") }
                                        OutlinedButton(onClick = { SairaAgent.confirm(m.id, false) }) { Text("বাতিল") }
                                    }
                                }
                            }
                        }
                    }
                }
                if (busy) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text("Saira কাজ করছে…", style = MaterialTheme.typography.labelMedium, color = Color(0xFF6B6480))
                        }
                    }
                }
            }

            // ---------------- Composer ----------------
            if (attachments.isNotEmpty() || attachError != null) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    attachments.forEachIndexed { i, a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "📎 ${a.name} (${a.bytes.size / 1024} KB)",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { attachments = attachments.filterIndexed { j, _ -> j != i } }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    attachError?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Color(0xFFB91C1C)) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.White).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.testTag("saira_agent_attach")) {
                    Icon(Icons.Rounded.AttachFile, contentDescription = "Drive / file", tint = ChatboxPrimary)
                }
                IconButton(onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                }) {
                    Icon(Icons.Rounded.Image, contentDescription = "Gallery", tint = ChatboxPrimary)
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(3000) },
                    placeholder = { Text("Saira-কে কমান্ড দিন… যেমন: লগইন পেজে একটা Help বাটন যোগ করো") },
                    modifier = Modifier.weight(1f).heightIn(max = 160.dp).testTag("saira_agent_input"),
                    shape = RoundedCornerShape(20.dp)
                )
                IconButton(
                    enabled = !busy && isOnline && (draft.isNotBlank() || attachments.isNotEmpty()),
                    onClick = {
                        SairaAgent.send(draft, attachments, hooks)
                        draft = ""
                        attachments = emptyList()
                        attachError = null
                    },
                    modifier = Modifier.testTag("saira_agent_send")
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).background(ChatboxPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF6B6480),
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun DrawerAction(emoji: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Text(emoji) },
        label = { Text(label, color = if (enabled) Color(0xFF2A2340) else Color(0xFFA8A2B8)) },
        selected = false,
        onClick = { if (enabled) onClick() },
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}

@Composable
private fun ConnectedSection(
    proKeys: List<ProKey>,
    onAddProKey: suspend (String) -> String,
    onRemoveProKey: suspend (String) -> String,
    onTestProKeys: suspend () -> String,
    githubLabel: String?,
    firebaseUid: String,
    galleryGranted: Boolean,
    autoInstall: Boolean,
    updateState: String,
    onConnectGitHub: suspend (String, String) -> Result<Any>,
    onDisconnectGitHub: () -> Unit,
    onOpenDrive: () -> Unit,
    onGrantGallery: () -> Unit,
    onAutoInstall: (Boolean) -> Unit,
    onUpdateNow: () -> Unit,
    onClearChat: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("Ishtiakru/Chatbox") }
    var connecting by remember { mutableStateOf(false) }
    var ghError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // AI Brain — the admin's own model keys
        var newKey by remember { mutableStateOf("") }
        var brainWorking by remember { mutableStateOf(false) }
        var brainStatus by remember { mutableStateOf<String?>(null) }
        ConnectorCard(
            title = "🧠 AI Brain (আপনার API key)",
            connected = proKeys.isNotEmpty(),
            subtitle = if (proKeys.isEmpty()) {
                "Claude / GPT / Gemini Pro-র key দিলে Saira সবচেয়ে শক্তিশালী মডেল দিয়ে উত্তর দেবে ও অ্যাপের কোড লিখবে। " +
                    "Key শুধু এই ফোনে (এনক্রিপ্ট করে) আর GitHub secret-এ থাকে — অ্যাপের APK-তে কখনো না।"
            } else "সবচেয়ে শক্তিশালীটা আগে ব্যবহার হয়; কাজ না করলে পরেরটা, শেষে ফ্রি Gemini।"
        ) {
            proKeys.forEach { k ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${ProBrain.LABELS[k.provider] ?: k.provider} · ${ProBrain.mask(k.key)}" + (k.model?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(enabled = !brainWorking, onClick = {
                        brainWorking = true
                        scope.launch {
                            brainStatus = onRemoveProKey(k.key)
                            brainWorking = false
                        }
                    }) { Text("মুছুন") }
                }
            }
            OutlinedTextField(
                value = newKey,
                onValueChange = { newKey = it },
                label = { Text("API key (চাইলে space দিয়ে model নাম)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("saira_agent_pro_key")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = newKey.trim().length > 15 && !brainWorking,
                    onClick = {
                        brainWorking = true
                        brainStatus = "যাচাই করছি…"
                        scope.launch {
                            brainStatus = onAddProKey(newKey)
                            newKey = ""
                            brainWorking = false
                        }
                    }
                ) { Text(if (brainWorking) "অপেক্ষা…" else "যোগ করুন") }
                if (proKeys.isNotEmpty()) {
                    OutlinedButton(enabled = !brainWorking, onClick = {
                        brainWorking = true
                        brainStatus = "পরীক্ষা করছি…"
                        scope.launch {
                            brainStatus = onTestProKeys()
                            brainWorking = false
                        }
                    }) { Text("পরীক্ষা") }
                }
            }
            brainStatus?.let {
                androidx.compose.material3.Text(it, style = MaterialTheme.typography.labelSmall, color = Color(0xFF4B4560))
            }
        }
        // GitHub
        ConnectorCard(title = "GitHub", connected = githubLabel != null, subtitle = githubLabel ?: "অ্যাপের কোড বদলানো, বিল্ড ও রিলিজের জন্য") {
            if (githubLabel != null) {
                TextButton(onClick = onDisconnectGitHub) { Text("Disconnect") }
            } else {
                Text(
                    "github.com → Settings → Developer settings → Fine-grained token → Repository: Chatbox → " +
                        "Permissions: Contents, Actions, Secrets = Read and write → Generate, তারপর এখানে পেস্ট করুন।",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6B6480)
                )
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/personal-access-tokens/new"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { Text("Token তৈরির পেজ খুলুন") }
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.trim() },
                    label = { Text("GitHub token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("saira_agent_github_token")
                )
                OutlinedTextField(
                    value = repo,
                    onValueChange = { repo = it.trim() },
                    label = { Text("Repository") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ghError?.let { Text(it, color = Color(0xFFB91C1C), style = MaterialTheme.typography.labelSmall) }
                Button(
                    enabled = token.length > 20 && !connecting,
                    onClick = {
                        connecting = true
                        ghError = null
                        scope.launch {
                            val r = onConnectGitHub(token, repo)
                            connecting = false
                            r.onSuccess { token = "" }.onFailure { ghError = it.message }
                        }
                    }
                ) { Text(if (connecting) "যাচাই করছি…" else "Connect") }
            }
        }
        // Firebase
        ConnectorCard(
            title = "Firebase",
            connected = firebaseUid.isNotBlank(),
            subtitle = if (firebaseUid.isNotBlank()) {
                "অ্যাডমিন অ্যাকাউন্টে যুক্ত · লাইভ কনফিগ সরাসরি; রিপোর্ট/রুলস GitHub-এর admin key দিয়ে"
            } else "লগইন করলে যুক্ত হবে"
        ) {}
        // Drive
        ConnectorCard(
            title = "Google Drive / ফাইল",
            connected = true,
            subtitle = "Android-এর ফাইল পিকার দিয়ে Drive-এর যেকোনো ফাইল (PDF, ছবি, টেক্সট, Google Docs) খুলে Saira-কে দিন"
        ) {
            TextButton(onClick = onOpenDrive) { Text("Drive থেকে ফাইল খুলুন") }
        }
        // Gallery
        ConnectorCard(
            title = "Gallery",
            connected = galleryGranted,
            subtitle = if (galleryGranted) "Saira আপনার সাম্প্রতিক ছবি দেখতে পারবে (\"আমার শেষ ৫টা ছবি দেখো\")"
            else "অনুমতি দিলে Saira আপনার গ্যালারির ছবি দেখতে পারবে"
        ) {
            if (!galleryGranted) TextButton(onClick = onGrantGallery) { Text("অনুমতি দিন") }
        }
        // Auto update
        ConnectorCard(
            title = "অ্যাপ আপডেট (লিংক ছাড়া)",
            connected = AppSelfUpdater.canInstall(context),
            subtitle = "এখন: v${BuildConfig.VERSION_NAME} · " + when {
                updateState.startsWith("downloading:") -> "ডাউনলোড ${updateState.substringAfter(':')}%"
                updateState == "checking" -> "খুঁজছি…"
                updateState == "installing" -> "ইনস্টল হচ্ছে…"
                updateState == "uptodate" -> "সর্বশেষ ভার্সন"
                updateState == "needs_permission" -> "অনুমতি দরকার"
                updateState.startsWith("error:") -> "ত্রুটি: ${updateState.substringAfter(':').take(60)}"
                else -> "প্রস্তুত"
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("নিজে থেকে ইনস্টল", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Switch(checked = autoInstall, onCheckedChange = onAutoInstall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onUpdateNow) { Text("এখনই আপডেট") }
                if (!AppSelfUpdater.canInstall(context)) {
                    TextButton(onClick = { AppSelfUpdater.openInstallPermissionSettings(context) }) { Text("ইনস্টল অনুমতি") }
                }
            }
        }
        TextButton(onClick = onClearChat) { Text("এজেন্ট চ্যাট মুছুন") }
    }
}

@Composable
private fun ConnectorCard(
    title: String,
    connected: Boolean,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (connected) Color(0xFF86EFAC) else Color(0xFFE6E1F2)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), color = Color(0xFF2A2340))
                Text(
                    if (connected) "Connected" else "Not connected",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (connected) Color(0xFF15803D) else Color(0xFF9A3412)
                )
            }
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B6480))
            content()
        }
    }
}


// ----------------------------------------------------------------- helpers

internal fun hasGalleryPermission(context: Context): Boolean {
    val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE
    return ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
}

private fun displayName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment ?: "file"

/** Reads a picked file for Gemini. Google Docs (virtual files) are exported as PDF. */
internal suspend fun readAttachment(context: Context, uri: Uri): AgentAttachment? = withContext(Dispatchers.IO) {
    runCatching {
        val cr = context.contentResolver
        val name = displayName(context, uri)
        var mime = cr.getType(uri) ?: "application/octet-stream"
        val bytes: ByteArray = try {
            cr.openInputStream(uri)?.use { readCapped(it) } ?: return@runCatching null
        } catch (e: Exception) {
            // Virtual (Google Docs / Sheets) file: ask the provider for a PDF / text export.
            val types = cr.getStreamTypes(uri, "*/*").orEmpty()
            val pick = types.firstOrNull { it == "application/pdf" }
                ?: types.firstOrNull { it.startsWith("text/") } ?: types.firstOrNull() ?: throw e
            mime = pick
            cr.openTypedAssetFileDescriptor(uri, pick, null)?.createInputStream()?.use { readCapped(it) }
                ?: throw e
        }
        when {
            mime.startsWith("image/") -> AgentAttachment(name, "image/jpeg", downscaleJpeg(bytes) ?: return@runCatching null)
            mime == "application/pdf" || mime.startsWith("audio/") || mime.startsWith("video/") ->
                AgentAttachment(name, mime, bytes)
            mime.startsWith("text/") || mime.contains("json") || mime.contains("xml") ||
                name.substringAfterLast('.', "").lowercase() in setOf("kt", "java", "md", "csv", "txt", "py", "js", "log") ->
                AgentAttachment(name, "text/plain", bytes)
            else -> null
        }
    }.getOrNull()
}

private fun readCapped(input: java.io.InputStream): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        total += n
        if (total > MAX_ATTACH_BYTES) throw IllegalStateException("too large")
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

private fun downscaleJpeg(bytes: ByteArray, maxDim: Int = 1600): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim * 2) sample *= 2
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    val scale = minOf(1f, maxDim.toFloat() / max(bmp.width, bmp.height))
    val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return out.toByteArray()
}

/** Newest photos from the phone gallery (needs the media permission). */
internal suspend fun recentGalleryPhotos(context: Context, count: Int): List<AgentAttachment> = withContext(Dispatchers.IO) {
    if (!hasGalleryPermission(context)) return@withContext emptyList()
    val out = mutableListOf<AgentAttachment>()
    runCatching {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
            null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            while (c.moveToNext() && out.size < count) {
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { readCapped(it) } }.getOrNull() ?: continue
                val jpg = downscaleJpeg(bytes, 1024) ?: continue
                out += AgentAttachment(c.getString(1) ?: "photo.jpg", "image/jpeg", jpg)
            }
        }
    }
    out
}
