package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.ui.ChatboxRoot
import com.example.util.configureAppCheck

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before any UI, ViewModel, or Firebase call
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        // Android 13+: message/call notifications need the runtime POST_NOTIFICATIONS grant.
        // Registered INSIDE onCreate — a property-initializer registration trips lint's
        // InvalidFragmentVersionForActivityResult (fatal for lintVitalRelease).
        val notifPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val appViewModel = com.example.util.AppViewModels.chatbox(application)
        setContent {
            ChatboxRoot(appViewModel)
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
