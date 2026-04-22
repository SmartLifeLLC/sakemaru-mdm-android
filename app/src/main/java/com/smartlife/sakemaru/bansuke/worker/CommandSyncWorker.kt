package com.smartlife.sakemaru.bansuke.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.smartlife.sakemaru.bansuke.command.CommandDispatcher
import com.smartlife.sakemaru.bansuke.config.DeviceConfigStore
import com.smartlife.sakemaru.bansuke.diagnostics.MdmLog
import com.smartlife.sakemaru.bansuke.device.DeviceRegistrationRepository
import com.smartlife.sakemaru.bansuke.network.MdmApiClient
import com.smartlife.sakemaru.bansuke.network.MdmApiException
import java.util.concurrent.TimeUnit

class CommandSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            val store = DeviceConfigStore(applicationContext)
            val config = store.read()
            if (config.isRegistered) {
                MdmLog.info("Command sync started: device=${config.deviceCode}")
                val repository = DeviceRegistrationRepository(store)
                val commands = MdmApiClient(config.mdmBaseUrl).commands(
                    authorization = config.bearerAuthorization(),
                    deviceCode = config.deviceCode,
                    registrationKey = config.registrationKey,
                )
                MdmLog.info("Command sync fetched ${commands.size} command(s)")
                val dispatcher = CommandDispatcher(applicationContext, repository)
                commands.forEach { command ->
                    MdmLog.info("Dispatching command: id=${command.id}, type=${command.type}, status=${command.status ?: "-"}")
                    dispatcher.dispatch(config, command)
                }
            } else {
                MdmLog.info("Command sync skipped: device is not registered")
            }
            scheduleNext(applicationContext, append = true)
            Result.success()
        } catch (throwable: Throwable) {
            MdmLog.warn("Command sync failed: ${throwable.message}", throwable)
            if (throwable is MdmApiException && throwable.statusCode == 401) {
                DeviceConfigStore(applicationContext).resetRegistration()
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
        private const val UNIQUE_LOOP = "mdm-command-sync-loop"
        private const val UNIQUE_NOW = "mdm-command-sync-now"
        private const val LOOP_DELAY_MINUTES = 5L

        fun enqueueImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<CommandSyncWorker>()
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_NOW, ExistingWorkPolicy.KEEP, request)
        }

        fun scheduleNext(context: Context, append: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<CommandSyncWorker>()
                .setConstraints(networkConstraints())
                .setInitialDelay(LOOP_DELAY_MINUTES, TimeUnit.MINUTES)
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
