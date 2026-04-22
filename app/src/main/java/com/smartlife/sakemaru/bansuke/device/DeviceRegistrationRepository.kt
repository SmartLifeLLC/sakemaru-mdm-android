package com.smartlife.sakemaru.bansuke.device

import com.smartlife.sakemaru.bansuke.config.DeviceConfig
import com.smartlife.sakemaru.bansuke.config.DeviceConfigStore
import com.smartlife.sakemaru.bansuke.diagnostics.MdmLog
import com.smartlife.sakemaru.bansuke.network.MdmApiClient
import com.smartlife.sakemaru.bansuke.network.MdmApiException
import com.smartlife.sakemaru.bansuke.network.dto.CommandResultRequest
import com.smartlife.sakemaru.bansuke.network.dto.FcmTokenRequest
import com.smartlife.sakemaru.bansuke.network.dto.HeartbeatRequest
import com.smartlife.sakemaru.bansuke.network.dto.InstalledAppDto
import com.smartlife.sakemaru.bansuke.network.dto.InstalledAppsReportRequest
import com.smartlife.sakemaru.bansuke.network.dto.RegisterRequest

class DeviceRegistrationRepository(
    private val configStore: DeviceConfigStore,
) {
    suspend fun registerDevice(): DeviceConfig {
        configStore.ensureRegistrationKey()
        val config = requireProvisioningConfig(configStore.read())
        val data = MdmApiClient(config.mdmBaseUrl).register(
            RegisterRequest(
                registrationKey = config.registrationKey,
                name = config.deviceName.ifBlank { null },
                fcmToken = config.fcmToken.ifBlank { null },
            )
        )
        configStore.saveRegistration(data)
        MdmLog.info("Device registered: serverDeviceId=${data.id}, device=${data.deviceCode}")
        return configStore.read()
    }

    suspend fun registerFcmToken(token: String) {
        configStore.saveFcmToken(token)
        val config = configStore.read()
        if (!config.isRegistered) {
            MdmLog.info("FCM token saved locally; server token registration skipped until device registration")
            return
        }

        resetRegistrationOnUnauthorized {
            MdmApiClient(config.mdmBaseUrl).registerFcmToken(
                authorization = config.bearerAuthorization(),
                request = FcmTokenRequest(
                    deviceCode = config.deviceCode,
                    registrationKey = config.registrationKey,
                    fcmToken = token,
                )
            )
        }
        MdmLog.info("FCM token registered with server: device=${config.deviceCode}, tokenLength=${token.length}")
    }

    suspend fun heartbeat(status: String = "active") {
        val config = configStore.read()
        if (!config.isRegistered) return

        resetRegistrationOnUnauthorized {
            MdmApiClient(config.mdmBaseUrl).heartbeat(
                authorization = config.bearerAuthorization(),
                request = HeartbeatRequest(
                    deviceCode = config.deviceCode,
                    registrationKey = config.registrationKey,
                    name = config.displayName,
                    status = status,
                )
            )
        }
        MdmLog.info("Heartbeat sent: device=${config.deviceCode}, status=$status")
    }

    suspend fun reportInstalledApps(apps: List<InstalledAppDto>) {
        val config = configStore.read()
        if (!config.isRegistered) return

        val result = resetRegistrationOnUnauthorized {
            MdmApiClient(config.mdmBaseUrl).reportInstalledApps(
                authorization = config.bearerAuthorization(),
                request = InstalledAppsReportRequest(
                    deviceCode = config.deviceCode,
                    registrationKey = config.registrationKey,
                    apps = apps,
                ),
            )
        }
        MdmLog.info(
            "Installed apps reported: reported=${result.reportedCount}, " +
                "active=${result.activeCount}, removed=${result.removedCount}"
        )
    }

    suspend fun sendCommandResult(
        commandId: Long,
        result: String,
        errorCode: String? = null,
        message: String? = null,
    ) {
        val config = configStore.read()
        if (!config.isRegistered) return

        resetRegistrationOnUnauthorized {
            MdmApiClient(config.mdmBaseUrl).commandResult(
                authorization = config.bearerAuthorization(),
                request = CommandResultRequest(
                    deviceCode = config.deviceCode,
                    registrationKey = config.registrationKey,
                    commandId = commandId,
                    result = result,
                    errorCode = errorCode,
                    message = message,
                )
            )
        }
    }

    suspend fun resetRegistration() {
        configStore.resetRegistration()
        MdmLog.info("Local device registration reset; provisioning config and FCM token preserved")
    }

    private fun requireProvisioningConfig(config: DeviceConfig): DeviceConfig {
        require(config.hasProvisioningConfig) {
            "Missing provisioning config: mdm environment and registration key are required"
        }
        return config
    }

    private suspend fun <T> resetRegistrationOnUnauthorized(block: suspend () -> T): T {
        return try {
            block()
        } catch (exception: MdmApiException) {
            if (exception.statusCode == 401) {
                resetRegistration()
                MdmLog.warn("Device access token was rejected by the server; local registration was reset", exception)
            }
            throw exception
        }
    }
}
