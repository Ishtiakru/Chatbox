package com.example.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Is the owner "on line" (Chatbox open on screen)? Saira only answers for the
 * owner while they are NOT using the app (app closed, in background or screen off).
 */
object AppPresence {
    private val _foregroundFlow = MutableStateFlow(false)
    val foregroundFlow: StateFlow<Boolean> = _foregroundFlow

    val foreground: Boolean get() = _foregroundFlow.value

    fun onAppVisible() {
        _foregroundFlow.value = true
    }

    fun onAppHidden() {
        _foregroundFlow.value = false
    }
}
