package com.smartlife.sakemaru.bansuke.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.smartlife.sakemaru.bansuke.diagnostics.MdmLog
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val recordedAt: String,
)

class LocationSnapshotProvider(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    @Volatile
    private var cachedLocation: Location? = null
    private var listening = false
    private var currentIntervalMs = currentLocationInterval()

    private val locationListener = LocationListener { location ->
        cachedLocation = location
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (listening || locationManager == null || !hasAnyLocationPermission(appContext)) return
        currentIntervalMs = currentLocationInterval()
        registerUpdates(currentIntervalMs)
        cachedLocation = lastKnownLocationOrNull()
        listening = true
    }

    fun refreshIntervalIfNeeded() {
        if (!listening || locationManager == null) return
        val newInterval = currentLocationInterval()
        if (newInterval != currentIntervalMs) {
            MdmLog.info("Location interval changed: ${currentIntervalMs / 1000}s -> ${newInterval / 1000}s")
            runCatching { locationManager.removeUpdates(locationListener) }
            currentIntervalMs = newInterval
            registerUpdates(newInterval)
        }
    }

    fun stopListening() {
        if (!listening || locationManager == null) return
        runCatching { locationManager.removeUpdates(locationListener) }
        listening = false
    }

    @SuppressLint("MissingPermission")
    private fun registerUpdates(intervalMs: Long) {
        val providers = buildList {
            if (hasFineLocationPermission(appContext) && locationManager!!.isProviderEnabledSafe(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (locationManager!!.isProviderEnabledSafe(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
        }
        providers.forEach { provider ->
            runCatching {
                locationManager!!.requestLocationUpdates(
                    provider,
                    intervalMs,
                    LOCATION_MIN_DISTANCE_M,
                    locationListener,
                    Looper.getMainLooper(),
                )
            }.onFailure { throwable ->
                MdmLog.warn("Failed to start location updates for $provider: ${throwable.message}")
            }
        }
        MdmLog.info("Location listener started: providers=${providers.joinToString()}, interval=${intervalMs / 1000}s")
    }

    fun currentOrNull(): LocationSnapshot? {
        val location = cachedLocation ?: lastKnownLocationOrNull()
        if (location == null) {
            MdmLog.warn("Location was unavailable during heartbeat")
            return null
        }

        val ageMs = System.currentTimeMillis() - location.time
        if (ageMs > LOCATION_STALE_MS) {
            MdmLog.warn("Location is stale: age=${ageMs / 1000}s, discarding")
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
    private fun lastKnownLocationOrNull(): Location? {
        if (locationManager == null || !hasAnyLocationPermission(appContext)) return null
        return buildList {
            if (hasFineLocationPermission(appContext) && locationManager.isProviderEnabledSafe(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (locationManager.isProviderEnabledSafe(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
            if (locationManager.isProviderEnabledSafe(LocationManager.PASSIVE_PROVIDER)) {
                add(LocationManager.PASSIVE_PROVIDER)
            }
        }.distinct()
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
    }

    private fun LocationManager.isProviderEnabledSafe(provider: String): Boolean =
        runCatching { isProviderEnabled(provider) }.getOrDefault(false)

    companion object {
        private const val LOCATION_INTERVAL_BUSINESS_MS = 120_000L
        private const val LOCATION_INTERVAL_OFF_MS = 300_000L
        private const val LOCATION_MIN_DISTANCE_M = 0f
        private const val LOCATION_STALE_MS = 10 * 60_000L
        private val JST = ZoneId.of("Asia/Tokyo")

        fun currentLocationInterval(): Long {
            val hour = LocalTime.now(JST).hour
            return if (hour in 8..18) LOCATION_INTERVAL_BUSINESS_MS else LOCATION_INTERVAL_OFF_MS
        }

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
