package com.example.jewelrymanagement.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

fun Activity.showEnableLocationDialog() {
    AlertDialog.Builder(this)
        .setTitle("Vị trí đang tắt")
        .setMessage("Hãy bật Vị trí (GPS) trên điện thoại để ứng dụng lấy được địa chỉ của bạn.")
        .setPositiveButton("Mở cài đặt") { _, _ ->
            runCatching { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        }
        .setNegativeButton("Để sau", null)
        .show()
}

/** Turns the device's current location into a shipping address and remembers it across sessions. */
object LocationAddressHelper {

    val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    private const val PREF_NAME = "JewelryLocationPrefs"
    private const val KEY_ADDRESS = "detected_address"
    private const val MAX_LAST_KNOWN_AGE_MS = 10 * 60 * 1000L
    private const val FRESH_LOCATION_TIMEOUT_MS = 15_000L

    sealed class Result {
        data class Success(val address: String) : Result()
        object LocationDisabled : Result()
        object NoLocation : Result()
        object GeocodeFailed : Result()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun hasPermission(context: Context): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun getSavedAddress(context: Context): String = prefs(context).getString(KEY_ADDRESS, "").orEmpty()

    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    /** Requires location permission. Saves the address on success. */
    suspend fun detectAddress(context: Context): Result {
        val appContext = context.applicationContext
        if (!isLocationEnabled(appContext)) return Result.LocationDisabled
        val location = currentLocation(appContext) ?: return Result.NoLocation
        val address = reverseGeocode(appContext, location) ?: return Result.GeocodeFailed
        prefs(appContext).edit().putString(KEY_ADDRESS, address).apply()
        return Result.Success(address)
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(context: Context): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = lm.getProviders(true)
        val lastKnown = providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < MAX_LAST_KNOWN_AGE_MS) return lastKnown

        val provider = when {
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            else -> return lastKnown
        }
        val fresh = withTimeoutOrNull(FRESH_LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(context)) {
                    if (cont.isActive) cont.resume(it)
                }
            }
        }
        return fresh ?: lastKnown
    }

    private suspend fun reverseGeocode(context: Context, location: Location): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.forLanguageTag("vi-VN"))
        val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(FRESH_LOCATION_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (cont.isActive) cont.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(null)
                        }
                    })
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull() }.getOrNull()
            }
        }
        return address?.let(::format)
    }

    private fun format(address: Address): String? {
        val line = address.getAddressLine(0)
        if (!line.isNullOrBlank()) return line.trim()
        return listOfNotNull(
            address.subThoroughfare, address.thoroughfare, address.subLocality,
            address.locality, address.subAdminArea, address.adminArea
        ).filter { it.isNotBlank() }.distinct().joinToString(", ").ifBlank { null }
    }
}
