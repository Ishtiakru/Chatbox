package com.example.agent

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.BuildConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * In-app updater: downloads the newest signed release APK from GitHub and
 * installs it through Android's PackageInstaller — no browser, no link.
 *
 * On Android 12+ a self-update can finish with no prompt at all
 * (USER_ACTION_NOT_REQUIRED); some phones still show one "Update" tap.
 * The first time, Android asks once to allow "Install unknown apps" for Chatbox.
 */
object AppSelfUpdater {
    private const val TAG = "AppSelfUpdater"

    /** idle | checking | downloading:<pct> | installing | done | uptodate | error:<msg> | needs_permission */
    private val _state = MutableStateFlow("idle")
    val state: StateFlow<String> = _state.asStateFlow()

    /** Newest GitHub release seen by [refreshLatest] — drives the green update button for every user. */
    private val _latest = MutableStateFlow<GitHubRelease?>(null)
    val latest: StateFlow<GitHubRelease?> = _latest.asStateFlow()
    @Volatile private var lastCheckAt = 0L

    /**
     * Asks GitHub (public, no token) for the newest release. Cached for 10 minutes
     * unless [force]. Never throws; returns the last known release on failure.
     */
    suspend fun refreshLatest(force: Boolean = false): GitHubRelease? {
        val now = System.currentTimeMillis()
        if (!force && _latest.value != null && now - lastCheckAt < 10 * 60_000L) return _latest.value
        GitHubClient(null).latestRelease()
            .onSuccess { rel ->
                lastCheckAt = now
                if (buildNumberOf(rel.tag) > 0) _latest.value = rel
            }
            .onFailure { Log.w(TAG, "latest release check failed: ${it.message}") }
        return _latest.value
    }

    /** True when GitHub has a build newer than the one installed. */
    fun hasNewerRelease(release: GitHubRelease? = _latest.value): Boolean =
        release != null && buildNumberOf(release.tag) > installedBuildNumber()

    fun installedBuildNumber(): Int =
        BuildConfig.VERSION_NAME.substringAfterLast('.').toIntOrNull() ?: 0

    fun buildNumberOf(tag: String): Int = tag.substringAfterLast('.').toIntOrNull() ?: 0

    /** Full edition = the build that declares Saira's keep-alive service. */
    fun isFullEdition(context: Context): Boolean = runCatching {
        context.packageManager.getServiceInfo(
            ComponentName(context, "com.example.saira.SairaKeepAliveService"), 0
        )
        true
    }.getOrDefault(false)

    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Picks the right APK for this edition from a release's assets. */
    fun pickAsset(context: Context, release: GitHubRelease): String? {
        val full = isFullEdition(context)
        return if (full) {
            release.assets["chatbox-full.apk"]
                ?: release.assets.entries.firstOrNull { it.key.endsWith("-full.apk") }?.value
        } else {
            release.assets.entries.firstOrNull {
                it.key.startsWith("chatbox-v") && it.key.endsWith(".apk") && !it.key.endsWith("-full.apk")
            }?.value
        }
    }

    /**
     * Checks GitHub for a newer release and installs it.
     * @return human-readable result (Bengali).
     */
    suspend fun updateToLatest(context: Context, force: Boolean = false): String {
        _state.value = "checking"
        val release = GitHubClient(null).latestRelease().getOrElse {
            _state.value = "error:${it.message}"
            return "নতুন ভার্সন খুঁজতে পারিনি — ইন্টারনেট চেক করে আবার চাপুন।"
        }
        if (buildNumberOf(release.tag) > 0) {
            _latest.value = release
            lastCheckAt = System.currentTimeMillis()
        }
        val latest = buildNumberOf(release.tag)
        val mine = installedBuildNumber()
        if (!force && latest <= mine) {
            _state.value = "uptodate"
            return "আপনি সর্বশেষ ভার্সনেই আছেন (v${BuildConfig.VERSION_NAME})।"
        }
        val url = pickAsset(context, release) ?: run {
            _state.value = "error:asset"
            return "রিলিজ ${release.tag}-এ APK ফাইল পাইনি।"
        }
        if (!canInstall(context)) {
            _state.value = "needs_permission"
            openInstallPermissionSettings(context)
            return "একবার অনুমতি দরকার: খোলা পেজে Chatbox-এর জন্য \"Allow from this source\" চালু করুন, তারপর আবার আপডেট চাপুন।"
        }
        val apk = runCatching { download(context, url) }.getOrElse {
            _state.value = "error:${it.message}"
            return "ডাউনলোড ব্যর্থ: ${it.message}"
        }
        _state.value = "installing"
        return runCatching {
            install(context, apk)
            "${release.tag} ইনস্টল হচ্ছে… অ্যাপ নিজে থেকে বন্ধ হয়ে নতুন ভার্সন বসবে, তারপর আবার খুলুন।"
        }.getOrElse {
            _state.value = "error:${it.message}"
            "ইনস্টল শুরু করা যায়নি: ${it.message}"
        }
    }

