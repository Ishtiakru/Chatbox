package com.example.util

/**
 * Is the owner "on line" (Chatbox open on screen)? Saira only answers for the
 * owner while they are NOT using the app (app closed, in background or screen off).
 */
object AppPresence {
    @Volatile
    var foreground: Boolean = false
        private set

    fun onAppVisible() {
        foreground = true
    }

    fun onAppHidden() {
        foreground = false
    }
}
