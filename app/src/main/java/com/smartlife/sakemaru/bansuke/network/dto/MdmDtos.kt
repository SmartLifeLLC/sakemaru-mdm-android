package com.smartlife.sakemaru.bansuke.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiEnvelope<T>(
    val data: T,
)

@Serializable
data class RegisterRequest(
    @SerialName("registration_key") val registrationKey: String,
    val name: String? = null,
    @SerialName("fcm_token") val fcmToken: String? = null,
    val platform: String = "android",
)

@Serializable
data class RegisterData(
    val id: Int,
    @SerialName("device_code") val deviceCode: String,
    @SerialName("device_access_token") val deviceAccessToken: String,
    val name: String? = null,
    val status: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
)

@Serializable
data class FcmTokenRequest(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("registration_key") val registrationKey: String? = null,
    @SerialName("fcm_token") val fcmToken: String,
    val platform: String = "android",
)

@Serializable
data class FcmTokenData(
    val id: Int,
    @SerialName("device_id") val deviceId: Int? = null,
    val platform: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class HeartbeatRequest(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("registration_key") val registrationKey: String? = null,
    val name: String,
    val status: String = "active",
    val location: HeartbeatLocationDto? = null,
)

@Serializable
data class HeartbeatLocationDto(
    val latitude: Double,
    val longitude: Double,
    @SerialName("recorded_at") val recordedAt: String? = null,
)

@Serializable
data class InstalledAppDto(
    @SerialName("package_name") val packageName: String,
    @SerialName("app_name") val appName: String? = null,
    @SerialName("version_code") val versionCode: Long? = null,
    @SerialName("version_name") val versionName: String? = null,
    @SerialName("is_system") val isSystem: Boolean = false,
)

@Serializable
data class InstalledAppsReportRequest(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("registration_key") val registrationKey: String? = null,
    val apps: List<InstalledAppDto>,
)

@Serializable
data class InstalledAppsReportData(
    @SerialName("device_id") val deviceId: Int,
    @SerialName("reported_count") val reportedCount: Int,
    @SerialName("active_count") val activeCount: Int,
    @SerialName("removed_count") val removedCount: Int,
    @SerialName("reported_at") val reportedAt: String,
)

@Serializable
data class DeviceCommandDto(
    val id: Long,
    @SerialName("device_id") val deviceId: Int? = null,
    val type: String,
    val payload: JsonElement? = null,
    val status: String? = null,
    @SerialName("executed_at") val executedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CommandResultRequest(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("registration_key") val registrationKey: String? = null,
    @SerialName("command_id") val commandId: Long,
    val result: String,
    @SerialName("error_code") val errorCode: String? = null,
    val message: String? = null,
)

@Serializable
data class AppUpdateCheckRequest(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("registration_key") val registrationKey: String? = null,
    @SerialName("app_name") val appName: String,
    @SerialName("version_code") val versionCode: Int,
)

@Serializable
data class AppUpdateCheckData(
    val device: DeviceData? = null,
    @SerialName("app_name") val appName: String,
    @SerialName("current_version_code") val currentVersionCode: Int,
    @SerialName("latest_version_code") val latestVersionCode: Int? = null,
    @SerialName("update_available") val updateAvailable: Boolean,
    @SerialName("force_update") val forceUpdate: Boolean = false,
    val latest: AppVersionData? = null,
)

@Serializable
data class DeviceData(
    val id: Int,
    @SerialName("device_code") val deviceCode: String,
    val name: String? = null,
    val status: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
)

@Serializable
data class AppVersionData(
    val id: Int,
    @SerialName("app_name") val appName: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String? = null,
    @SerialName("apk_url") val apkUrl: String,
    @SerialName("checksum_sha256") val checksumSha256: String? = null,
    @SerialName("release_notes") val releaseNotes: String? = null,
    @SerialName("is_force_update") val isForceUpdate: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)
