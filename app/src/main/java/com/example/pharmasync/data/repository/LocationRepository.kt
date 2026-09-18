package com.example.pharmasync.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.pharmasync.BuildConfig
import com.example.pharmasync.data.remote.GeoapifyApi
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * Geocoding uses the on-device [Geocoder] (free, no key). Geoapify is used as a fallback only if a
 * GEOAPIFY_API_KEY is configured in local.properties.
 */
class LocationRepository(
    private val context: Context,
    private val geoapify: GeoapifyApi,
) {
    suspend fun geocode(address: String): GeoPoint? {
        if (address.isBlank()) return null
        val targetAddress = when {
            address.contains("Kenya", ignoreCase = true) -> address
            address.contains("Nairobi", ignoreCase = true) -> "$address, Kenya"
            else -> "$address, Nairobi, Kenya"
        }
        val fromDevice = runCatching { deviceGeocode(targetAddress) }.getOrNull()
            ?: runCatching { deviceGeocode(address) }.getOrNull()
        if (fromDevice != null) return fromDevice
        val key = BuildConfig.GEOAPIFY_API_KEY
        if (key.isBlank()) return null
        return runCatching {
            geoapify.search(targetAddress, key).features?.firstOrNull()?.geometry?.coordinates
                ?.takeIf { it.size >= 2 }
                ?.let { GeoPoint(latitude = it[1], longitude = it[0]) }
        }.getOrNull() ?: runCatching {
            geoapify.search(address, key).features?.firstOrNull()?.geometry?.coordinates
                ?.takeIf { it.size >= 2 }
                ?.let { GeoPoint(latitude = it[1], longitude = it[0]) }
        }.onFailure { Log.w(TAG, "Geoapify failed", it) }.getOrNull()
    }

    suspend fun reverseGeocode(point: GeoPoint): String? = runCatching {
        addresses { geocoder, cb ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(point.latitude, point.longitude, 1) { cb(it) }
            } else {
                @Suppress("DEPRECATION")
                cb(geocoder.getFromLocation(point.latitude, point.longitude, 1).orEmpty())
            }
        }.firstOrNull()?.getAddressLine(0)
    }.getOrNull()

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): GeoPoint? {
        if (!hasLocationPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val location: Location? = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await()
            ?: client.lastLocation.await()
        return location?.let { GeoPoint(it.latitude, it.longitude) }
    }

    private suspend fun deviceGeocode(address: String): GeoPoint? {
        if (!Geocoder.isPresent()) return null
        return addresses { geocoder, cb ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocationName(address, 1) { cb(it) }
            } else {
                @Suppress("DEPRECATION")
                cb(geocoder.getFromLocationName(address, 1).orEmpty())
            }
        }.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
    }

    private suspend fun addresses(block: (Geocoder, (List<Address>) -> Unit) -> Unit): List<Address> =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                try {
                    block(Geocoder(context, Locale.getDefault())) { if (cont.isActive) cont.resume(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "Geocoder failed", e)
                    if (cont.isActive) cont.resume(emptyList())
                }
            }
        }

    private companion object {
        const val TAG = "LocationRepository"
    }
}
