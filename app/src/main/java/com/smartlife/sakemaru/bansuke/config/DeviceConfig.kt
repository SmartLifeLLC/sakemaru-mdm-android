package com.smartlife.sakemaru.bansuke.config

data class DeviceConfig(
    val mdmEnvironment: String = ServerEnvironment.default().name,
    val mdmBaseUrl: String = "",
    val registrationKey: String = "",
    val deviceCode: String = "",
    val deviceName: String = "",
    val deviceAccessToken: String = "",
    val fcmToken: String = "",
    val registeredDeviceId: Int? = null,
) {
    val hasProvisioningConfig: Boolean
        get() = mdmBaseUrl.isNotBlank() && registrationKey.isNotBlank()

    val isRegistered: Boolean
        get() = hasProvisioningConfig && deviceCode.isNotBlank() && deviceAccessToken.isNotBlank() && registeredDeviceId != null

    val displayName: String
        get() = deviceName.ifBlank { deviceCode }

    fun bearerAuthorization(): String = "Bearer $deviceAccessToken"
}
