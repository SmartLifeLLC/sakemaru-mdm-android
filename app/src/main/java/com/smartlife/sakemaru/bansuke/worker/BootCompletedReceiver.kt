package com.smartlife.sakemaru.bansuke.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

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
            }
        }
    }
}
