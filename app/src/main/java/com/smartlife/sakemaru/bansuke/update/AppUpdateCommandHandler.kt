package com.smartlife.sakemaru.bansuke.update

import android.content.Context
import com.smartlife.sakemaru.bansuke.command.CommandExecutionOutcome
import com.smartlife.sakemaru.bansuke.config.DeviceConfig
import com.smartlife.sakemaru.bansuke.diagnostics.MdmLog
import com.smartlife.sakemaru.bansuke.network.MdmApiClient
import com.smartlife.sakemaru.bansuke.network.dto.AppUpdateCheckData
import com.smartlife.sakemaru.bansuke.network.dto.AppUpdateCheckRequest
import com.smartlife.sakemaru.bansuke.network.dto.DeviceCommandDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class AppUpdateCommandHandler(context: Context) {
    private val appContext = context.applicationContext
    private val versionReader = AppVersionReader(appContext)
    private val downloader = ApkDownloader()
    private val packageInstallManager = PackageInstallManager(appContext)

    suspend fun handle(config: DeviceConfig, command: DeviceCommandDto): CommandExecutionOutcome {
        val payload = command.payload as? JsonObject
            ?: return CommandExecutionOutcome.error("INVALID_PAYLOAD", "app_update payload must be a JSON object")
        val appName = payload
            .get("app_name")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: return CommandExecutionOutcome.error("INVALID_PAYLOAD", "Missing app_name in app_update payload")

        val api = MdmApiClient(config.mdmBaseUrl)
        val auth = config.bearerAuthorization()
        var packageName = ManagedAppResolver.packageNameHint(appName, payload)
        var currentVersionCode = packageName?.let { versionReader.installedVersionCode(it) } ?: 0
        var update = updateCheck(api, auth, config, appName, currentVersionCode)

        val latestPackageName = update.latest?.packageName ?: packageName
        if (!latestPackageName.isNullOrBlank()) {
            packageName = latestPackageName
            val actualVersionCode = versionReader.installedVersionCode(latestPackageName)
            if (actualVersionCode != currentVersionCode) {
                currentVersionCode = actualVersionCode
                update = updateCheck(api, auth, config, appName, currentVersionCode)
            }
        }

        if (!update.updateAvailable) {
            MdmLog.info("App update skipped: app=$appName currentVersion=$currentVersionCode")
            return CommandExecutionOutcome.done("no update")
        }

        val latest = update.latest
            ?: return CommandExecutionOutcome.error("INVALID_UPDATE_RESPONSE", "update_available=true but latest is null")

        val apkFile = File(appContext.cacheDir, "mdm-apk/${latest.appName}-${latest.versionCode}-${command.id}.apk")
        return try {
            MdmLog.info("Downloading APK: app=${latest.appName}, versionCode=${latest.versionCode}")
            downloader.download(latest.apkUrl, apkFile)
            val checksum = latest.checksumSha256
            if (!checksum.isNullOrBlank() && !Sha256.matches(apkFile, checksum)) {
                MdmLog.warn("APK checksum mismatch: app=${latest.appName}, versionCode=${latest.versionCode}")
                return CommandExecutionOutcome.error("CHECKSUM_MISMATCH", "apk checksum mismatch")
            }

            when (val install = packageInstallManager.install(apkFile, latest.packageName)) {
                PackageInstallOutcome.Success -> {
                    MdmLog.info("APK install succeeded: package=${latest.packageName}, versionCode=${latest.versionCode}")
                    CommandExecutionOutcome.done("updated")
                }
                is PackageInstallOutcome.Failure -> CommandExecutionOutcome.error(
                    errorCode = "INSTALL_FAILED",
                    message = install.message,
                )
            }
        } catch (throwable: Throwable) {
            CommandExecutionOutcome.error(
                errorCode = "DOWNLOAD_FAILED",
                message = throwable.message ?: throwable::class.java.simpleName,
            )
        } finally {
            apkFile.delete()
        }
    }

    private suspend fun updateCheck(
        api: MdmApiClient,
        authorization: String,
        config: DeviceConfig,
        appName: String,
        versionCode: Int,
    ): AppUpdateCheckData =
        api.appUpdateCheck(
            authorization = authorization,
            request = AppUpdateCheckRequest(
                deviceCode = config.deviceCode,
                registrationKey = config.registrationKey,
                appName = appName,
                versionCode = versionCode,
            ),
        )
}
