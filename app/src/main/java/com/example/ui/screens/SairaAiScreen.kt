package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.ai.AiChatMessage
import com.example.data.ai.AiFallbackProviderKind
import com.example.data.ai.SairaAiClient
import com.example.data.ai.SairaPrompts
import com.example.data.model.AiExchange
import com.example.data.model.SairaChatSession
import com.example.data.repository.SairaSessionRepository
import com.example.saira.SairaNotificationRule
import com.example.saira.SairaPlatform
import com.example.ui.components.ChatboxLogoWithUpdateBadge
import com.example.ui.components.NoticeBanner
import com.example.ui.components.SairaMetaOrb
import com.example.ui.theme.ChatboxErrorInk
import com.example.ui.theme.ChatboxPrimary
import com.example.ui.theme.ChatboxSoftLavender
import com.example.ui.theme.ChatboxUpdateGreen
import com.example.ui.theme.LocalChatboxColors
import com.example.ui.theme.MetaOrbCyan
import com.example.ui.theme.MetaOrbIndigo
import com.example.ui.theme.MetaOrbPurple
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.random.Random

private const val SESSION_ROLLOVER_LIMIT = 20

private val starterPrompts = listOf(
    "বাংলায় একটি সুন্দর শুভ সকালের মেসেজ লিখে দাও",
    "Explain quantum computing simply in 3 points",
    "Help me plan a productive daily routine",
    "Write a friendly reply when I am busy at work"
)

