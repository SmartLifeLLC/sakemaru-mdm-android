package com.smartlife.sakemaru.bansuke.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.smartlife.sakemaru.bansuke.config.DeviceConfigStore
import com.smartlife.sakemaru.bansuke.device.DeviceRegistrationRepository
import com.smartlife.sakemaru.bansuke.diagnostics.MdmLog
import com.smartlife.sakemaru.bansuke.inventory.InstalledAppInventoryCollector
import com.smartlife.sakemaru.bansuke.network.MdmApiException
import java.util.concurrent.TimeUnit

class InstalledAppsReportWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            val store = DeviceConfigStore(applicationContext)
            val config = store.read()
            if (config.isRegistered) {
                val apps = InstalledAppInventoryCollector(applicationContext).collect()
                MdmLog.info("Installed app inventory collected: count=${apps.size}")
                DeviceRegistrationRepository(store).reportInstalledApps(apps)
            } else {
                MdmLog.info("Installed app inventory skipped: device is not registered")
            }
            scheduleNext(applicationContext, append = true)
            Result.success()
        } catch (throwable: Throwable) {
            MdmLog.warn("Installed app inventory report failed: ${throwable.message}", throwable)
            if (throwable is MdmApiException && throwable.statusCode == 401) {
                DeviceRegistrationWorker.enqueue(applicationContext)
                scheduleNext(applicationContext, append = true)
                return Result.success()
            }

            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                scheduleNext(applicationContext, append = true)
                Result.success()
            }
        }
    }

    companion object {
        private const val MAX_RETRIES = 3
        private const val UNIQUE_LOOP = "mdm-installed-apps-loop"
        private const val UNIQUE_NOW = "mdm-installed-apps-now"
        private const val LOOP_DELAY_HOURS = 1L

        fun enqueueImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<InstalledAppsReportWorker>()
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_NOW, ExistingWorkPolicy.KEEP, request)
        }

        fun scheduleNext(context: Context, append: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<InstalledAppsReportWorker>()
                .setConstraints(networkConstraints())
                .setInitialDelay(LOOP_DELAY_HOURS, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_LOOP,
                if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }

        private fun networkConstraints(): Constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
    }
}