    private suspend fun download(context: Context, url: String): File = withContext(Dispatchers.IO) {
        val out = File(context.cacheDir, "update.apk")
        var current = url
        var conn: HttpURLConnection? = null
        var hops = 0
        while (conn == null) {
            if (hops++ > 5) throw IOException("too many redirects")
            // Updates only over HTTPS — a redirect may never downgrade to plain http.
            if (!current.startsWith("https://")) throw IOException("insecure update link")
            val c = URL(current).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 20_000
            c.readTimeout = 60_000
            c.setRequestProperty("User-Agent", "Chatbox-Updater")
            val code = c.responseCode
            if (code in 300..399) {
                current = c.getHeaderField("Location")?.let { URL(URL(current), it).toString() }
                    ?: throw IOException("bad redirect")
                c.disconnect()
            } else {
                if (code !in 200..299) {
                    c.disconnect()
                    throw IOException("HTTP $code")
                }
                conn = c
            }
        }
        val c: HttpURLConnection = conn ?: throw IOException("no connection")
        try {
            val total = c.contentLengthLong
            c.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                _state.value = "downloading:$pct"
                            }
                        }
                    }
                    if (total > 0 && done != total) throw IOException("ফাইল অসম্পূর্ণ — আবার চেষ্টা করুন")
                }
            }
        } finally {
            c.disconnect()
        }
        if (out.length() < 1_000_000) throw IOException("ফাইল অসম্পূর্ণ")
        verifyApk(context, out)
        out
    }

    /**
     * The downloaded file must be Chatbox itself, signed with the same key as the installed app.
     * Android also refuses a differently signed update, but checking first gives a clear message
     * and never hands a foreign APK to the installer.
     */
    private fun verifyApk(context: Context, apk: File) {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES
        val info = pm.getPackageArchiveInfo(apk.path, flags)
        if (info == null) {
            apk.delete()
            throw IOException("ডাউনলোড করা ফাইলটা ঠিক APK নয়")
        }
        if (info.packageName != context.packageName) {
            apk.delete()
            throw IOException("এটা Chatbox-এর APK নয়")
        }
        val theirs = signerSet(info)
        val mine = runCatching { signerSet(pm.getPackageInfo(context.packageName, flags)) }.getOrDefault(emptySet())
        // Some Android versions can't read an archive's signer — then the system installer decides.
        if (theirs.isNotEmpty() && mine.isNotEmpty() && theirs.intersect(mine).isEmpty()) {
            apk.delete()
            throw IOException("APK-র সাইন মেলেনি — নিরাপত্তার জন্য ইনস্টল করিনি")
        }
    }

    @Suppress("DEPRECATION")
    private fun signerSet(p: PackageInfo): Set<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val si = p.signingInfo ?: return emptySet()
            val certs = if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
            certs?.map { it.toCharsString() }?.toSet().orEmpty()
        } else p.signatures?.map { it.toCharsString() }?.toSet().orEmpty()

    private suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("chatbox.apk", 0, apk.length()).use { os ->
                apk.inputStream().use { it.copyTo(os, 64 * 1024) }
                session.fsync(os)
            }
            val intent = Intent(context, SelfUpdateReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else PendingIntent.FLAG_UPDATE_CURRENT
            val pi = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pi.intentSender)
        }
    }

    internal fun onStatus(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> _state.value = "done"
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                Log.w(TAG, "install failed status=$status $msg")
                _state.value = "error:$msg"
            }
        }
    }
}

class SelfUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppSelfUpdater.onStatus(context, intent)
    }
}

/** After an update finishes, tell the user the new version is ready. */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        runCatching {
            com.example.util.ChatboxNotifier.notifyMessage(
                context,
                "app_updated",
                "Chatbox আপডেট হয়েছে",
                "নতুন ভার্সন v${BuildConfig.VERSION_NAME} তৈরি — খুলতে ট্যাপ করুন"
            )
        }
    }
}
