package com.example.saira

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One UI step Saira performs inside another app. */
sealed class AutoStep {
    /** Tap a button found by content description, text or view id. */
    data class Click(
        val descs: List<String> = emptyList(),
        val texts: List<String> = emptyList(),
        val ids: List<String> = emptyList(),
        val optional: Boolean = false
    ) : AutoStep()

    /** Type into the focused / first editable field. */
    data class TypeText(val text: String) : AutoStep()

    /** Tap the first search result showing [name]. */
    data class PickResult(val name: String) : AutoStep()
}

data class AutoTask(
    val tag: String,
    val label: String,
    /** Packages the steps run in (empty = any app). */
    val packages: Set<String>,
    val steps: List<AutoStep>,
    val timeoutMs: Long = 25_000
)

data class AutoResult(val tag: String, val ok: Boolean, val message: String, val atMs: Long = System.currentTimeMillis())

/**
 * "Saira Automation": an accessibility service that lets Saira tap Send / Call buttons
 * in other apps and lock the screen. Only runs the short task Saira was just asked to do
 * (after the user's confirmation); it never reads or stores what is on screen otherwise.
 * Declared only in the Full edition manifest.
 */
class SairaAutomationService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        SairaAutomation.service = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (SairaAutomation.hasTask()) SairaAutomation.poke()
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        if (SairaAutomation.service === this) SairaAutomation.service = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (SairaAutomation.service === this) SairaAutomation.service = null
        super.onDestroy()
    }
}

object SairaAutomation {
    @Volatile
    internal var service: SairaAutomationService? = null

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var task: AutoTask? = null
    private var index = 0
    private var startedAt = 0L
    private var stepStartedAt = 0L
    private var lastActionAt = 0L
    private var tickScheduled = false

    private val _results = MutableStateFlow<AutoResult?>(null)
    val results: StateFlow<AutoResult?> = _results

    /** True only in the Full edition (the service is declared there). */
    fun isAvailable(context: Context): Boolean = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getServiceInfo(ComponentName(context, SairaAutomationService::class.java), 0)
        true
    }.getOrDefault(false)

    fun isEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(context, SairaAutomationService::class.java)
        return flat.split(':').any {
            ComponentName.unflattenFromString(it.trim()) == me || it.contains(SairaAutomationService::class.java.name)
        }
    }

    /** Enabled in Settings and connected right now. */
    fun isReady(context: Context): Boolean = service != null && isEnabled(context)

    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 33) {
            val detail = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(context, SairaAutomationService::class.java).flattenToString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(detail) }.isSuccess) return
        }
        runCatching { context.startActivity(intent) }
    }

    fun lockScreen(): Boolean {
        val s = service ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return runCatching { s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) }.getOrDefault(false)
    }

    fun hasTask(): Boolean = task != null

    fun start(t: AutoTask) {
        main.post {
            task = t
            index = 0
            startedAt = System.currentTimeMillis()
            stepStartedAt = startedAt
            lastActionAt = 0L
            schedule(900)
        }
    }

    fun cancel() {
        main.post { task = null }
    }

    internal fun poke() {
        main.post { schedule(250) }
    }

    private fun schedule(delayMs: Long) {
        if (tickScheduled) return
        tickScheduled = true
        main.postDelayed({
            tickScheduled = false
            tick()
        }, delayMs)
    }

    private fun finish(ok: Boolean, message: String) {
        val t = task ?: return
        task = null
        _results.value = AutoResult(t.tag, ok, message)
    }

    private fun tick() {
        val t = task ?: return
        val now = System.currentTimeMillis()
        if (now - startedAt > t.timeoutMs) {
            finish(false, "⚠️ ${t.label}: নিজে বাটনটা খুঁজে পাইনি — একবার নিজে চাপুন।")
            return
        }
        val s = service
        if (s == null) {
            finish(false, "⚠️ Saira Automation বন্ধ — কাজটা শেষ করতে পারিনি।")
            return
        }
        if (now - lastActionAt < 800) { schedule(400); return }
        val root = s.rootInActiveWindow
        val pkg = root?.packageName?.toString()
        if (root == null || (t.packages.isNotEmpty() && pkg !in t.packages)) { schedule(500); return }

        val step = t.steps[index]
        val done = when (step) {
            is AutoStep.Click -> findClickTarget(root, step)?.let { click(s, it) } ?: false
            is AutoStep.TypeText -> findEditable(root)?.let { setText(it, step.text) } ?: false
            is AutoStep.PickResult -> findResult(root, step.name)?.let { click(s, it) } ?: false
        }
        if (done) {
            index++
            lastActionAt = now
            stepStartedAt = now
            if (index >= t.steps.size) {
                finish(true, "✅ ${t.label}: হয়ে গেছে।")
                return
            }
        } else if (step is AutoStep.Click && step.optional && now - stepStartedAt > 3_500) {
            index++
            stepStartedAt = now
            if (index >= t.steps.size) { finish(true, "✅ ${t.label}: হয়ে গেছে।"); return }
        }
        schedule(600)
    }

    // ---------------------------------------------------------------- node search
    private fun allNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && out.size < 1500) {
            val n = queue.removeFirst()
            out.add(n)
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return out
    }

    private fun norm(cs: CharSequence?): String = cs?.toString()?.trim()?.lowercase().orEmpty()

    private fun findClickTarget(root: AccessibilityNodeInfo, step: AutoStep.Click): AccessibilityNodeInfo? {
        for (id in step.ids) {
            root.findAccessibilityNodeInfosByViewId(id)?.firstOrNull { it.isVisibleToUser }?.let { return it }
        }
        val descs = step.descs.map { it.lowercase() }
        val texts = step.texts.map { it.lowercase() }
        val nodes = allNodes(root).filter { it.isVisibleToUser && it.isEnabled }
        // exact matches first, in label priority order
        for (label in descs) nodes.firstOrNull { norm(it.contentDescription) == label }?.let { return it }
        for (label in texts) nodes.firstOrNull { norm(it.text) == label && !it.isEditable }?.let { return it }
        for (label in descs) nodes.firstOrNull { norm(it.contentDescription).startsWith(label) }?.let { return it }
        return null
    }

    private fun findEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }?.let { return it }
        return allNodes(root).firstOrNull { it.isEditable && it.isVisibleToUser }
    }

    private fun findResult(root: AccessibilityNodeInfo, name: String): AccessibilityNodeInfo? {
        val q = name.lowercase()
        return allNodes(root).firstOrNull {
            !it.isEditable && it.isVisibleToUser &&
                (norm(it.text).contains(q) || norm(it.contentDescription).contains(q))
        }
    }

    private fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun click(s: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 6) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
            depth++
        }
        // Fallback: tap the centre of the node.
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.isEmpty) return false
        val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return runCatching { s.dispatchGesture(gesture, null, null) }.getOrDefault(false)
    }
}
