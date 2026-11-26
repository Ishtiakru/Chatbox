package com.example.ui.screens

import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState

/** Result of the phone's own account chooser. */
sealed interface DeviceGmailResult {
    data class Chosen(val gmail: String) : DeviceGmailResult
    data object Cancelled : DeviceGmailResult
    data object Unavailable : DeviceGmailResult
}

/**
 * Android's built-in "Choose an account" screen listing every Google (Gmail) account
 * on the phone, with "Add account" too. It needs no Google Cloud / OAuth setup, so it
 * works even when Google sign-in reports [28444] "Developer console is not set up".
 * Returns a function that opens the chooser.
 */
@Composable
fun rememberDeviceGmailChooser(onResult: (DeviceGmailResult) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val name = res.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.trim()?.lowercase()
        callback.value(
            if (res.resultCode == Activity.RESULT_OK && !name.isNullOrBlank()) DeviceGmailResult.Chosen(name)
            else DeviceGmailResult.Cancelled
        )
    }
    return {
        try {
            launcher.launch(
                AccountManager.newChooseAccountIntent(
                    null, null, arrayOf("com.google"), null, null, null, null
                )
            )
        } catch (e: Exception) {
            callback.value(DeviceGmailResult.Unavailable)
        }
    }
}
