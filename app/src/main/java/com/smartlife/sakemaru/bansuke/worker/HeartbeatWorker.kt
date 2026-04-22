package com.smartlife.sakemaru.bansuke.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.smartlife.sakemaru.bansuke.command.DeviceLockCommandHandler
import com.smartlife.sakemaru.bansuke.config.DeviceConfigStore
import com.smartlife.sakemaru.bansuke.device.DeviceRegistrationRepository
import com.smartlife.sakemaru.bansuke.network.MdmApiException

class HeartbeatWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            val status = if (DeviceLockCommandHandler.isLocked(applicationContext)) "locked" else "active"
            DeviceRegistrationRepository(DeviceConfigStore(applicationContext)).heartbeat(status)
            Result.success()
        } catch (throwable: Throwable) {
            if (throwable is MdmApiException && throwable.statusCode == 401) {
                DeviceRegistrationWorker.enqueue(applicationContext)
                return Result.success()
            }
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val MAX_RETRIES = 3
        private const val UNIQUE_NOW = "mdm-heartbeat-now"

        fun enqueueImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<HeartbeatWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_NOW, ExistingWorkPolicy.KEEP, request)
        }
    }
}
