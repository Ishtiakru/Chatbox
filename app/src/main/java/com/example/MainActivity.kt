package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

        setContent {
            ChatboxRoot()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
    }

    override fun onResume() {
        super.onResume()
        // Full edition: (re)start the auto-reply keep-alive while we're in the foreground
        // (always allowed), so it survives recents-clear + screen off afterwards.
        com.example.saira.SairaKeepAliveService.sync(this, fromForeground = true)
        // Keep a recent location fix cached for screen-off auto-replies.
        lifecycleScope.launch { com.example.saira.SairaLocationHelper.refreshCache(applicationContext) }
    }
}
