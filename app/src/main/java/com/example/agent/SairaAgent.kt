package com.example.agent

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

/** One bubble in the admin ↔ Saira agent conversation. */
data class AgentMessage(
    val id: String,
    val fromSaira: Boolean,
    val text: String,
    val attachmentNote: String = "",
    /** Non-null = Saira is waiting for a yes/no before doing something big. */
    val confirm: AgentAction? = null,
    val resolved: Boolean = false
)

data class AgentAction(
    val type: String,
    val instruction: String,
    val title: String,
    /** Public image URLs (admin's attached logo/pictures) the coding agent adds as drawables. */
    val assets: List<String> = emptyList(),
    /** Public screenshot URLs that only show the coding agent WHAT to change (never added to the app). */
    val refs: List<String> = emptyList()
)

/** Long-running app change being built on GitHub. */
data class AgentJob(
    val requestId: String,
    val instruction: String,
    val runId: Long,
    /** dispatched | running | releasing | installing */
    val phase: String,
    val startedAtMs: Long,
    val baseBuild: Int
)

/** Hooks the agent needs from the rest of the app (live config, gallery). */
class AgentHooks(
    val applyLiveConfig: suspend (String) -> Result<String>,
    val recentGalleryPhotos: suspend (Int) -> List<AgentAttachment>
)

/**
 * Saira's admin-only agent: understands the admin's command, then really does
 * it — changes the app's code via GitHub (the change is built, tested and
 * released by CI, then installed in-app), changes the live app config in
 * Firebase, runs Firebase admin reports, reads Drive / gallery files.
 */
object SairaAgent {
    private const val TAG = "SairaAgent"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _messages = MutableStateFlow<List<AgentMessage>>(emptyList())
    val messages: StateFlow<List<AgentMessage>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _github = MutableStateFlow<GitHubIdentity?>(null)
    val github: StateFlow<GitHubIdentity?> = _github.asStateFlow()

    private val _job = MutableStateFlow<AgentJob?>(null)
    val job: StateFlow<AgentJob?> = _job.asStateFlow()

    private val _autoInstall = MutableStateFlow(true)
    val autoInstall: StateFlow<Boolean> = _autoInstall.asStateFlow()

    private val _proKeys = MutableStateFlow<List<ProKey>>(emptyList())
    /** The admin's own AI keys (masked in the UI) that power Saira's pro brain. */
    val proKeys: StateFlow<List<ProKey>> = _proKeys.asStateFlow()

