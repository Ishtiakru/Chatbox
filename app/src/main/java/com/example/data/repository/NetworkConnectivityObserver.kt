package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ConnectivityStatusProvider {
    val isOnline: StateFlow<Boolean>
    fun isCurrentlyOnline(): Boolean
    fun reportServerReachable() {}
}

object AlwaysOnlineConnectivityProvider : ConnectivityStatusProvider {
    private val _isOnline = MutableStateFlow(true)
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    override fun isCurrentlyOnline(): Boolean = true
}

class NetworkConnectivityObserver(context: Context) : ConnectivityStatusProvider {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    @Volatile
    private var serverConfirmedOnCurrentNetwork: Boolean = false

    private val _isOnline = MutableStateFlow(checkCurrentConnectivity())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            serverConfirmedOnCurrentNetwork = false
            _isOnline.value = checkCurrentConnectivity()
        }

        override fun onLost(network: Network) {
            serverConfirmedOnCurrentNetwork = false
            _isOnline.value = checkCurrentConnectivity()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            _isOnline.value = checkCapabilities(networkCapabilities)
        }

        override fun onUnavailable() {
            serverConfirmedOnCurrentNetwork = false
            _isOnline.value = false
        }
    }

    init {
        registerCallback()
    }

    override fun isCurrentlyOnline(): Boolean {
        val current = checkCurrentConnectivity()
        _isOnline.value = current
        return current
    }

    override fun reportServerReachable() {
        if (hasInternetTransport()) {
            serverConfirmedOnCurrentNetwork = true
            _isOnline.value = true
        }
    }

    private fun registerCallback() {
        val cm = connectivityManager ?: return
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, networkCallback)
        }
    }

    private fun hasInternetTransport(): Boolean {
        val cm = connectivityManager ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkCurrentConnectivity(): Boolean {
        val cm = connectivityManager ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return checkCapabilities(capabilities)
    }

    private fun checkCapabilities(capabilities: NetworkCapabilities): Boolean {
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!hasInternet) {
            serverConfirmedOnCurrentNetwork = false
            return false
        }
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return isValidated || serverConfirmedOnCurrentNetwork
    }
}
