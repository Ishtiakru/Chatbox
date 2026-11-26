package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

// Base64 sentinel returned by FirebaseAppCheck.getToken(false) when attestation fails internally: {"error":"UNKNOWN_ERROR"}
private const val DUMMY_ERROR_TOKEN = "eyJlcnJvciI6IlVOS05PV05fRVJST1IifQ=="

private val appCheckConfigured = AtomicBoolean(false)

sealed interface AppCheckRefreshResult {
    data class Success(val token: String) : AppCheckRefreshResult
    data class Failure(val message: String) : AppCheckRefreshResult
}

data class SigningShaFingerprints(
    val sha1: String,
    val sha256: String
)

@SuppressLint("ApplySharedPref")
fun configureAppCheck(
    context: Context,
    intent: Intent?,
    forceReinstall: Boolean = false
) {
    val appContext = context.applicationContext ?: context
    val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()
    if (!forceReinstall && appCheckConfigured.get() && debugToken.isNullOrEmpty()) {
        return
    }
    try {
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }

        val appCheck = FirebaseAppCheck.getInstance()

        if (BuildConfig.DEBUG) {
            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val debugPrefs = appContext.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                // Synchronous commit() ensures the debug secret is persisted before DebugAppCheckProviderFactory reads it.
                debugPrefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra; using DebugAppCheckProviderFactory.")
            }

            if (installDebugAppCheckProvider(appCheck)) {
                appCheckConfigured.set(true)
                Log.d(TAG, "DebugAppCheckProviderFactory installed.")
            }
        } else {
            if (installRecaptchaProvider(appContext, appCheck)) {
                appCheckConfigured.set(true)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

private fun installRecaptchaProvider(context: Context, appCheck: FirebaseAppCheck): Boolean {
    val resId = context.resources.getIdentifier(
        RECAPTCHA_SITE_KEY_RES,
        "string",
        context.packageName
    )
    if (resId == 0) {
        Log.e(TAG, "recaptcha_site_key string resource not found in package.")
        return false
    }
    val siteKey = context.getString(resId)
    if (siteKey.isBlank()) {
        Log.e(TAG, "recaptcha_site_key resource string is blank.")
        return false
    }
    appCheck.installAppCheckProviderFactory(
        RecaptchaAppCheckProviderFactory.getInstance(siteKey),
        true
    )
    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
    return true
}

@SuppressLint("ApplySharedPref")
fun getOrCreateLocalDebugToken(context: Context): String {
    val appContext = context.applicationContext ?: context
    if (FirebaseApp.getApps(appContext).isEmpty()) {
        FirebaseApp.initializeApp(appContext)
    }
    val persistenceKey = FirebaseApp.getInstance().persistenceKey
    val debugPrefs = appContext.getSharedPreferences(
        "$DEBUG_STORE_PREFIX$persistenceKey",
        Context.MODE_PRIVATE
    )
    val existing = debugPrefs.getString(DEBUG_SECRET_KEY, null)?.trim()
    if (!existing.isNullOrEmpty()) return existing

    val generated = UUID.randomUUID().toString()
    debugPrefs.edit().putString(DEBUG_SECRET_KEY, generated).commit()
    return generated
}

@SuppressLint("ApplySharedPref")
fun applyManualDebugToken(context: Context, rawToken: String): Boolean {
    if (!BuildConfig.DEBUG) return false
    val cleaned = rawToken.trim()
    if (cleaned.isEmpty()) return false
    return try {
        val appContext = context.applicationContext ?: context
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }
        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val debugPrefs = appContext.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )
        debugPrefs.edit()
            .putString(DEBUG_SECRET_KEY, cleaned)
            .commit()

        val appCheck = FirebaseAppCheck.getInstance()
        val installed = installDebugAppCheckProvider(appCheck)
        if (installed) {
            appCheckConfigured.set(true)
        }
        installed
    } catch (e: Exception) {
        Log.e(TAG, "Failed to apply manual App Check debug token", e)
        false
    }
}

fun getAppSigningShaFingerprints(context: Context): SigningShaFingerprints? {
    return try {
        val pm = context.packageManager
        val pkgName = context.packageName
        val certBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo
            val sigs = if (signingInfo?.hasMultipleSigners() == true) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo?.signingCertificateHistory
            }
            sigs?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()?.toByteArray()
        } ?: return null

        SigningShaFingerprints(
            sha1 = digestHex(certBytes, "SHA-1"),
            sha256 = digestHex(certBytes, "SHA-256")
        )
    } catch (e: Exception) {
        Log.w(TAG, "Unable to read signing certificate fingerprints", e)
        null
    }
}