    private var store: SecureStore? = null
    private var ai: SairaAgentAi? = null
    private var appContext: Context? = null
    private var watcher: Job? = null
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        appContext = app
        val s = SecureStore(app)
        store = s
        ai = SairaAgentAi(app)
        _autoInstall.value = s.getPlain("auto_install") != "0"
        _proKeys.value = ProBrain.parse(s.get(ProBrain.STORE_KEY))
        restoreProKeysFromVault(app)
        _messages.value = loadMessages(s)
        if (_messages.value.isEmpty()) {
            _messages.value = listOf(
                sairaMsg(
                    "আমি Saira — আপনার অ্যাডমিন এজেন্ট। আমাকে বলুন অ্যাপে কী বদলাতে বা যোগ করতে হবে, " +
                        "আমি GitHub-এ কোড লিখে, বিল্ড-টেস্ট করে নতুন ভার্সন বানিয়ে অ্যাপের ভেতরেই আপডেট বসিয়ে দেব। " +
                        "Drive/গ্যালারির ফাইল দিলে পড়ে বুঝিয়ে দেব, Firebase-এর রিপোর্টও দেখাতে পারি। " +
                        "শুরু করতে উপরের Connected থেকে GitHub যুক্ত করুন।"
                )
            )
        }
        s.get("github_token")?.let { token ->
            scope.launch {
                GitHubClient(token, s.getPlain("github_repo") ?: GitHubClient.DEFAULT_REPO)
                    .verify().onSuccess { _github.value = it }
            }
        }
        loadJob(s)?.let { j ->
            _job.value = j
            if (AppSelfUpdater.installedBuildNumber() > j.baseBuild && j.phase in setOf("releasing", "installing")) {
                post(sairaMsg("✅ নতুন ভার্সন v${BuildConfig.VERSION_NAME} চালু হয়েছে।\nপরিবর্তন: ${taskSummary(j.instruction)}"))
                setJob(null)
            } else {
                startWatcher()
            }
        }
    }

    fun setAutoInstall(on: Boolean) {
        _autoInstall.value = on
        store?.putPlain("auto_install", if (on) "1" else "0")
    }

    suspend fun connectGitHub(token: String, repo: String): Result<GitHubIdentity> {
        val clean = token.trim()
        val r = GitHubClient(clean, repo.trim().ifBlank { GitHubClient.DEFAULT_REPO }).verify()
        r.onSuccess { id ->
            if (!id.canPush) {
                return Result.failure(IllegalStateException("এই token দিয়ে ${id.repo}-এ লেখার/Actions চালানোর অনুমতি নেই।"))
            }
            store?.put("github_token", clean)
            store?.putPlain("github_repo", id.repo)
            _github.value = id
            post(sairaMsg("🔗 GitHub যুক্ত হয়েছে: ${id.login} → ${id.repo}। এখন যেকোনো পরিবর্তনের কথা বলুন।"))
        }
        return r
    }

    // ------------------------------------------------------------ pro brain keys

    /** Merges new pro keys into this phone's encrypted store; returns the full list. */
    fun mergeProKeysLocally(added: List<ProKey>): List<ProKey> {
        val merged = (_proKeys.value.filter { old -> added.none { it.key == old.key } } + added)
            .sortedBy { ProBrain.ORDER.indexOf(it.provider) }
        store?.put(ProBrain.STORE_KEY, ProBrain.serialize(merged))
        _proKeys.value = merged
        return merged
    }

    /** After a reinstall the owner's pro keys come back from the owner-only Firestore vault. */
    private fun restoreProKeysFromVault(context: Context) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val snap = com.example.data.repository.FirebaseProviders.firestore(context)
                    .collection("adminVault").document("sairaProKeys").get().await()
                val fromVault = ProBrain.parse(snap.getString("keys"))
                if (fromVault.isNotEmpty()) mergeProKeysLocally(fromVault)
            }
        }
    }

    fun disconnectGitHub() {
        store?.put("github_token", null)
        _github.value = null
        post(sairaMsg("GitHub সংযোগ মুছে ফেলা হয়েছে।"))
    }

    fun clearChat() {
        _messages.value = emptyList()
        store?.let { saveMessages(it, emptyList()) }
    }

    private fun gh(): GitHubClient? {
        val s = store ?: return null
        val token = s.get("github_token") ?: return null
        return GitHubClient(token, s.getPlain("github_repo") ?: GitHubClient.DEFAULT_REPO)
    }

    // ------------------------------------------------------------------ chat

    fun send(text: String, attachments: List<AgentAttachment>, hooks: AgentHooks) {
        // API keys sent in chat are cut out first: never stored in the chat, never sent to an AI.
        val intake = KeyIntake.extract(text.trim())
        val clean = intake.redacted.trim()
        if (clean.isEmpty() && attachments.isEmpty() && intake.keys.isEmpty()) return
        val note = buildList {
            if (intake.keys.isNotEmpty()) add("🔑 ${intake.keys.size}টা key (লুকানো)")
            if (attachments.isNotEmpty()) add("📎 " + attachments.joinToString { it.name })
        }.joinToString(" · ")
        post(AgentMessage(newId(), false, clean.ifEmpty { if (intake.keys.isNotEmpty()) "(key গুলো বসিয়ে নাও)" else "(ফাইলগুলো দেখো)" }, note))
        _busy.value = true
        scope.launch {
            try {
                if (intake.keys.isNotEmpty()) {
                    val ctx = appContext
                    post(sairaMsg("🔑 ${intake.keys.size}টা key পেয়েছি — পরীক্ষা করে বসাচ্ছি…"))
                    post(sairaMsg(if (ctx != null) KeyIntake.install(ctx, intake.keys) else "—"))
                    // Only the keys? Then we're done; otherwise also handle the rest of the message.
                    val rest = clean.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
                    val trivial = rest.split(" ").filter { it.isNotBlank() }.size <= 12 &&
                        Regex("key|api|bosa|add|নাও|বসা|যোগ|nao|dao|kor").containsMatchIn(rest)
                    if (rest.isEmpty() || trivial) return@launch
                }
                handle(clean, attachments, hooks)
            } catch (e: Exception) {
                Log.w(TAG, "agent failed", e)
                post(sairaMsg(friendlyError(e)))
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun handle(text: String, attachments: List<AgentAttachment>, hooks: AgentHooks) {
        val brain = ai ?: return
        val history = _messages.value.takeLast(20).dropLast(1).filter { it.text.isNotBlank() }
            .map { AgentTurn(if (it.fromSaira) "model" else "user", it.text.take(4000)) }

        val decision = runCatching {
            JSONObject(
                brain.generate(
                    system = PLANNER_SYSTEM,
                    history = history,
                    prompt = "Admin: $text\nAttached files: " +
                        (attachments.joinToString { "${it.name} (${it.mimeType})" }.ifEmpty { "none" }) +
                        "\nGitHub connected: ${_github.value != null}. Running job: ${_job.value?.phase ?: "none"}.",
                    json = true,
                    maxTokens = 1024,
                    fast = true
                )
            )
        }.getOrNull() ?: JSONObject().put("action", "answer")

        val action = decision.optString("action", "answer")
        val instruction = decision.optString("instruction").ifBlank { text }
        when (action) {
            "app_change" -> {
                if (_github.value == null) {
                    post(sairaMsg("অ্যাপের কোড বদলাতে আগে GitHub যুক্ত করতে হবে — উপরের Connected → GitHub-এ token দিন।"))
                } else if (_job.value != null) {
                    post(sairaMsg("আগের একটা পরিবর্তন এখনও চলছে (${phaseLabel(_job.value!!.phase)})। শেষ হলে নতুনটা ধরব।"))
                } else {
                    val images = attachments.filter { it.mimeType.startsWith("image/") }.take(4)
                    val asAsset = decision.optString("image_role") == "asset"
                    val urls = images.mapIndexedNotNull { idx, a ->
                        val ext = if (a.mimeType.contains("png")) "png" else "jpg"
                        com.example.util.MediaHost.uploadBytes(a.bytes, "saira_img_$idx.$ext").getOrNull()
                    }
                    val imageNote = when {
                        images.isEmpty() -> ""
                        urls.size < images.size -> "\n\n⚠️ ${images.size - urls.size}টা ছবি আপলোড হয়নি — সেগুলো ছাড়া কাজ হবে।"
                        asAsset -> "\n\n🖼️ সংযুক্ত ${urls.size}টা ছবি অ্যাপের ভেতরে যোগ করে ব্যবহার করব।"
                        else -> "\n\n🔍 সংযুক্ত ছবি দেখে বুঝে নেব কোন জায়গা বদলাতে হবে (ছবিটা অ্যাপে যোগ হবে না)।"
                    }
                    // The coding agent gets the admin's exact words too, so nothing is lost in translation.
                    val fullInstruction = "Admin's exact words: $text\nSaira's understanding: $instruction"
                    post(
                        sairaMsg(
                            "আমি অ্যাপে এই পরিবর্তনটা করব:\n“${text.ifBlank { instruction }}”\n($instruction)$imageNote\n\nকোড লিখে, কম্পাইল ও টেস্ট পাস করলে নতুন ভার্সন রিলিজ করে অ্যাপের ভেতরেই বসিয়ে দেব (প্রায় ১০–১৫ মিনিট)। শুরু করব?",
                            confirm = AgentAction(
                                "app_change", fullInstruction, "অ্যাপ পরিবর্তন",
                                assets = if (asAsset) urls else emptyList(),
                                refs = if (asAsset) emptyList() else urls
                            )
                        )
                    )
                }
            }
            "app_update" -> post(sairaMsg(appContext?.let { AppSelfUpdater.updateToLatest(it) } ?: "—"))
            "github_status" -> post(sairaMsg(githubStatus()))
            "live_config" -> {
                val r = hooks.applyLiveConfig(instruction)
                post(sairaMsg(r.fold({ "✅ লাইভ অ্যাপে বদলে দিয়েছি (Firebase): $it" }, { "লাইভ কনফিগ বদলানো যায়নি: ${it.message}" })))
            }
            "firebase_report" -> runFirebaseReport()
            "rules_deploy" -> post(
                sairaMsg(
                    "Firestore security rules (repo-তে যেটা আছে) Firebase-এ deploy করব। নিশ্চিত?",
                    confirm = AgentAction("rules_deploy", instruction, "Rules deploy")
                )
            )
            "gallery" -> {
                val count = decision.optInt("count", 6).coerceIn(1, 10)
                val photos = hooks.recentGalleryPhotos(count)
                if (photos.isEmpty()) {
                    post(sairaMsg("গ্যালারি দেখতে অনুমতি দরকার — Connected → Gallery চালু করুন (অথবা 📎 দিয়ে ছবি বেছে দিন)।"))
                } else {
                    answer(text, history, photos)
                }
            }
            else -> answer(text, history, attachments)
        }
    }

    private suspend fun answer(text: String, history: List<AgentTurn>, attachments: List<AgentAttachment>) {
        val brain = ai ?: return
        val reply = brain.generate(
            system = ANSWER_SYSTEM + "\nNow: ${java.text.SimpleDateFormat("EEEE d MMMM yyyy, h:mm a", java.util.Locale.ENGLISH).format(java.util.Date())} (Bangladesh).",
            history = history,
            prompt = text.ifEmpty { "Look at the attached files and explain them." },
            attachments = attachments,
            search = attachments.isEmpty(),
            maxTokens = 8192
        )
        post(sairaMsg(reply))
    }

    fun confirm(messageId: String, accept: Boolean) {
        val msg = _messages.value.firstOrNull { it.id == messageId } ?: return
        val action = msg.confirm ?: return
        _messages.value = _messages.value.map { if (it.id == messageId) it.copy(resolved = true) else it }
        store?.let { saveMessages(it, _messages.value) }
        if (!accept) {
            post(sairaMsg("ঠিক আছে, বাতিল করলাম।"))
            return
        }
        scope.launch {
            _busy.value = true
            try {
                when (action.type) {
                    "app_change" -> startAppChange(action.instruction, action.assets, action.refs)
                    "rules_deploy" -> {
                        val r = gh()?.dispatch(GitHubClient.RULES_WORKFLOW, mapOf("mode" to "deploy"))
                        post(sairaMsg(if (r?.isSuccess == true) "🚀 Rules deploy শুরু হয়েছে (১–২ মিনিট)।" else "Deploy শুরু করা যায়নি: ${r?.exceptionOrNull()?.message}"))
                    }
                }
            } finally {
                _busy.value = false
            }
        }
    }

    // ------------------------------------------------------------ app change

    private suspend fun startAppChange(
        instruction: String,
        assets: List<String> = emptyList(),
        refs: List<String> = emptyList()
    ) {
        val client = gh() ?: run {
            post(sairaMsg("GitHub যুক্ত নেই।"))
            return
        }
        val requestId = "r${System.currentTimeMillis()}"
        val d = client.dispatch(
            GitHubClient.AGENT_WORKFLOW,
            buildMap {
                put("instruction", instruction.take(3000))
                put("request_id", requestId)
                if (assets.isNotEmpty()) put("assets", assets.joinToString(" "))
                if (refs.isNotEmpty()) put("refs", refs.joinToString(" "))
            }
        )
        if (d.isFailure) {
            post(sairaMsg("কাজ শুরু করা যায়নি: ${d.exceptionOrNull()?.message}"))
            return
        }
        post(sairaMsg("🛠️ কাজ শুরু করেছি। কোড লিখছি → কম্পাইল → টেস্ট → রিলিজ। প্রতিটা ধাপে জানাব; অ্যাপ বন্ধ করলেও GitHub-এ কাজ চলবে।"))
        setJob(AgentJob(requestId, instruction, 0L, "dispatched", System.currentTimeMillis(), AppSelfUpdater.installedBuildNumber()))
        startWatcher()
    }

    private fun startWatcher() {
        if (watcher?.isActive == true) return
        watcher = scope.launch {
            while (true) {
                val j = _job.value ?: break
                runCatching { step(j) }.onFailure { Log.w(TAG, "watch step failed", it) }
                if (_job.value == null) break
                if (System.currentTimeMillis() - j.startedAtMs > 90 * 60_000L) {
                    post(sairaMsg("⌛ কাজটা অনেক সময় নিচ্ছে — GitHub-এ চেক করুন বা আবার বলুন।"))
                    setJob(null)
                    break
                }
                delay(12_000L)
            }
        }
    }

    private suspend fun step(j: AgentJob) {
        val client = gh() ?: return
        when (j.phase) {
            "dispatched" -> {
                val run = client.runs(GitHubClient.AGENT_WORKFLOW, 10).getOrNull()
                    ?.firstOrNull { it.title.contains(j.requestId) } ?: return
                setJob(j.copy(runId = run.id, phase = "running"))
            }
            "running" -> {
                val run = client.run(j.runId).getOrNull() ?: return
                if (run.status != "completed") return
                val notes = client.runMessages(j.runId).getOrDefault(emptyList())
                    .filter { !it.startsWith("Process completed") }
                if (run.conclusion == "success") {
                    val summary = notes.firstOrNull { it.contains("সফল") } ?: "পরিবর্তন তৈরি।"
                    post(sairaMsg("✅ $summary\n📦 নতুন ভার্সন রিলিজ হচ্ছে (প্রায় ৬–১০ মিনিট)…"))
                    setJob(j.copy(phase = "releasing"))
                } else {
                    val why = notes.firstOrNull { !it.contains("সফল") && !it.startsWith("PUBLISHED") }
                        ?: "GitHub-এ ব্যর্থ (${run.conclusion})"
                    post(sairaMsg("❌ পরিবর্তনটা প্রকাশ করিনি — $why\nঅ্যাপ আগের মতোই নিরাপদ আছে। নির্দেশটা একটু অন্যভাবে বলে আবার চেষ্টা করতে পারেন।"))
                    setJob(null)
                }
            }
            "releasing" -> {
                val rel = client.latestRelease().getOrNull() ?: return
                if (AppSelfUpdater.buildNumberOf(rel.tag) <= j.baseBuild) return
                if (_autoInstall.value) {
                    post(sairaMsg("🎉 ${rel.tag} তৈরি! অ্যাপের ভেতরেই ইনস্টল করছি…"))
                    setJob(j.copy(phase = "installing"))
                    val ctx = appContext ?: return
                    post(sairaMsg(AppSelfUpdater.updateToLatest(ctx)))
                } else {
                    post(sairaMsg("🎉 ${rel.tag} তৈরি! Connected → \"এখনই আপডেট\" চাপলে অ্যাপের ভেতরেই বসে যাবে।"))
                    setJob(null)
                }
            }
            "installing" -> {
                // The app restarts after a successful update; init() reports it.
                if (System.currentTimeMillis() - j.startedAtMs > 60 * 60_000L) setJob(null)
            }
        }
    }

    private suspend fun runFirebaseReport() {
        val client = gh() ?: run {
            post(sairaMsg("Firebase অ্যাডমিন রিপোর্টের জন্য GitHub যুক্ত থাকতে হবে (সেখানে Firebase admin key রাখা আছে)।"))
            return
        }
        val rid = "f${System.currentTimeMillis()}"
        val d = client.dispatch("saira-firebase.yml", mapOf("op" to "report", "request_id" to rid))
        if (d.isFailure) {
            post(sairaMsg("Firebase রিপোর্ট শুরু করা যায়নি: ${d.exceptionOrNull()?.message}"))
            return
        }
        post(sairaMsg("🔥 Firebase থেকে তথ্য আনছি (প্রায় ১ মিনিট)…"))
        var runId = 0L
        repeat(60) {
            delay(5_000L)
            if (runId == 0L) {
                runId = client.runs("saira-firebase.yml", 5).getOrNull()
                    ?.firstOrNull { it.title.contains(rid) }?.id ?: 0L
            } else {
                val run = client.run(runId).getOrNull()
                if (run?.status == "completed") {
                    val notes = client.runMessages(runId).getOrDefault(emptyList())
                        .filter { !it.startsWith("Process completed") }
                    post(sairaMsg(notes.joinToString("\n").ifBlank { "রিপোর্ট পাওয়া যায়নি (${run.conclusion})।" }))
                    return
                }
            }
        }
        post(sairaMsg("Firebase রিপোর্ট সময়মতো আসেনি।"))
    }

    private suspend fun githubStatus(): String {
        val client = gh() ?: return "GitHub যুক্ত নেই।"
        val runs = client.runs(null, 6).getOrDefault(emptyList())
        val commits = client.recentCommits(4).getOrDefault(emptyList())
        val rel = client.latestRelease().getOrNull()
        return buildString {
            append("📦 সর্বশেষ রিলিজ: ${rel?.tag ?: "—"} · আপনার অ্যাপ: v${BuildConfig.VERSION_NAME}\n\n")
            append("⚙️ সাম্প্রতিক কাজ:\n")
            runs.forEach { r ->
                val icon = when {
                    r.status != "completed" -> "⏳"
                    r.conclusion == "success" -> "✅"
                    else -> "❌"
                }
                val label = if (r.title.isBlank() || r.title == r.name) r.name else "${r.name} — ${r.title.take(50)}"
                append("$icon $label\n")
            }
            append("\n📝 সাম্প্রতিক কমিট:\n")
            commits.forEach { append("• $it\n") }
        }.trim()
    }

    fun phaseLabel(phase: String): String = when (phase) {
        "dispatched" -> "শুরু হচ্ছে"
        "running" -> "কোড লেখা ও টেস্ট চলছে"
        "releasing" -> "নতুন ভার্সন রিলিজ হচ্ছে"
        "installing" -> "ইনস্টল হচ্ছে"
        else -> phase
    }

    // ------------------------------------------------------------- storage

    private fun post(m: AgentMessage) {
        _messages.value = (_messages.value + m).takeLast(80)
        store?.let { saveMessages(it, _messages.value) }
    }

    private fun friendlyError(e: Exception): String {
        val m = e.message.orEmpty()
        return when {
            m.contains("429") || m.contains("quota", true) ->
                "😓 Saira-র ফ্রি AI কোটা এই মুহূর্তে শেষ/ব্যস্ত। ১–২ মিনিট পরে আবার বলুন।"
            m.contains("503") || m.contains("overloaded", true) || m.contains("UNAVAILABLE") ->
                "Google-এর AI সার্ভার এখন ব্যস্ত। একটু পরে আবার চেষ্টা করুন।"
            e is java.net.UnknownHostException || e is java.net.SocketTimeoutException ->
                "ইন্টারনেট সংযোগে সমস্যা — নেট চেক করে আবার চেষ্টা করুন।"
            else -> "দুঃখিত, এখন কাজটা করতে পারলাম না: ${m.take(160).ifEmpty { e.javaClass.simpleName }}"
        }
    }

    private fun sairaMsg(text: String, confirm: AgentAction? = null) =
        AgentMessage(newId(), true, text, confirm = confirm)

    private fun newId() = "a${System.nanoTime()}"

    private fun setJob(j: AgentJob?) {
        _job.value = j
        val s = store ?: return
        if (j == null) s.putPlain("job", null)
        else s.putPlain(
            "job",
            JSONObject()
                .put("requestId", j.requestId).put("instruction", j.instruction).put("runId", j.runId)
                .put("phase", j.phase).put("startedAtMs", j.startedAtMs).put("baseBuild", j.baseBuild)
                .toString()
        )
    }

    private fun loadJob(s: SecureStore): AgentJob? = runCatching {
        val o = JSONObject(s.getPlain("job") ?: return null)
        AgentJob(
            o.getString("requestId"), o.getString("instruction"), o.optLong("runId"),
            o.getString("phase"), o.getLong("startedAtMs"), o.optInt("baseBuild")
        )
    }.getOrNull()

    private fun saveMessages(s: SecureStore, list: List<AgentMessage>) {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(
                JSONObject().put("id", m.id).put("s", m.fromSaira).put("t", m.text)
                    .put("n", m.attachmentNote).put("r", m.resolved || m.confirm != null)
            )
        }
        // Stored encrypted: the admin's commands can be sensitive.
        s.put("messages", arr.toString())
    }

    private fun loadMessages(s: SecureStore): List<AgentMessage> = runCatching {
        val arr = JSONArray(s.get("messages") ?: return emptyList())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            AgentMessage(o.getString("id"), o.getBoolean("s"), o.getString("t"), o.optString("n"), resolved = true)
        }
    }.getOrDefault(emptyList())

    private val PLANNER_SYSTEM = """
        You are the command router of Saira, the admin-only agent inside the Chatbox Android app.
        Pick ONE action for the admin's latest message and return ONLY JSON:
        {"action": "...", "instruction": "<clear English restatement of what to do>", "count": <int, gallery only>, "image_role": "asset" | "reference"}
        image_role (app_change with attached images only): "asset" = the admin wants that picture itself put INTO the app
        (new logo, icon, background, banner image). "reference" = a screenshot/photo that only SHOWS what to change
        (a marked/circled area, an example design, a bug). When unsure, use "reference".
        Actions:
        - "app_change": the admin wants the app's code/UI/features changed, upgraded, fixed or a new feature added (e.g. "add a dark mode button", "change the login screen colour", "fix X", "notun feature add koro").
        - "app_update": install the newest app version now / check for update.
        - "github_status": status of builds, releases, commits, GitHub activity.
        - "live_config": change LIVE app settings that need no new version: theme (default/lavender), menu position, welcome banner, app title, home announcement, custom info menus, update notice.
        - "firebase_report": Firebase / database statistics, number of users, rules status.
        - "rules_deploy": deploy Firestore security rules.
        - "gallery": look at the admin's recent phone photos (count = how many, default 6).
        - "answer": anything else — questions, explanations, reading attached files, advice, writing, research.
        Prefer "live_config" over "app_change" when the request is only one of those live settings.
    """.trimIndent()

    private val ANSWER_SYSTEM = """
        You are Saira, the Chatbox app's personal AI agent for its admin/owner. You are highly capable:
        expert in software, Android, Firebase, GitHub, writing, research, maths and everyday life.
        Reply in the admin's language (Bengali, Banglish or English). Be accurate, direct and complete;
        use short lists when helpful. When files or photos are attached, read them carefully and answer
        about their real content. Use live web search results when given. Never claim to have done an
        action you did not do. The admin is the owner — never hide anything from him.
    """.trimIndent()
}

/** Human-readable task line from a stored job instruction (drops the "Admin's exact words" wrapper). */
internal fun taskSummary(instruction: String): String {
    val understood = instruction.substringAfter("Saira's understanding:", "").trim()
    val text = understood.ifBlank { instruction.removePrefix("Admin's exact words:").trim() }
    val oneLine = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
    return if (oneLine.length > 220) oneLine.take(217).trimEnd() + "…" else oneLine
}

