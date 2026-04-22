package com.smartlife.sakemaru.bansuke.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.smartlife.sakemaru.bansuke.diagnostics.MdmLog
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import kotlin.coroutines.resume

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val recordedAt: String,
)

class LocationSnapshotProvider(context: Context) {
    private val appContext = context.applicationContext

    suspend fun currentOrNull(): LocationSnapshot? {
        if (!hasAnyLocationPermission(appContext)) {
            return null
        }

        val locationManager = appContext.getSystemService(LocationManager::class.java) ?: return null
        val location = currentLocationOrNull(locationManager) ?: lastKnownLocationOrNull(locationManager)

        if (location == null) {
            MdmLog.warn("Location was unavailable during heartbeat")
            return null
        }

        val recordedAtMillis = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()

        return LocationSnapshot(
            latitude = location.latitude,
            longitude = location.longitude,
            recordedAt = Instant.ofEpochMilli(recordedAtMillis).toString(),
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocationOrNull(locationManager: LocationManager): Location? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null
        }

        for (provider in currentLocationProviders(locationManager)) {
            val location = suspendCancellableCoroutine<Location?> { continuation ->
                val signal = CancellationSignal()

                runCatching {
                    locationManager.getCurrentLocation(
                        provider,
                        signal,
                        ContextCompat.getMainExecutor(appContext),
                    ) { result ->
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    }
                }.onFailure {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }

                continuation.invokeOnCancellation {
                    signal.cancel()
                }
            }

            if (location != null) {
                return location
            }
        }

        return null
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocationOrNull(locationManager: LocationManager): Location? =
        lastKnownProviders(locationManager)
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }

    private fun currentLocationProviders(locationManager: LocationManager): List<String> = buildList {
        if (hasFineLocationPermission(appContext) && locationManager.isProviderEnabledSafe(LocationManager.GPS_PROVIDER)) {
            add(LocationManager.GPS_PROVIDER)
        }
        if (locationManager.isProviderEnabledSafe(LocationManager.NETWORK_PROVIDER)) {
            add(LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun lastKnownProviders(locationManager: LocationManager): List<String> = buildList {
        addAll(currentLocationProviders(locationManager))
        if (locationManager.isProviderEnabledSafe(LocationManager.PASSIVE_PROVIDER)) {
            add(LocationManager.PASSIVE_PROVIDER)
        }
    }.distinct()

    private fun LocationManager.isProviderEnabledSafe(provider: String): Boolean =
        runCatching { isProviderEnabled(provider) }.getOrDefault(false)

    companion object {
        val foregroundPermissions: Array<String> = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        fun managedDevicePermissions(): List<String> = buildList {
            addAll(foregroundPermissions)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }

        fun hasFineLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun hasAnyLocationPermission(context: Context): Boolean =
            foregroundPermissions.any { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }

        fun hasBackgroundLocationPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED

        fun permissionStatusLabel(context: Context): String = when {
            !hasAnyLocationPermission(context) -> "missing"
            !hasBackgroundLocationPermission(context) -> "foreground only"
            else -> "granted"
        }
    }
}
