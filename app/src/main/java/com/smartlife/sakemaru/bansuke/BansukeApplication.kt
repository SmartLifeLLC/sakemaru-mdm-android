package com.smartlife.sakemaru.bansuke

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.smartlife.sakemaru.bansuke.worker.MdmWorkScheduler

class BansukeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        MdmWorkScheduler.scheduleLoops(this)
        MdmWorkScheduler.enqueueRegistration(this)
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            DEFAULT_CHANNEL_ID,
            "MDM",
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.setSound(null, null)
        channel.enableVibration(false)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        const val DEFAULT_CHANNEL_ID = "mdm_default"
    }
}
