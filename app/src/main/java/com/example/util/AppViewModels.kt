package com.example.util

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.example.ui.viewmodel.ChatboxAppViewModel

/**
 * Process-wide home for the main ViewModel, so live chat sync and Saira's
 * auto-replies keep running after the user leaves / closes the Chatbox screen
 * (as long as the process lives — the full edition's keep-alive service holds it).
 */
object AppViewModels : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()

    fun chatbox(app: Application): ChatboxAppViewModel =
        ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[ChatboxAppViewModel::class.java]
}
