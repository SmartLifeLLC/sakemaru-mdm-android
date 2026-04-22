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
import com.smartlife.sakemaru.bansuke.diagnostics.MdmLog
import com.smartlife.sakemaru.bansuke.device.DeviceRegistrationRepository
import com.smartlife.sakemaru.bansuke.fcm.FcmTokenProvider
import com.smartlife.sakemaru.bansuke.network.MdmApiException

class DeviceRegistrationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            val store = DeviceConfigStore(applicationContext)
            val config = store.read()
            if (!config.hasProvisioningConfig) return Result.success()

            val repository = DeviceRegistrationRepository(store)
            val fcmToken = runCatching { FcmTokenProvider().currentTokenOrNull() }.getOrNull()
            if (!fcmToken.isNullOrBlank()) {
                try {
                    repository.registerFcmToken(fcmToken)
                } catch (exception: MdmApiException) {
                    if (exception.statusCode != 401) throw exception
                    MdmLog.warn("FCM token registration hit an invalid device token; re-registering device", exception)
                }
            }

            if (!store.read().isRegistered) {
                repository.registerDevice()
            }

            MdmWorkScheduler.enqueueImmediateSync(applicationContext)
            MdmWorkScheduler.enqueueInstalledAppsReport(applicationContext)
            Result.success()
        } catch (_: Throwable) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val MAX_RETRIES = 3
        private const val UNIQUE_WORK = "mdm-device-registration"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<DeviceRegistrationWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
        }
    }
}
