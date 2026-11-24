package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val TOKEN_SOURCE_PREFS = "chatbox_appcheck_meta"
private const val KEY_HAS_TRUSTED_DEBUG_TOKEN_PREFIX = "has_trusted_debug_token_"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

private val appCheckConfigured = AtomicBoolean(false)

sealed interface AppCheckRefreshResult {
    data class Success(val token: String) : AppCheckRefreshResult
    data class Failure(val message: String) : AppCheckRefreshResult
}

@SuppressLint("ApplySharedPref")
fun configureAppCheck(context: Context, intent: Intent?) {
    val appContext = context.applicationContext ?: context
    val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()
    if (appCheckConfigured.get() && debugToken.isNullOrEmpty()) {
        return
    }
    try {
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }

        val appCheck = FirebaseAppCheck.getInstance()

        if (BuildConfig.DEBUG) {
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val debugPrefs = appContext.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )
            val metaPrefs = appContext.getSharedPreferences(
                TOKEN_SOURCE_PREFS,
                Context.MODE_PRIVATE
            )
            val trustedKey = "$KEY_HAS_TRUSTED_DEBUG_TOKEN_PREFIX$persistenceKey"

            if (!debugToken.isNullOrEmpty()) {
                // Synchronous commit() ensures debug token is written before DebugAppCheckProviderFactory initializes
                debugPrefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                metaPrefs.edit()
                    .putBoolean(trustedKey, true)
                    .commit()
                if (installDebugAppCheckProvider(appCheck)) {
                    appCheckConfigured.set(true)
                    Log.d(TAG, "App Check debug secret configured from launch intent.")
                }
            } else if (
                metaPrefs.getBoolean(trustedKey, false) &&
                !debugPrefs.getString(DEBUG_SECRET_KEY, null).isNullOrBlank()
            ) {
                if (installDebugAppCheckProvider(appCheck)) {
                    appCheckConfigured.set(true)
                    Log.d(TAG, "App Check debug secret reused from trusted local store.")
                }
            } else {
                // Sideloaded debug APK on a physical device has no harness debug token:
                // install reCAPTCHA Enterprise with auto-refresh enabled after factory installation.
                if (!installRecaptchaProvider(appContext, appCheck)) {
                    installDebugAppCheckProvider(appCheck)
                    Log.w(TAG, "Fallback to DebugAppCheckProviderFactory without trusted token.")
                }
                appCheckConfigured.set(true)
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
        val metaPrefs = appContext.getSharedPreferences(
            TOKEN_SOURCE_PREFS,
            Context.MODE_PRIVATE
        )
        debugPrefs.edit()
            .putString(DEBUG_SECRET_KEY, cleaned)
            .commit()
        metaPrefs.edit()
            .putBoolean("$KEY_HAS_TRUSTED_DEBUG_TOKEN_PREFIX$persistenceKey", true)
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

suspend fun refreshAppCheckToken(
    context: Context,
    timeoutMs: Long = 10_000L
): AppCheckRefreshResult {
    configureAppCheck(context, null)
    return try {
        val result = withTimeoutOrNull(timeoutMs) {
            FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
        }
        val token = result?.token?.takeIf { it.isNotBlank() }
        if (token != null) {
            AppCheckRefreshResult.Success(token)
        } else {
            AppCheckRefreshResult.Failure(
                "App Check token refresh timed out. Sideloaded debug APKs need either a registered Debug Token or a Backup AI key."
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "App Check token refresh failed: ${e.message}", e)
        AppCheckRefreshResult.Failure(
            e.localizedMessage ?: "App Check token refresh failed."
        )
    }
}

suspend fun forceRefreshAppCheckToken(timeoutMs: Long = 8_000L): String? =
    withTimeoutOrNull(timeoutMs) {
        try {
            val result = FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
            result.token.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "App Check token refresh failed: ${e.message}", e)
            null
        }
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
