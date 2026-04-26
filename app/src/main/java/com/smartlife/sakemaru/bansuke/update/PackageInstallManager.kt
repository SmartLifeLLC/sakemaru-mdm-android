package com.smartlife.sakemaru.bansuke.update

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import com.smartlife.sakemaru.bansuke.BansukeDeviceAdminReceiver
import com.smartlife.sakemaru.bansuke.diagnostics.MdmLog
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

class PackageInstallManager(context: Context) {
    private val appContext = context.applicationContext

    suspend fun install(apkFile: File, packageName: String): PackageInstallOutcome {
        val devicePolicyManager = appContext.getSystemService(DevicePolicyManager::class.java)
        if (devicePolicyManager?.isDeviceOwnerApp(appContext.packageName) != true) {
            return PackageInstallOutcome.Failure("Device Owner is required for silent install")
        }

        val adminComponent = ComponentName(appContext, BansukeDeviceAdminReceiver::class.java)
        suppressPlayStoreVerifier(devicePolicyManager, adminComponent, true)

        try {
            return doInstall(apkFile, packageName)
        } finally {
            suppressPlayStoreVerifier(devicePolicyManager, adminComponent, false)
        }
    }

    private fun suppressPlayStoreVerifier(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        hide: Boolean,
    ) {
        runCatching {
            dpm.setApplicationHidden(admin, PLAY_STORE_PACKAGE, hide)
            MdmLog.info("Play Store ${if (hide) "hidden" else "restored"} for install")
        }.onFailure { throwable ->
            MdmLog.warn("Failed to ${if (hide) "hide" else "restore"} Play Store: ${throwable.message}")
        }
    }

    private suspend fun doInstall(apkFile: File, packageName: String): PackageInstallOutcome {
        return suspendCancellableCoroutine { continuation ->
            val installer = appContext.packageManager.packageInstaller
            var sessionId: Int? = null
            var receiverRegistered = false
            var receiver: BroadcastReceiver? = null

            fun unregisterReceiver() {
                if (!receiverRegistered) return
                runCatching { appContext.unregisterReceiver(receiver) }
                receiverRegistered = false
            }

            try {
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                params.setAppPackageName(packageName)
                params.setInstallReason(PackageManager.INSTALL_REASON_POLICY)
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                params.setDontKillApp(true)
                val createdSessionId = installer.createSession(params)
                sessionId = createdSessionId
                val action = "${appContext.packageName}.INSTALL_RESULT.$createdSessionId"

                receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        unregisterReceiver()
                        val status = intent.getIntExtra(
                            PackageInstaller.EXTRA_STATUS,
                            PackageInstaller.STATUS_FAILURE,
                        )
                        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                            ?: "PackageInstaller status: $status"
                        val outcome = if (status == PackageInstaller.STATUS_SUCCESS) {
                            PackageInstallOutcome.Success
                        } else {
                            PackageInstallOutcome.Failure(message)
                        }
                        if (continuation.isActive) {
                            continuation.resume(outcome)
                        }
                    }
                }
                val filter = IntentFilter(action)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("DEPRECATION")
                    appContext.registerReceiver(receiver, filter)
                }
                receiverRegistered = true

                installer.openSession(createdSessionId).use { session ->
                    apkFile.inputStream().use { input ->
                        session.openWrite("package", 0, apkFile.length()).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }

                    val intent = Intent(action).setPackage(appContext.packageName)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            PendingIntent.FLAG_MUTABLE
                        } else {
                            0
                        }
                    val pendingIntent = PendingIntent.getBroadcast(
                        appContext,
                        createdSessionId,
                        intent,
                        flags,
                    )
                    session.commit(pendingIntent.intentSender)
                }
            } catch (throwable: Throwable) {
                unregisterReceiver()
                sessionId?.let { runCatching { installer.abandonSession(it) } }
                if (continuation.isActive) {
                    continuation.resume(
                        PackageInstallOutcome.Failure(
                            throwable.message ?: throwable::class.java.simpleName,
                        )
                    )
                }
            }

            continuation.invokeOnCancellation {
                unregisterReceiver()
                sessionId?.let { runCatching { installer.abandonSession(it) } }
            }
        }
    }

    companion object {
        private const val PLAY_STORE_PACKAGE = "com.android.vending"
    }
}

sealed interface PackageInstallOutcome {
    data object Success : PackageInstallOutcome
    data class Failure(val message: String) : PackageInstallOutcome
}
