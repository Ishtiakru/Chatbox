package com.example.saira

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class SharedLocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val provider: String,
    val timeMs: Long = 0L
) {
    fun toShareText(): String {
        val lat = String.format(Locale.US, "%.5f", latitude)
        val lon = String.format(Locale.US, "%.5f", longitude)
        return "আমার লাইভ লোকেশন (প্রায় ${accuracyMeters.toInt()}m): https://maps.google.com/?q=$lat,$lon"
    }
}

object SairaLocationHelper {

    private const val CACHE_PREFS = "saira_location_cache"
    private const val FRESH_ENOUGH_MS = 2 * 60_000L

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    /** Last known fix from the OS (instant, may be stale or null in background). */
    @SuppressLint("MissingPermission")
    fun getBestAvailableLocation(context: Context): SharedLocationSnapshot? {
        if (!hasLocationPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        var best: Location? = null
        for (provider in providerOrder()) {
            val loc = runCatching {
                if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
            }.getOrNull() ?: continue
            if (best == null || loc.time > best.time) best = loc
        }
        val chosen = best ?: return null
        return chosen.toSnapshot()
    }

    /**
     * Live location for auto-replies: asks the OS for a fresh fix (bounded by
     * [timeoutMs]); falls back to the newest of last-known / cached fix. Every
     * success is cached so a later background request still has a location.
     */
    suspend fun getFreshLocation(context: Context, timeoutMs: Long = 6_000L): SharedLocationSnapshot? {
        val app = context.applicationContext
        if (!hasLocationPermission(app)) return null
        val lastKnown = getBestAvailableLocation(app)
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.timeMs < FRESH_ENOUGH_MS) {
            saveCache(app, lastKnown)
            return lastKnown
        }
        val fresh = runCatching { withTimeoutOrNull(timeoutMs) { requestCurrent(app) } }.getOrNull()
        if (fresh != null) {
            saveCache(app, fresh)
            return fresh
        }
        val newest = listOfNotNull(lastKnown, cached(app)).maxByOrNull { it.timeMs }
        newest?.let { saveCache(app, it) }
        return newest
    }

    /** Call while the app is in the foreground so background replies have a recent fix. */
    suspend fun refreshCache(context: Context) {
        if (!hasLocationPermission(context)) return
        runCatching { getFreshLocation(context, timeoutMs = 8_000L) }
    }

    private fun providerOrder(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.PASSIVE_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrent(context: Context): SharedLocationSnapshot? = withContext(Dispatchers.Main) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null
        val provider = providerOrder()
            .filter { it != LocationManager.PASSIVE_PROVIDER }
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return@withContext null
        suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                cont.invokeOnCancellation { runCatching { signal.cancel() } }
                val started = runCatching {
                    manager.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { loc ->
                        if (cont.isActive) cont.resume(loc?.toSnapshot())
                    }
                }.isSuccess
                if (!started && cont.isActive) cont.resume(null)
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (cont.isActive) cont.resume(location.toSnapshot())
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
                cont.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
                val started = runCatching {
                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }.isSuccess
                if (!started && cont.isActive) cont.resume(null)
            }
        }
    }

    private fun Location.toSnapshot() = SharedLocationSnapshot(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        provider = provider ?: "network",
        timeMs = time.takeIf { it > 0L } ?: System.currentTimeMillis()
    )

    private fun saveCache(context: Context, s: SharedLocationSnapshot) {
        runCatching {
            context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE).edit()
                .putString("lat", s.latitude.toString())
                .putString("lon", s.longitude.toString())
                .putFloat("acc", s.accuracyMeters)
                .putString("provider", s.provider)
                .putLong("time", s.timeMs)
                .apply()
        }
    }

    private fun cached(context: Context): SharedLocationSnapshot? = runCatching {
        val p = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        val lat = p.getString("lat", null)?.toDoubleOrNull() ?: return null
        val lon = p.getString("lon", null)?.toDoubleOrNull() ?: return null
        SharedLocationSnapshot(lat, lon, p.getFloat("acc", 1000f), p.getString("provider", "cache") ?: "cache", p.getLong("time", 0L))
    }.getOrNull()
}
