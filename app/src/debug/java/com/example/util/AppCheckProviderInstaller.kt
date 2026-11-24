package com.example.util

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal fun installDebugAppCheckProvider(appCheck: FirebaseAppCheck): Boolean {
    appCheck.installAppCheckProviderFactory(
        DebugAppCheckProviderFactory.getInstance()
    )
    return true
}
