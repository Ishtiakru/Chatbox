package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

interface ConnectivityStatusProvider {
    val isOnline: StateFlow<Boolean>
    fun isCurrentlyOnline(): Boolean
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

    private val _isOnline = MutableStateFlow(checkCurrentConnectivity())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = checkCurrentConnectivity()
        }

        override fun onLost(network: Network) {
            _isOnline.value = checkCurrentConnectivity()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            _isOnline.value = checkCurrentConnectivity()
        }

        override fun onUnavailable() {
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

    private fun registerCallback() {
        val cm = connectivityManager ?: return
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, networkCallback)
        }
    }

    private fun checkCurrentConnectivity(): Boolean {
        val cm = connectivityManager ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        fun observe(context: Context): Flow<Boolean> = callbackFlow {
            val appContext = context.applicationContext
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            fun currentOnline(): Boolean {
                val active = cm?.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(active) ?: return false
                return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
            trySend(currentOnline())
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trySend(currentOnline())
                }

                override fun onLost(network: Network) {
                    trySend(currentOnline())
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    trySend(currentOnline())
                }

                override fun onUnavailable() {
                    trySend(false)
                }
            }
            val registered = cm?.let {
                runCatching {
                    val request = NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()
                    it.registerNetworkCallback(request, callback)
                    true
                }.getOrDefault(false)
            } ?: false
            awaitClose {
                if (registered) {
                    runCatching { cm?.unregisterNetworkCallback(callback) }
                }
            }
        }.distinctUntilChanged()
    }
}
