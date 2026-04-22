package com.smartlife.sakemaru.bansuke

import android.app.Application
import com.smartlife.sakemaru.bansuke.worker.MdmWorkScheduler

class BansukeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MdmWorkScheduler.scheduleLoops(this)
        MdmWorkScheduler.enqueueRegistration(this)
    }
}
