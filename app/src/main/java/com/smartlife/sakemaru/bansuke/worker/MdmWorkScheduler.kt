package com.smartlife.sakemaru.bansuke.worker

import android.content.Context

object MdmWorkScheduler {
    fun scheduleLoops(context: Context) {
        MdmForegroundService.start(context)
        InstalledAppsReportWorker.scheduleNext(context)
    }

    fun enqueueRegistration(context: Context) {
        DeviceRegistrationWorker.enqueue(context)
    }

    fun enqueueImmediateSync(context: Context) {
        HeartbeatWorker.enqueueImmediate(context)
        CommandSyncWorker.enqueueImmediate(context)
    }

    fun enqueueInstalledAppsReport(context: Context) {
        InstalledAppsReportWorker.enqueueImmediate(context)
    }
}
