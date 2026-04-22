package com.smartlife.sakemaru.bansuke.worker

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.smartlife.sakemaru.bansuke.BansukeDeviceAdminReceiver
import com.smartlife.sakemaru.bansuke.command.DeviceLockCommandHandler
import com.smartlife.sakemaru.bansuke.ui.LockScreenActivity

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED
            -> {
                MdmWorkScheduler.scheduleLoops(context)
                MdmWorkScheduler.enqueueRegistration(context)
                MdmWorkScheduler.enqueueImmediateSync(context)
                MdmWorkScheduler.enqueueInstalledAppsReport(context)

                val message = DeviceLockCommandHandler.lockMessage(context)
                if (message != null) {
                    val dpm = context.getSystemService(DevicePolicyManager::class.java)
                    if (dpm != null && dpm.isDeviceOwnerApp(context.packageName)) {
                        val admin = ComponentName(context, BansukeDeviceAdminReceiver::class.java)
                        runCatching { dpm.setLockTaskPackages(admin, arrayOf(context.packageName)) }
                    }
                    LockScreenActivity.start(context, message)
                }
            }
        }
    }
}