@Composable
fun SairaAiScreen(
    currentUserId: String,
    isOnline: Boolean,
    appUpdateActive: Boolean,
    onAppUpdateClick: () -> Unit,
    oneMinAutoReplyEnabled: Boolean,
    onChangeOneMinAutoReply: (Boolean) -> Unit,
    notifAutoReplyEnabled: Boolean,
    onChangeNotifAutoReply: (Boolean) -> Unit,
    rules: List<SairaNotificationRule>,
    onSaveRules: (List<SairaNotificationRule>) -> Unit,
    fallbackKindId: String,
    fallbackApiKey: String,
    fallbackBaseUrl: String,
    fallbackModel: String,
    onSaveFallbackConfig: (kindId: String, apiKey: String, baseUrl: String, model: String) -> Unit,
    sessionRepo: SairaSessionRepository
) {
    val colors = LocalChatboxColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedSubTab by remember { mutableStateOf("chat") } // "chat" | "autoReply"
    var draft by remember { mutableStateOf("") }
    var exchanges by remember { mutableStateOf<List<AiExchange>>(emptyList()) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var copiedId by remember { mutableStateOf<String?>(null) }

    var sessionId by remember {
        mutableStateOf("saira_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}")
    }
    var sessionIsNew by remember { mutableStateOf(true) }
    var rolloverCount by remember { mutableIntStateOf(0) }
    var rolloverContextNote by remember { mutableStateOf("") }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showBackupKeyDialog by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    val savedSessions by remember(currentUserId) {
        sessionRepo.observeSessions(currentUserId)
    }.collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        SairaAiClient.warmUp()
    }

    if (showHistoryDialog) {
        BackHandler { showHistoryDialog = false }
    }

    fun startNewThread(carryContext: String = "") {
        activeJob?.cancel()
        activeJob = null
        sessionId = "saira_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
        sessionIsNew = true
        exchanges = emptyList()
        errorMessage = null
        isBusy = false
        rolloverContextNote = carryContext
    }

    fun openSavedSession(session: SairaChatSession) {
        activeJob?.cancel()
        showHistoryDialog = false
        isBusy = false
        errorMessage = null
        sessionId = session.id
        sessionIsNew = false
        rolloverContextNote = ""
        scope.launch {
            sessionRepo.loadExchanges(currentUserId, session.id)
                .onSuccess { loaded -> exchanges = loaded }
                .onFailure { err -> errorMessage = err.message ?: "Unable to load saved thread." }
        }
    }

    fun sendPrompt(rawQuestion: String) {
        val question = rawQuestion.trim()
        if (isBusy || question.isEmpty() || !isOnline || question.length > 2000) return

        var activeSessionId = sessionId
        var activeIsNew = sessionIsNew
        var prevExchanges = exchanges
        var activeContextNote = rolloverContextNote

        // Unlimited seamless session rollover if thread hits 20 exchanges
        if (prevExchanges.size >= SESSION_ROLLOVER_LIMIT) {
            val recentSummary = prevExchanges.takeLast(3).joinToString("\n") { ex ->
                "User: ${ex.question.take(120)} | Saira: ${ex.answer.take(160)}"
            }
            activeSessionId = "saira_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
            activeIsNew = true
            activeContextNote = recentSummary
            sessionId = activeSessionId
            sessionIsNew = true
            rolloverContextNote = recentSummary
            rolloverCount += 1
            prevExchanges = emptyList()
            exchanges = emptyList()
        }

        val exId = "ex_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}"
        val isFirstTurn = prevExchanges.isEmpty() && activeContextNote.isEmpty()
        val historyForModel = buildList {
            if (activeContextNote.isNotEmpty()) {
                add(AiChatMessage("user", "Earlier conversation summary"))
                add(AiChatMessage("model", activeContextNote))
            }
            prevExchanges.takeLast(16).forEach { ex ->
                add(AiChatMessage("user", ex.question))
                add(AiChatMessage("model", ex.answer))
            }
        }

        val turnPrompt = SairaPrompts.buildTurnPrompt(
            question = question,
            isFirstTurn = isFirstTurn,
            isContinuation = activeContextNote.isNotEmpty()
        )

        draft = ""
        isBusy = true
        errorMessage = null
        exchanges = prevExchanges + AiExchange(id = exId, question = question, answer = "", isStreaming = true)

        activeJob = scope.launch {
            val accumulated = StringBuilder()
            try {
                withTimeout(50_000L) {
                    SairaAiClient.stream(
                        systemInstruction = SairaPrompts.chatSystemInstruction,
                        history = historyForModel,
                        prompt = turnPrompt
                    ).collect { chunk ->
                        if (chunk.isNotEmpty()) {
                            accumulated.append(chunk)
                            val currentText = accumulated.toString()
                            exchanges = exchanges.map { item ->
                                if (item.id == exId) item.copy(answer = currentText, isStreaming = true)
                                else item
                            }
                        }
                    }
                }
                val finalAnswer = accumulated.toString().trim()
                if (finalAnswer.isNotEmpty()) {
                    exchanges = exchanges.map { item ->
                        if (item.id == exId) item.copy(answer = finalAnswer, isStreaming = false)
                        else item
                    }
                    val title = (prevExchanges.firstOrNull()?.question ?: question).take(90).ifEmpty { "Saira Chat" }
                    sessionIsNew = false
                    sessionRepo.persistExchange(
                        uid = currentUserId,
                        sessionId = activeSessionId,
                        isNewSession = activeIsNew,
                        title = title,
                        question = question,
                        answer = finalAnswer,
                        sequence = prevExchanges.size
                    )
                } else {
                    exchanges = exchanges.filterNot { it.id == exId }
                    if (draft.isEmpty()) draft = question
                    errorMessage = "Saira returned an empty response. Please try again."
                }
            } catch (e: TimeoutCancellationException) {
                exchanges = exchanges
                    .filter { it.id != exId || it.answer.isNotEmpty() }
                    .map { it.copy(isStreaming = false) }
                if (accumulated.isEmpty() && draft.isEmpty()) draft = question
                errorMessage = e.message ?: "Saira AI timed out. Please try again."
            } catch (_: CancellationException) {
                exchanges = exchanges
                    .filter { it.id != exId || it.answer.isNotEmpty() }
                    .map { it.copy(isStreaming = false) }
            } catch (e: Exception) {
                exchanges = exchanges
                    .filter { it.id != exId || it.answer.isNotEmpty() }
                    .map { it.copy(isStreaming = false) }
                if (accumulated.isEmpty() && draft.isEmpty()) draft = question
                errorMessage = e.message ?: "Saira AI is temporarily unavailable."
            } finally {
                isBusy = false
                activeJob = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
    ) {
        // Meta-AI Styled Top Header
        Surface(color = colors.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    ChatboxLogoWithUpdateBadge(
                        appUpdateActive = appUpdateActive,
                        onUpdateClick = onAppUpdateClick,
                        logoSize = 30.dp
                    )
                    SairaMetaOrb(size = 30.dp, animate = isBusy)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Saira AI",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                            maxLines = 1
                        )
                        Text(
                            text = if (rolloverCount > 0) {
                                "Always on · Thread ${rolloverCount + 1}"
                            } else {
                                "Always on · Unlimited continuity"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkSubtle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        color = colors.fieldBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.border)
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            Surface(
                                onClick = { selectedSubTab = "chat" },
                                color = if (selectedSubTab == "chat") Color.White else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("saira_subtab_chat")
                            ) {
                                Text(
                                    text = "Chat",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selectedSubTab == "chat") ChatboxPrimary else colors.inkMuted,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            Surface(
                                onClick = { selectedSubTab = "autoReply" },
                                color = if (selectedSubTab == "autoReply") Color.White else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("saira_subtab_rules")
                            ) {
                                val activeRules = rules.count { it.enabled }
                                Text(
                                    text = if (activeRules > 0) "Rules ($activeRules)" else "Auto-Reply",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selectedSubTab == "autoReply") ChatboxPrimary else colors.inkMuted,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    if (selectedSubTab == "chat") {
                        IconButton(
                            onClick = { showHistoryDialog = true },
                            modifier = Modifier.testTag("saira_history_button")
                        ) {
                            Icon(Icons.Rounded.History, contentDescription = "Saved threads", tint = ChatboxPrimary)
                        }
                        IconButton(
                            onClick = { startNewThread() },
                            modifier = Modifier.testTag("saira_new_chat_button")
                        ) {
                            Icon(Icons.Rounded.AddComment, contentDescription = "New Saira chat", tint = ChatboxPrimary)
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = colors.border)

        if (selectedSubTab == "chat") {
            val listState = rememberLazyListState()
            LaunchedEffect(exchanges.size, exchanges.lastOrNull()?.answer?.length) {
                if (exchanges.isNotEmpty()) {
                    listState.animateScrollToItem(exchanges.size - 1)
                }
            }

            if (!isOnline) {
                NoticeBanner(
                    text = "Offline · Connect to the internet to chat with Saira AI.",
                    isError = true,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            errorMessage?.let { err ->
                NoticeBanner(
                    text = err,
                    isError = true,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            if (exchanges.isEmpty()) {
                // Meta AI Starter Hero
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (rolloverContextNote.isNotEmpty()) {
                        Text(
                            text = "Auto-continued in fresh unlimited thread · context preserved",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.inkSubtle,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    SairaMetaOrb(size = 64.dp, animate = isBusy)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Ask Saira AI anything",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.ink,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "বাংলা, Banglish বা ইংরেজিতে যেকোনো প্রশ্ন করুন — আমি সবসময় প্রস্তুত। লিমিট শেষ হলে ব্যাকআপ AI ও নতুন থ্রেডে অটোমেটিক চলতে থাকবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 420.dp)
                    )
                    Spacer(Modifier.height(18.dp))
                    Column(
                        modifier = Modifier.widthIn(max = 480.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        starterPrompts.forEach { prompt ->
                            Surface(
                                onClick = { sendPrompt(prompt) },
                                enabled = isOnline && !isBusy,
                                shape = RoundedCornerShape(16.dp),
                                color = colors.surface,
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = prompt,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.ink,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(exchanges, key = { it.id }) { ex ->
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // User right-aligned pill
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Surface(
                                    color = ChatboxPrimary,
                                    shape = RoundedCornerShape(
                                        topStart = 22.dp,
                                        topEnd = 22.dp,
                                        bottomStart = 22.dp,
                                        bottomEnd = 6.dp
                                    ),
                                    modifier = Modifier.widthIn(max = 320.dp)
                                ) {
                                    Text(
                                        text = ex.question,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                    )
                                }
                            }

                            // Meta AI style left-aligned answer with orb
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                SairaMetaOrb(
                                    size = 26.dp,
                                    animate = ex.isStreaming,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Saira AI",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ChatboxPrimary
                                    )
                                    Surface(
                                        color = colors.surface,
                                        shape = RoundedCornerShape(18.dp),
                                        border = BorderStroke(1.dp, colors.border),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (ex.answer.isEmpty() && ex.isStreaming) {
                                                "Thinking…"
                                            } else {
                                                ex.answer
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (ex.answer.isEmpty() && ex.isStreaming) {
                                                colors.inkSubtle
                                            } else {
                                                colors.ink
                                            },
                                            modifier = Modifier.padding(14.dp)
                                        )
                                    }
                                    if (!ex.isStreaming && ex.answer.isNotEmpty()) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            TextButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Saira AI", ex.answer))
                                                    copiedId = ex.id
                                                    scope.launch {
                                                        delay(1600)
                                                        copiedId = null
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (copiedId == ex.id) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                                    contentDescription = "Copy",
                                                    tint = if (copiedId == ex.id) ChatboxUpdateGreen else colors.inkSubtle,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = if (copiedId == ex.id) "Copied" else "Copy",
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                            TextButton(
                                                enabled = !isBusy && isOnline,
                                                onClick = { sendPrompt(ex.question) }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Refresh,
                                                    contentDescription = "Ask again",
                                                    tint = colors.inkSubtle,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text("Ask again", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Meta AI Floating Gradient Pill Composer
            Surface(color = colors.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SairaMetaOrb(size = 24.dp, animate = isBusy)
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(2000) },
                        enabled = isOnline && !isBusy,
                        placeholder = {
                            Text(if (isOnline) "Ask Saira AI anything…" else "Offline · Saira disabled")
                        },
                        singleLine = true,
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("saira_prompt_input")
                    )
                    if (isBusy) {
                        Surface(
                            onClick = { activeJob?.cancel() },
                            shape = CircleShape,
                            color = ChatboxSoftLavender,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("saira_stop_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Stop, contentDescription = "Stop", tint = ChatboxPrimary)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(MetaOrbPurple, MetaOrbIndigo, MetaOrbCyan)
                                    )
                                )
                                .clickable(enabled = isOnline && draft.isNotBlank()) { sendPrompt(draft) }
                                .testTag("saira_send_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.ArrowUpward,
                                contentDescription = "Send prompt",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        } else {
            // Auto-Reply + Always-On Backup AI Provider Settings Tab
            AutoReplyAndBackupTab(
                oneMinAutoReplyEnabled = oneMinAutoReplyEnabled,
                onChangeOneMinAutoReply = onChangeOneMinAutoReply,
                notifAutoReplyEnabled = notifAutoReplyEnabled,
                onChangeNotifAutoReply = onChangeNotifAutoReply,
                rules = rules,
                onSaveRules = onSaveRules,
                hasBackupKeyConfigured = fallbackApiKey.isNotBlank(),
                onOpenBackupKeyModal = { showBackupKeyDialog = true }
            )
        }
    }

    if (showHistoryDialog) {
        SavedThreadsDialog(
            sessions = savedSessions,
            activeSessionId = sessionId,
            onStartNew = {
                showHistoryDialog = false
                startNewThread()
            },
            onOpenSession = { openSavedSession(it) },
            onDeleteSession = { s ->
                scope.launch {
                    sessionRepo.deleteSession(currentUserId, s.id)
                    if (s.id == sessionId) startNewThread()
                }
            },
            onDismiss = { showHistoryDialog = false }
        )
    }

    if (showBackupKeyDialog) {
        BackupAiProviderDialog(
            initialKindId = fallbackKindId,
            initialApiKey = fallbackApiKey,
            initialBaseUrl = fallbackBaseUrl,
            initialModel = fallbackModel,
            onSave = { k, key, url, mod ->
                onSaveFallbackConfig(k, key, url, mod)
                showBackupKeyDialog = false
            },
            onDismiss = { showBackupKeyDialog = false }
        )
    }
}

@Composable
private fun AutoReplyAndBackupTab(
    oneMinAutoReplyEnabled: Boolean,
    onChangeOneMinAutoReply: (Boolean) -> Unit,
    notifAutoReplyEnabled: Boolean,
    onChangeNotifAutoReply: (Boolean) -> Unit,
    rules: List<SairaNotificationRule>,
    onSaveRules: (List<SairaNotificationRule>) -> Unit,
    hasBackupKeyConfigured: Boolean,
    onOpenBackupKeyModal: () -> Unit
) {
    val colors = LocalChatboxColors.current
    var contactDraft by remember { mutableStateOf("") }
    var platformDraft by remember { mutableStateOf(SairaPlatform.ANY) }
    var guidanceDraft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Always-On Backup AI Engine Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.border)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.Key, contentDescription = null, tint = ChatboxPrimary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Always-On Backup AI Failover",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.ink
                        )
                        Text(
                            text = if (hasBackupKeyConfigured) {
                                "Backup key active · Saira automatically switches if Gemini free quota cools down."
                            } else {
                                "Optional: Add a free Groq / OpenRouter / Custom API key so Saira stays online even during peak Gemini quota limits."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                    }
                }
                OutlinedButton(
                    onClick = onOpenBackupKeyModal,
                    modifier = Modifier.testTag("saira_backup_config_button")
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (hasBackupKeyConfigured) "Edit Backup AI Provider" else "Configure Backup AI Key")
                }
            }
        }

        // 1-Minute Absence Companion Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = ChatboxPrimary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Chatbox 1-Min Absence Auto-Reply", style = MaterialTheme.typography.titleSmall, color = colors.ink)
                    Text(
                        "If a friend hasn't replied in Live chats for 1 minute, Saira greets you by name and assists while you wait.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkMuted
                    )
                }
                Switch(
                    checked = oneMinAutoReplyEnabled,
                    onCheckedChange = onChangeOneMinAutoReply,
                    modifier = Modifier.testTag("saira_one_min_switch")
                )
            }
        }

        // Notification Auto-Reply Switch
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Rounded.Notifications, contentDescription = null, tint = ChatboxPrimary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("WhatsApp & Messenger Rules", style = MaterialTheme.typography.titleSmall, color = colors.ink)
                    Text(
                        "Configure taught auto-reply instructions for specific contacts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkMuted
                    )
                }
                Switch(
                    checked = notifAutoReplyEnabled,
                    onCheckedChange = onChangeNotifAutoReply
                )
            }
        }

        // Add Taught Contact Rule Form
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.border)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Teach Saira a Contact Rule", style = MaterialTheme.typography.titleSmall, color = colors.ink)
                OutlinedTextField(
                    value = contactDraft,
                    onValueChange = { contactDraft = it.take(80) },
                    label = { Text("Contact name (e.g. Rahima)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SairaPlatform.entries.forEach { p ->
                        FilterChip(
                            selected = platformDraft == p,
                            onClick = { platformDraft = p },
                            label = { Text(p.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ChatboxSoftLavender,
                                selectedLabelColor = ChatboxPrimary
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = guidanceDraft,
                    onValueChange = { guidanceDraft = it.take(400) },
                    label = { Text("What should Saira reply when they message?") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = contactDraft.isNotBlank() && guidanceDraft.isNotBlank(),
                    onClick = {
                        val next = rules + SairaNotificationRule(
                            id = "${System.currentTimeMillis()}_${Random.nextInt(100, 999)}",
                            contactName = contactDraft.trim(),
                            platform = platformDraft,
                            taughtReplyGuidance = guidanceDraft.trim(),
                            enabled = true
                        )
                        onSaveRules(next)
                        contactDraft = ""
                        guidanceDraft = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Reply Rule")
                }
            }
        }

        if (rules.isNotEmpty()) {
            Text(
                text = "TAUGHT CONTACT RULES (${rules.size})",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkSubtle
            )
            rules.forEach { rule ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${rule.contactName} · ${rule.platform.label}",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.ink
                            )
                            Text(
                                text = rule.taughtReplyGuidance,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkMuted
                            )
                        }
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { checked ->
                                onSaveRules(rules.map { if (it.id == rule.id) it.copy(enabled = checked) else it })
                            }
                        )
                        IconButton(onClick = { onSaveRules(rules.filterNot { it.id == rule.id }) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete rule", tint = ChatboxErrorInk)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedThreadsDialog(
    sessions: List<SairaChatSession>,
    activeSessionId: String,
    onStartNew: () -> Unit,
    onOpenSession: (SairaChatSession) -> Unit,
    onDeleteSession: (SairaChatSession) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Saved Saira Threads", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }
                Button(
                    onClick = onStartNew,
                    colors = ButtonDefaults.buttonColors(containerColor = ChatboxPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.AddComment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New Unlimited Thread")
                }
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (sessions.isEmpty()) {
                        item {
                            Text(
                                text = "Your saved Saira conversations appear here automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8F89A3),
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    } else {
                        items(sessions, key = { it.id }) { s ->
                            val isSelected = s.id == activeSessionId
                            Surface(
                                onClick = { onOpenSession(s) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) ChatboxSoftLavender else Color(0xFFF7F5FB),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = s.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = s.lastPreview,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF6B657D),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = { onDeleteSession(s) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete thread", tint = ChatboxErrorInk)
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

@Composable
private fun BackupAiProviderDialog(
    initialKindId: String,
    initialApiKey: String,
    initialBaseUrl: String,
    initialModel: String,
    onSave: (kindId: String, apiKey: String, baseUrl: String, model: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedKind by remember { mutableStateOf(AiFallbackProviderKind.fromId(initialKindId)) }
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var baseUrl by remember { mutableStateOf(initialBaseUrl.ifBlank { selectedKind.defaultBaseUrl }) }
    var model by remember { mutableStateOf(initialModel.ifBlank { selectedKind.defaultModel }) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Backup AI Provider", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }
                Text(
                    text = "If Firebase Gemini free quota cools down, Saira automatically routes via this backup provider.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B657D)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiFallbackProviderKind.entries.forEach { kind ->
                        FilterChip(
                            selected = selectedKind == kind,
                            onClick = {
                                selectedKind = kind
                                if (kind != AiFallbackProviderKind.CUSTOM) {
                                    baseUrl = kind.defaultBaseUrl
                                    model = kind.defaultModel
                                }
                            },
                            label = { Text(kind.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("${selectedKind.label} API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(selectedKind.id, apiKey, baseUrl, model) },
                        modifier = Modifier.testTag("save_backup_ai_button")
                    ) {
                        Text("Save Backup")
                    }
                }
            }
        }
    }
}
