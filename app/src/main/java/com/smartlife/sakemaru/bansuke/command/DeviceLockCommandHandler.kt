package com.smartlife.sakemaru.bansuke.command

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.smartlife.sakemaru.bansuke.BansukeDeviceAdminReceiver
import com.smartlife.sakemaru.bansuke.diagnostics.MdmLog
import com.smartlife.sakemaru.bansuke.network.dto.DeviceCommandDto
import com.smartlife.sakemaru.bansuke.ui.LockScreenActivity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class DeviceLockCommandHandler(context: Context) {

    private val appContext = context.applicationContext
    private val dpm = appContext.getSystemService(DevicePolicyManager::class.java)
    private val adminComponent = ComponentName(appContext, BansukeDeviceAdminReceiver::class.java)

    fun lock(command: DeviceCommandDto): CommandExecutionOutcome {
        if (dpm == null || !dpm.isDeviceOwnerApp(appContext.packageName)) {
            return CommandExecutionOutcome.error("NOT_DEVICE_OWNER", "App is not device owner")
        }

        val payload = command.payload as? JsonObject
        val message = payload
            ?.get("message")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_LOCK_MESSAGE

        runCatching {
            dpm.setLockTaskPackages(adminComponent, arrayOf(appContext.packageName))
        }.onFailure { throwable ->
            MdmLog.warn("setLockTaskPackages failed: ${throwable.message}")
        }

        saveLockMessage(message)
        dpm.lockNow()
        dpm.setDeviceOwnerLockScreenInfo(adminComponent, message)
        LockScreenActivity.start(appContext, message)

        MdmLog.info("Device locked: message=$message")
        return CommandExecutionOutcome.done("locked")
    }

    fun unlock(): CommandExecutionOutcome {
        if (dpm == null || !dpm.isDeviceOwnerApp(appContext.packageName)) {
            return CommandExecutionOutcome.error("NOT_DEVICE_OWNER", "App is not device owner")
        }

        LockScreenActivity.dismiss(appContext)
        clearLockMessage()
        dpm.setDeviceOwnerLockScreenInfo(adminComponent, null)

        runCatching {
            dpm.setLockTaskPackages(adminComponent, emptyArray())
        }.onFailure { throwable ->
            MdmLog.warn("clearLockTaskPackages failed: ${throwable.message}")
        }

        MdmLog.info("Device unlocked")
        return CommandExecutionOutcome.done("unlocked")
    }

    private fun saveLockMessage(message: String) {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LOCK_MESSAGE, message).apply()
    }

    private fun clearLockMessage() {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_LOCK_MESSAGE).apply()
    }

    companion object {
        private const val PREFS_NAME = "mdm_lock"
        private const val KEY_LOCK_MESSAGE = "lock_message"
        private const val DEFAULT_LOCK_MESSAGE = "この端末は管理者によりロックされています"

        fun isLocked(context: Context): Boolean {
            return lockMessage(context) != null
        }

        fun lockMessage(context: Context): String? {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LOCK_MESSAGE, null)
        }
    }
}
