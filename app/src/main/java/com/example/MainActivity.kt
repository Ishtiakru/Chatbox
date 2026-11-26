package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.ui.ChatboxRoot
import com.example.util.configureAppCheck

class MainActivity : ComponentActivity() {

    private companion object {
        const val NOTIF_PERMISSION_CODE = 4401
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before any UI, ViewModel, or Firebase call
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        // Android 13+: message/call notifications need the runtime POST_NOTIFICATIONS grant.
        // Platform requestPermissions() instead of registerForActivityResult(): the latter
        // trips lint's InvalidFragmentVersionForActivityResult (fatal for lintVitalRelease)
        // because this app has no androidx.fragment >= 1.3.0 dependency.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            @Suppress("DEPRECATION")
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIF_PERMISSION_CODE)
        }

        val appViewModel = com.example.util.AppViewModels.chatbox(application)
        setContent {
            val loginLanguage by appViewModel.loginLanguage.collectAsState(initial = "en-US")
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides androidx.compose.ui.platform.LocalConfiguration.current.also {
                    it.setLocales(android.os.LocaleList.forLanguageTags(loginLanguage))
                }
            ) {
                ChatboxRoot(appViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
    }

    override fun onResume() {
        super.onResume()
        com.example.util.AppPresence.onAppVisible()
        // Full edition: (re)start the auto-reply keep-alive while we're in the foreground
        // (always allowed), so it survives recents-clear + screen off afterwards.
        com.example.saira.SairaKeepAliveService.sync(this, fromForeground = true)
        // Keep a recent location fix cached for screen-off auto-replies.
        lifecycleScope.launch { com.example.saira.SairaLocationHelper.refreshCache(applicationContext) }
    }

    override fun onPause() {
        com.example.util.AppPresence.onAppHidden()
        super.onPause()
    }
}
