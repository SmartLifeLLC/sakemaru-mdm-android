package com.smartlife.sakemaru.bansuke.command

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smartlife.sakemaru.bansuke.diagnostics.MdmLog

class AdminActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CLEAR_DEVICE_OWNER) return
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return
        if (!dpm.isDeviceOwnerApp(context.packageName)) return
        dpm.clearDeviceOwnerApp(context.packageName)
        MdmLog.info("Device Owner cleared via ADB broadcast")
    }

    companion object {
        const val ACTION_CLEAR_DEVICE_OWNER = "com.smartlife.sakemaru.bansuke.CLEAR_DEVICE_OWNER"
    }
}
