package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.example.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.SparkAdminCommands
import com.example.data.SparkCommandProposal
import com.example.data.ai.AdminPlanInterpreter
import com.example.data.model.AdminAiUpdatePlan
import com.example.data.model.AdminAuthStatus
import com.example.data.model.ChatboxConstants
import com.example.data.model.QeLocalReleaseArchive
import com.example.data.model.SparkAppearance
import com.example.data.model.SparkAppUpdate
import com.example.data.model.SparkDelegation
import com.example.data.model.SparkDynamicConfig
import com.example.data.repository.AuthRepository
import com.example.data.repository.SparkAdminRepository
import com.example.ui.components.ChatboxLogoWithUpdateBadge
import com.example.ui.components.NoticeBanner
import com.example.ui.components.customMenuIconVector
import com.example.ui.theme.ChatboxErrorInk
import com.example.ui.theme.ChatboxPrimary
import com.example.ui.theme.ChatboxSoftLavender
import com.example.ui.theme.ChatboxUpdateGreen
import com.example.ui.theme.ChatboxUpdateGreenSoft
import com.example.ui.theme.LocalChatboxColors
import com.example.util.friendlyFirestoreMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun formatFileBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(java.util.Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
}

@Composable
fun AdminScreen(
    currentUserId: String,
    currentUserEmail: String?,
    isOnline: Boolean,
    appearance: SparkAppearance,
    welcomeBanner: String,
    appUpdate: SparkAppUpdate,
    dynamicConfig: SparkDynamicConfig,
    authRepo: AuthRepository,
    sparkAdminRepo: SparkAdminRepository,
    onAppUpdateClick: () -> Unit
) {
    val colors = LocalChatboxColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var adminTab by remember { mutableStateOf("dashboard") } // "dashboard" | "qeCoder"
    var status by remember { mutableStateOf(AdminAuthStatus.CHECKING) }
    var statusError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    var promptDraft by remember { mutableStateOf("") }
    var pendingPlan by remember { mutableStateOf<AdminAiUpdatePlan?>(null) }
    var welcomeDraft by remember(welcomeBanner) { mutableStateOf(welcomeBanner) }

    // QE Coder ZIP + Update state
    var qeVersion by remember(appUpdate.version) { mutableStateOf(appUpdate.version.ifBlank { "1.0.1" }) }
    var qeNote by remember(appUpdate.note) { mutableStateOf(appUpdate.note) }
    var qeApkUrl by remember(appUpdate.downloadUrl) { mutableStateOf(appUpdate.downloadUrl) }
    var currentZipInfo by remember { mutableStateOf<QeLocalReleaseArchive?>(null) }
    var currentZipFile by remember { mutableStateOf<File?>(null) }

    // Delegate access management
    var showDelegates by remember { mutableStateOf(false) }
    var delegateEmailDraft by remember { mutableStateOf("") }

    val delegations by remember(status) {
        if (status == AdminAuthStatus.OWNER) sparkAdminRepo.observeSparkDelegations()
        else kotlinx.coroutines.flow.flowOf(emptyList<SparkDelegation>())
    }.collectAsState(initial = emptyList())

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destUri ->
        val src = currentZipFile ?: return@rememberLauncherForActivityResult
        if (destUri == null || !src.exists()) return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(destUri)?.use { out ->
                        src.inputStream().use { inp -> inp.copyTo(out) }
                    }
                }.onSuccess {
                    feedback = "Saved ${src.name} cleanly!" to false
                }.onFailure { err ->
                    feedback = (err.message ?: "Failed to save ZIP") to true
                }
            }
        }
    }

    fun runAccessCheck() {
        scope.launch {
            status = AdminAuthStatus.CHECKING
            statusError = null
            status = runCatching {
                sparkAdminRepo.checkSparkAdminStatus(currentUserId, currentUserEmail)
            }.getOrElse { err ->
                statusError = friendlyFirestoreMessage(err)
                AdminAuthStatus.ERROR
            }
        }
    }

    suspend fun <T> executeAdminWrite(block: suspend () -> Result<T>): Result<T> {
        authRepo.ensureRecentGoogleAuth(context).onFailure { return Result.failure(it) }
        val first = block()
        val firstErr = first.exceptionOrNull()
        if (firstErr != null && firstErr.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true) {
            authRepo.reauthenticateWithGoogle(context).onFailure { return Result.failure(it) }
            return block()
        }
        return first
    }

    LaunchedEffect(currentUserId, currentUserEmail) {
        runAccessCheck()
        currentZipInfo = sparkAdminRepo.getCurrentQeZipInfo()
        val existingZip = File(context.filesDir, "qe_releases").listFiles()
            ?.firstOrNull { it.extension.equals("zip", ignoreCase = true) }
        currentZipFile = existingZip
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Simple Admin Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    ChatboxLogoWithUpdateBadge(
                        appUpdateActive = appUpdate.active,
                        onUpdateClick = onAppUpdateClick,
                        logoSize = 34.dp
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.admin_panel_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.ink
                        )
                        Text(
                            text = currentUserEmail?.takeUnless { it.endsWith("@phone.chatbox.app") } ?: "Admin session",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkSubtle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                OutlinedButton(
                    enabled = !busy && isOnline,
                    onClick = {
                        scope.launch {
                            busy = true
                            feedback = null
                            authRepo.reauthenticateWithGoogle(context)
                                .onSuccess {
                                    if (status == AdminAuthStatus.UNCLAIMED_OWNER && currentUserEmail != null) {
                                        sparkAdminRepo.claimSparkOwnership(currentUserId, currentUserEmail)
                                    }
                                    runAccessCheck()
                                    feedback = "Google verified! Admin control session refreshed." to false
                                }
                                .onFailure { err ->
                                    feedback = (err.message ?: "Google verification failed.") to true
                                }
                            busy = false
                        }
                    },
                    modifier = Modifier.testTag("admin_verify_google_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Verify Google")
                }
            }

            if (!isOnline) {
                NoticeBanner(text = "Offline · Admin controls require internet connection.", isError = true)
            }
            feedback?.let { (msg, isErr) -> NoticeBanner(text = msg, isError = isErr) }
            statusError?.let { err -> NoticeBanner(text = err, isError = true) }

            when (status) {
                AdminAuthStatus.CHECKING -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Checking admin permission…",
                            modifier = Modifier.padding(28.dp),
                            color = colors.inkSubtle
                        )
                    }
                }
                AdminAuthStatus.DENIED, AdminAuthStatus.IDLE -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Shield, contentDescription = null, tint = ChatboxErrorInk, modifier = Modifier.size(36.dp))
                            Text("Admin Only", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Only verified admin Gmail accounts can access this panel.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkMuted
                            )
                        }
                    }
                }
                AdminAuthStatus.UNCLAIMED_OWNER -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = ChatboxPrimary, modifier = Modifier.size(36.dp))
                            Text("Verify Google to unlock Admin Dashboard", style = MaterialTheme.typography.titleMedium)
                            Button(
                                enabled = !busy,
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        authRepo.reauthenticateWithGoogle(context)
                                            .onSuccess {
                                                if (currentUserEmail != null) {
                                                    sparkAdminRepo.claimSparkOwnership(currentUserId, currentUserEmail)
                                                }
                                                runAccessCheck()
                                            }
                                            .onFailure { err -> feedback = (err.message ?: "Verification failed.") to true }
                                        busy = false
                                    }
                                }
                            ) {
                                Text("Verify Google Now")
                            }
                        }
                    }
                }
                AdminAuthStatus.INVITED_DELEGATE -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = ChatboxPrimary, modifier = Modifier.size(36.dp))
                            Text("Admin Invitation Ready", style = MaterialTheme.typography.titleMedium)
                            Button(
                                enabled = !busy && isOnline && currentUserEmail != null,
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        sparkAdminRepo.acceptSparkDelegation(currentUserId, currentUserEmail!!)
                                            .onSuccess {
                                                runAccessCheck()
                                                feedback = "Admin access unlocked!" to false
                                            }
                                            .onFailure { err -> feedback = friendlyFirestoreMessage(err) to true }
                                        busy = false
                                    }
                                }
                            ) {
                                Text("Accept Admin Access")
                            }
                        }
                    }
                }
                AdminAuthStatus.ERROR -> {
                    Button(onClick = { runAccessCheck() }) { Text("Retry check") }
                }
                AdminAuthStatus.OWNER, AdminAuthStatus.DELEGATE -> {
                    // Simple 2-tab Admin bar: Dashboard & QE Coder
                    Surface(
                        color = colors.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                onClick = { adminTab = "dashboard" },
                                shape = RoundedCornerShape(12.dp),
                                color = if (adminTab == "dashboard") ChatboxSoftLavender else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_tab_dashboard")
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.Dashboard,
                                        contentDescription = null,
                                        tint = if (adminTab == "dashboard") ChatboxPrimary else colors.inkMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Dashboard",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (adminTab == "dashboard") ChatboxPrimary else colors.inkMuted
                                    )
                                }
                            }
                            Surface(
                                onClick = { adminTab = "qeCoder" },
                                shape = RoundedCornerShape(12.dp),
                                color = if (adminTab == "qeCoder") ChatboxSoftLavender else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_tab_qe_coder")
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.FolderZip,
                                        contentDescription = null,
                                        tint = if (adminTab == "qeCoder") ChatboxPrimary else colors.inkMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "QE Coder",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (adminTab == "qeCoder") ChatboxPrimary else colors.inkMuted
                                    )
                                }
                            }
                        }
                    }

                    if (adminTab == "dashboard") {
                        // 1. Natural-Language Dashboard Builder
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            border = BorderStroke(1.dp, colors.border)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "App Control & Live Builder",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.ink
                                )
                                Text(
                                    text = "অ্যাপে যা যোগ বা পরিবর্তন করতে চান বিস্তারিত লিখুন (যেমন: নতুন মেনু বার যোগ, থিম পরিবর্তন, বা নোটিশ) এবং Submit করুন:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.inkMuted
                                )
                                OutlinedTextField(
                                    value = promptDraft,
                                    onValueChange = { promptDraft = it.take(1500) },
                                    minLines = 3,
                                    enabled = !busy && isOnline,
                                    placeholder = {
                                        Text("যেমন: একটা নতুন মেনু বার যোগ করো নাম Notice, সেখানে লেখা থাকবে প্রতিদিন রাত ১০টায় লাইভ আড্ডা হবে...")
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("admin_builder_prompt_input")
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Button(
                                        enabled = promptDraft.isNotBlank() && !busy && isOnline,
                                        onClick = {
                                            scope.launch {
                                                busy = true
                                                feedback = null
                                                runCatching {
                                                    AdminPlanInterpreter.interpret(promptDraft, dynamicConfig.customMenus)
                                                }.onSuccess { plan ->
                                                    pendingPlan = plan
                                                }.onFailure { err ->
                                                    feedback = (err.message ?: "Unable to interpret request.") to true
                                                }
                                                busy = false
                                            }
                                        },
                                        modifier = Modifier.testTag("admin_preview_submit_button")
                                    ) {
                                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (busy) "Preparing…" else "Preview & Submit")
                                    }
                                }

                                pendingPlan?.let { plan ->
                                    Surface(
                                        color = Color(0xFFF7F5FB),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, ChatboxPrimary.copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("Ready to update live app:", style = MaterialTheme.typography.labelLarge, color = ChatboxPrimary)
                                            Text(plan.summary, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
                                            plan.addOrUpdateMenu?.let { menu ->
                                                Surface(
                                                    color = Color.White,
                                                    shape = RoundedCornerShape(10.dp),
                                                    border = BorderStroke(1.dp, colors.border)
                                                ) {
                                                    Text(
                                                        text = "New Menu: ${menu.label} — ${menu.title}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        modifier = Modifier.padding(10.dp)
                                                    )
                                                }
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                                TextButton(onClick = { pendingPlan = null }) { Text("Cancel") }
                                                Spacer(Modifier.width(6.dp))
                                                Button(
                                                    enabled = !busy && isOnline,
                                                    colors = ButtonDefaults.buttonColors(containerColor = ChatboxUpdateGreen),
                                                    onClick = {
                                                        scope.launch {
                                                            busy = true
                                                            feedback = null
                                                            executeAdminWrite {
                                                                sparkAdminRepo.applyAdminAiUpdatePlan(currentUserId, dynamicConfig, plan)
                                                            }.onSuccess { summary ->
                                                                pendingPlan = null
                                                                promptDraft = ""
                                                                feedback = summary to false
                                                            }.onFailure { err ->
                                                                feedback = friendlyFirestoreMessage(err) to true
                                                            }
                                                            busy = false
                                                        }
                                                    },
                                                    modifier = Modifier.testTag("admin_apply_plan_button")
                                                ) {
                                                    Text(if (busy) "Updating…" else "Apply Update Now")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Quick App Controls
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            border = BorderStroke(1.dp, colors.border)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Quick App Controls", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("APP THEME", style = MaterialTheme.typography.labelSmall, color = colors.inkSubtle)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf("default", "lavender").forEach { theme ->
                                                FilterChip(
                                                    selected = appearance.theme == theme,
                                                    enabled = !busy && isOnline,
                                                    onClick = {
                                                        scope.launch {
                                                            busy = true
                                                            executeAdminWrite {
                                                                sparkAdminRepo.applySparkProposal(currentUserId, SparkCommandProposal.SetTheme(theme))
                                                            }.onSuccess { feedback = it to false }
                                                                .onFailure { feedback = friendlyFirestoreMessage(it) to true }
                                                            busy = false
                                                        }
                                                    },
                                                    label = { Text(theme.replaceFirstChar { it.uppercase() }) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = ChatboxSoftLavender,
                                                        selectedLabelColor = ChatboxPrimary
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("MENU POSITION", style = MaterialTheme.typography.labelSmall, color = colors.inkSubtle)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf("bottom", "top").forEach { pos ->
                                                FilterChip(
                                                    selected = appearance.menu == pos,
                                                    enabled = !busy && isOnline,
                                                    onClick = {
                                                        scope.launch {
                                                            busy = true
                                                            executeAdminWrite {
                                                                sparkAdminRepo.applySparkProposal(currentUserId, SparkCommandProposal.SetMenu(pos))
                                                            }.onSuccess { feedback = it to false }
                                                                .onFailure { feedback = friendlyFirestoreMessage(it) to true }
                                                            busy = false
                                                        }
                                                    },
                                                    label = { Text(pos.replaceFirstChar { it.uppercase() }) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = ChatboxSoftLavender,
                                                        selectedLabelColor = ChatboxPrimary
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = welcomeDraft,
                                        onValueChange = { welcomeDraft = it.take(ChatboxConstants.MAX_WELCOME_LENGTH) },
                                        label = { Text("Welcome Banner") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        enabled = !busy && welcomeDraft.isNotBlank() && isOnline,
                                        onClick = {
                                            scope.launch {
                                                busy = true
                                                executeAdminWrite {
                                                    sparkAdminRepo.applySparkProposal(currentUserId, SparkCommandProposal.SetWelcome(welcomeDraft))
                                                }.onSuccess { feedback = it to false }
                                                    .onFailure { feedback = friendlyFirestoreMessage(it) to true }
                                                busy = false
                                            }
                                        }
                                    ) {
                                        Text("Save")
                                    }
                                }

                                if (dynamicConfig.customMenus.isNotEmpty()) {
                                    HorizontalDivider(color = colors.border)
                                    Text(
                                        text = "CUSTOM MENU BARS (${dynamicConfig.customMenus.size}/5)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.inkSubtle
                                    )
                                    dynamicConfig.customMenus.forEach { menu ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = colors.fieldBg,
                                            border = BorderStroke(1.dp, colors.border)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    customMenuIconVector(menu.iconEnum),
                                                    contentDescription = null,
                                                    tint = ChatboxPrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("${menu.label} · ${menu.title}", style = MaterialTheme.typography.labelLarge)
                                                    Text(
                                                        menu.body,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = colors.inkMuted,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                IconButton(
                                                    enabled = !busy,
                                                    onClick = {
                                                        scope.launch {
                                                            busy = true
                                                            val nextMenus = dynamicConfig.customMenus.filterNot { it.id == menu.id }
                                                            executeAdminWrite {
                                                                sparkAdminRepo.saveSparkDynamicConfig(
                                                                    currentUserId,
                                                                    dynamicConfig.copy(customMenus = nextMenus)
                                                                )
                                                            }.onSuccess {
                                                                feedback = "Removed custom menu \"${menu.label}\"." to false
                                                            }.onFailure {
                                                                feedback = friendlyFirestoreMessage(it) to true
                                                            }
                                                            busy = false
                                                        }
                                                    }
                                                ) {
                                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove menu", tint = ChatboxErrorInk)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Owner Gmail Delegation Toggle
                        if (status == AdminAuthStatus.OWNER) {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.surface),
                                border = BorderStroke(1.dp, colors.border)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Rounded.PersonAdd, contentDescription = null, tint = ChatboxPrimary, modifier = Modifier.size(18.dp))
                                            Text(
                                                "Admin Gmail Delegates (${delegations.count { it.status == "active" }})",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = ChatboxPrimary
                                            )
                                        }
                                        TextButton(onClick = { showDelegates = !showDelegates }) {
                                            Text(if (showDelegates) "Hide" else "Manage")
                                        }
                                    }

                                    if (showDelegates) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = delegateEmailDraft,
                                                onValueChange = { delegateEmailDraft = it },
                                                placeholder = { Text("partner@gmail.com") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Button(
                                                enabled = SparkAdminCommands.isValidSparkTarget(delegateEmailDraft) && !busy && isOnline,
                                                onClick = {
                                                    scope.launch {
                                                        busy = true
                                                        executeAdminWrite {
                                                            sparkAdminRepo.inviteSparkDelegate(currentUserId, delegateEmailDraft)
                                                        }.onSuccess {
                                                            delegateEmailDraft = ""
                                                            feedback = "Admin invitation sent." to false
                                                        }.onFailure { feedback = friendlyFirestoreMessage(it) to true }
                                                        busy = false
                                                    }
                                                }
                                            ) {
                                                Text("Invite")
                                            }
                                        }
                                        delegations.forEach { del ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("${del.email} (${del.status})", style = MaterialTheme.typography.bodySmall)
                                                if (del.status != "revoked") {
                                                    TextButton(onClick = {
                                                        scope.launch {
                                                            busy = true
                                                            executeAdminWrite {
                                                                sparkAdminRepo.revokeSparkDelegate(del.email)
                                                            }.onSuccess { feedback = "Revoked ${del.email}" to false }
                                                                .onFailure { feedback = friendlyFirestoreMessage(it) to true }
                                                            busy = false
                                                        }
                                                    }) {
                                                        Icon(Icons.Rounded.Cancel, contentDescription = null, tint = ChatboxErrorInk, modifier = Modifier.size(15.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Revoke", color = ChatboxErrorInk)
                                                    }
                                                } else {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.inkSubtle, modifier = Modifier.size(14.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Revoked", style = MaterialTheme.typography.bodySmall, color = colors.inkSubtle)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // QE Coder Tab: Single Latest ZIP Release Vault + Green Update Publisher
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            border = BorderStroke(1.dp, colors.border)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("QE Coder · Latest Coding ZIP", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Only the newest version ZIP is kept; previous ZIP files are automatically deleted.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.inkSubtle
                                        )
                                    }
                                    if (appUpdate.active) {
                                        Surface(color = ChatboxUpdateGreenSoft, shape = RoundedCornerShape(8.dp)) {
                                            Text(
                                                "LIVE v${appUpdate.version}",
                                                color = Color(0xFF15803D),
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                currentZipInfo?.let { zipInfo ->
                                    Surface(
                                        color = Color(0xFFF7F5FB),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, Color(0xFFE2DCF2)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    zipInfo.filename,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                Text(
                                                    "v${zipInfo.version} · ${formatFileBytes(zipInfo.sizeBytes)} · ${zipInfo.fileCount} files",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colors.inkMuted
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { exportZipLauncher.launch(zipInfo.filename) },
                                                modifier = Modifier.testTag("qe_export_zip_button")
                                            ) {
                                                Icon(Icons.Rounded.Download, contentDescription = "Save ZIP", modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Export ZIP")
                                            }
                                        }
                                    }
                                } ?: Surface(
                                    color = Color(0xFFF7F5FB),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, Color(0xFFD8D0EC)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "No coding ZIP packaged yet. Release a version below to build the bundle and show the green Update button.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.inkSubtle,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            border = BorderStroke(1.dp, colors.border)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Release New App Update & Coding ZIP", style = MaterialTheme.typography.titleMedium)
                                OutlinedTextField(
                                    value = qeVersion,
                                    onValueChange = { qeVersion = it.take(ChatboxConstants.MAX_UPDATE_VERSION_LENGTH) },
                                    label = { Text("New version (e.g. 1.1.0)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("qe_version_input")
                                )
                                OutlinedTextField(
                                    value = qeNote,
                                    onValueChange = { qeNote = it.take(ChatboxConstants.MAX_UPDATE_NOTE_LENGTH) },
                                    label = { Text("What changed in this update?") },
                                    minLines = 2,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("qe_note_input")
                                )
                                OutlinedTextField(
                                    value = qeApkUrl,
                                    onValueChange = { qeApkUrl = it.take(ChatboxConstants.MAX_UPDATE_URL_LENGTH) },
                                    label = { Text("Optional GitHub APK HTTPS URL") },
                                    placeholder = { Text(ChatboxConstants.GITHUB_RELEASES_BASE_URL) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        enabled = !busy && qeVersion.isNotBlank() && isOnline,
                                        colors = ButtonDefaults.buttonColors(containerColor = ChatboxUpdateGreen),
                                        onClick = {
                                            scope.launch {
                                                busy = true
                                                feedback = null
                                                val zipRes = sparkAdminRepo.packageQeReleaseZip(
                                                    version = qeVersion,
                                                    note = qeNote,
                                                    dynamicConfig = dynamicConfig,
                                                    appearance = appearance
                                                )
                                                zipRes.onSuccess { (info, file) ->
                                                    currentZipInfo = info
                                                    currentZipFile = file
                                                    val cleanApkUrl = qeApkUrl.trim().takeIf { it.startsWith("https://") }
                                                        ?: info.downloadUrl
                                                    executeAdminWrite {
                                                        sparkAdminRepo.applySparkProposal(
                                                            currentUserId,
                                                            SparkCommandProposal.SetAppUpdate(
                                                                active = true,
                                                                version = qeVersion.trim(),
                                                                note = qeNote.trim().ifEmpty { "New release v${qeVersion.trim()} is available." },
                                                                downloadUrl = cleanApkUrl
                                                            )
                                                        )
                                                    }.onSuccess {
                                                        feedback = "Released v${info.version}! Old ZIP deleted, ${info.filename} packaged, and green Update button shown to all users." to false
                                                    }.onFailure { err ->
                                                        feedback = friendlyFirestoreMessage(err) to true
                                                    }
                                                }.onFailure { err ->
                                                    feedback = (err.message ?: "Failed to package ZIP.") to true
                                                }
                                                busy = false
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("qe_publish_button")
                                    ) {
                                        Text(if (busy) "Packaging…" else "Package ZIP & Publish Update")
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (appUpdate.active) {
                                        OutlinedButton(
                                            enabled = !busy && isOnline,
                                            onClick = {
                                                scope.launch {
                                                    busy = true
                                                    executeAdminWrite {
                                                        sparkAdminRepo.applySparkProposal(
                                                            currentUserId,
                                                            SparkCommandProposal.SetAppUpdate(false, "", "", "")
                                                        )
                                                    }.onSuccess { feedback = it to false }
                                                        .onFailure { feedback = friendlyFirestoreMessage(it) to true }
                                                    busy = false
                                                }
                                            }
                                        ) {
                                            Text("Hide Green Button")
                                        }
                                    } else {
                                        Spacer(Modifier.width(1.dp))
                                    }

                                    TextButton(onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse(ChatboxConstants.GITHUB_RELEASES_BASE_URL)
                                                )
                                            )
                                        }
                                    }) {
                                        Text("GitHub APK Releases")
                                        Spacer(Modifier.width(4.dp))
                                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
