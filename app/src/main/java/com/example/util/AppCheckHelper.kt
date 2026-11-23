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
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                appCheckConfigured.set(true)
                Log.d(TAG, "App Check debug secret configured from launch intent.")
            } else if (
                metaPrefs.getBoolean(KEY_HAS_TRUSTED_DEBUG_TOKEN, false) &&
                !debugPrefs.getString(DEBUG_SECRET_KEY, null).isNullOrEmpty()
            ) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                appCheckConfigured.set(true)
                Log.d(TAG, "App Check debug secret reused from trusted local store.")
            } else {
                // Sideloaded debug APK on a physical device has no harness debug token:
                // use reCAPTCHA Enterprise so Firebase AI Logic attestation succeeds out of the box.
                if (!installRecaptchaProvider(context)) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                    Log.w(TAG, "Fallback to DebugAppCheckProviderFactory without trusted token.")
                }
                appCheckConfigured.set(true)
            }
        } else {
            if (installRecaptchaProvider(context)) {
                appCheckConfigured.set(true)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

private fun installRecaptchaProvider(context: Context): Boolean {
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
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
    )
    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
    return true
}
