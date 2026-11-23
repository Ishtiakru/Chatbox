package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val TOKEN_SOURCE_PREFS = "chatbox_appcheck_meta"
private const val KEY_HAS_TRUSTED_DEBUG_TOKEN = "has_trusted_debug_token"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

private val appCheckConfigured = AtomicBoolean(false)

@SuppressLint("ApplySharedPref")
fun configureAppCheck(context: Context, intent: Intent?) {
    val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
    if (appCheckConfigured.get() && debugToken.isNullOrEmpty()) {
        return
    }
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val appCheck = FirebaseAppCheck.getInstance()
        appCheck.setTokenAutoRefreshEnabled(true)

        if (BuildConfig.DEBUG) {
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val debugPrefs = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )
            val metaPrefs = context.getSharedPreferences(
                TOKEN_SOURCE_PREFS,
                Context.MODE_PRIVATE
            )

            if (!debugToken.isNullOrEmpty()) {
                // Synchronous commit() ensures debug token is written before DebugAppCheckProviderFactory initializes
                debugPrefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                metaPrefs.edit()
                    .putBoolean(KEY_HAS_TRUSTED_DEBUG_TOKEN, true)
                    .commit()
                appCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                appCheckConfigured.set(true)
                Log.d(TAG, "App Check debug secret configured from launch intent.")
            } else if (
                metaPrefs.getBoolean(KEY_HAS_TRUSTED_DEBUG_TOKEN, false) &&
                !debugPrefs.getString(DEBUG_SECRET_KEY, null).isNullOrEmpty()
            ) {
                appCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                appCheckConfigured.set(true)
                Log.d(TAG, "App Check debug secret reused from trusted local store.")
            } else {
                // Sideloaded debug APK on a physical device has no harness debug token:
                // use reCAPTCHA Enterprise so Firebase AI Logic attestation succeeds out of the box.
                if (!installRecaptchaProvider(context, appCheck)) {
                    appCheck.installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                    Log.w(TAG, "Fallback to DebugAppCheckProviderFactory without trusted token.")
                }
                appCheckConfigured.set(true)
            }
        } else {
            if (installRecaptchaProvider(context, appCheck)) {
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
        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
    )
    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
    return true
}

suspend fun forceRefreshAppCheckToken(timeoutMs: Long = 8_000L): String? =
    withTimeoutOrNull(timeoutMs) {
        runCatching {
            val result = FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
            result.token.takeIf { it.isNotBlank() }
        }.onFailure { err ->
            Log.e(TAG, "App Check token refresh failed: ${err.message}", err)
        }.getOrNull()
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
