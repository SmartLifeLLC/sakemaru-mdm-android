package com.smartlife.sakemaru.bansuke

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.smartlife.sakemaru.bansuke.ui.MainActivity
import com.smartlife.sakemaru.bansuke.worker.MdmWorkScheduler

class BansukeDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        MdmWorkScheduler.scheduleLoops(context)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        MdmWorkScheduler.scheduleLoops(context)
        MdmWorkScheduler.enqueueRegistration(context)
        MdmWorkScheduler.enqueueImmediateSync(context)
        MdmWorkScheduler.enqueueInstalledAppsReport(context)
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
