package com.example.saira

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.util.Locale

data class SharedLocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val provider: String
) {
    fun toShareText(): String {
        val lat = String.format(Locale.US, "%.5f", latitude)
        val lon = String.format(Locale.US, "%.5f", longitude)
        return "আমার লাইভ লোকেশন (প্রায় ${accuracyMeters.toInt()}m): https://maps.google.com/?q=$lat,$lon"
    }
}

object SairaLocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun getBestAvailableLocation(context: Context): SharedLocationSnapshot? {
        if (!hasLocationPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var best: Location? = null
        for (provider in providers) {
            val loc = runCatching {
                if (manager.isProviderEnabled(provider)) {
                    manager.getLastKnownLocation(provider)
                } else {
                    null
                }
            }.getOrNull() ?: continue

            if (best == null || loc.time > best.time) {
                best = loc
            }
        }

        val chosen = best ?: return null
        return SharedLocationSnapshot(
            latitude = chosen.latitude,
            longitude = chosen.longitude,
            accuracyMeters = chosen.accuracy,
            provider = chosen.provider ?: "network"
        )
    }
}