private fun digestHex(bytes: ByteArray, algorithm: String): String =
    MessageDigest.getInstance(algorithm)
        .digest(bytes)
        .joinToString(":") { b -> "%02X".format(b) }

suspend fun refreshAppCheckToken(
    context: Context,
    timeoutMs: Long = 10_000L
): AppCheckRefreshResult {
    val appContext = context.applicationContext ?: context
    configureAppCheck(appContext, null, forceReinstall = true)
    return try {
        val result = withTimeoutOrNull(timeoutMs) {
            FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
        }
        val token = result?.token?.takeIf { it.isNotBlank() && it != DUMMY_ERROR_TOKEN }
        if (token != null) {
            AppCheckRefreshResult.Success(token)
        } else {
            AppCheckRefreshResult.Failure(
                buildAppCheckDiagnosticMessage(
                    context = appContext,
                    rawReason = "App Check token refresh timed out or returned an invalid attestation token."
                )
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "App Check token refresh failed: ${e.message}", e)
        AppCheckRefreshResult.Failure(
            buildAppCheckDiagnosticMessage(
                context = appContext,
                rawReason = e.localizedMessage ?: "App Check token refresh failed."
            )
        )
    }
}

suspend fun forceRefreshAppCheckToken(
    context: Context? = null,
    timeoutMs: Long = 10_000L
): AppCheckRefreshResult {
    val resolvedContext = context ?: runCatching {
        FirebaseApp.getInstance().applicationContext
    }.getOrNull()
    if (resolvedContext != null) {
        return refreshAppCheckToken(resolvedContext, timeoutMs)
    }
    return try {
        val result = withTimeoutOrNull(timeoutMs) {
            FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
        }
        val token = result?.token?.takeIf { it.isNotBlank() && it != DUMMY_ERROR_TOKEN }
        if (token != null) {
            AppCheckRefreshResult.Success(token)
        } else {
            AppCheckRefreshResult.Failure("App Check token refresh timed out.")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "App Check token refresh failed: ${e.message}", e)
        AppCheckRefreshResult.Failure(e.localizedMessage ?: "App Check token refresh failed.")
    }
}

fun buildAppCheckDiagnosticMessage(context: Context?, rawReason: String): String {
    val clean = rawReason.trim().ifEmpty { "Firebase App Check token is invalid." }
    if (BuildConfig.DEBUG) {
        return "$clean — Debug build হলে 'Debug Token' বাটন থেকে টোকেন কপি করে Firebase Console → App Check-এ যোগ করুন, অথবা Backup AI সেট করুন।"
    }
    val sha1 = context?.let { getAppSigningShaFingerprints(it)?.sha1 }
    val shaHint = if (!sha1.isNullOrBlank()) {
        " এই Release APK-র SHA-1 ($sha1) Firebase Console → Project Settings ও App Check-এ যোগ না থাকলে বা reCAPTCHA এনভায়রনমেন্ট ব্লক করলে টোকেন ব্যর্থ হয়।"
    } else {
        " সাইডলোড করা Release APK-র SHA-1/SHA-256 Firebase Console-এ রেজিস্টার না থাকলে App Check টোকেন ব্যর্থ হতে পারে।"
    }
    return "$clean.$shaHint সাময়িকভাবে চলতে 'Backup AI সেট করুন' বাটন থেকে ফ্রি Groq/OpenRouter Key যোগ করতে পারেন।"
}

fun isAppCheckError(error: Throwable?): Boolean {
    var current = error
    var depth = 0
    while (current != null && depth < 6) {
        val msg = "${current.javaClass.simpleName} ${current.message.orEmpty()}".lowercase()
        if (
            msg.contains("app check") ||
            msg.contains("appcheck") ||
            msg.contains("attestation")
        ) {
            return true
        }
        current = current.cause
        depth++
    }
    return false
}

/**
 * Fetches (and caches) an App Check token in the background while the login
 * screen is visible, so the first Firebase sign-in request doesn't wait for it.
 */
fun prewarmAppCheck() {
    runCatching {
        if (appCheckConfigured.get()) {
            FirebaseAppCheck.getInstance().getAppCheckToken(false)
                .addOnFailureListener { Log.w(TAG, "App Check prewarm failed: ${it.message}") }
        }
    }
}
