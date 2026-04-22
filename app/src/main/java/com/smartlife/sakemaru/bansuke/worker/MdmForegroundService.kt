package com.smartlife.sakemaru.bansuke.worker

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import com.smartlife.sakemaru.bansuke.ui.MainActivity
import com.smartlife.sakemaru.bansuke.BansukeApplication
import com.smartlife.sakemaru.bansuke.R
import com.smartlife.sakemaru.bansuke.command.CommandDispatcher
import com.smartlife.sakemaru.bansuke.command.DeviceLockCommandHandler
import com.smartlife.sakemaru.bansuke.config.DeviceConfigStore
import com.smartlife.sakemaru.bansuke.device.DeviceRegistrationRepository
import com.smartlife.sakemaru.bansuke.diagnostics.MdmLog
import com.smartlife.sakemaru.bansuke.network.MdmApiClient
import com.smartlife.sakemaru.bansuke.network.MdmApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MdmForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            MdmLog.warn("startForeground failed (permissions not ready): ${e.message}")
            stopSelf()
            return
        }
        acquireWakeLock()
        startHeartbeatLoop()
        startCommandSyncLoop()
        MdmLog.info("MDM foreground service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        serviceScope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        MdmLog.info("MDM foreground service stopped")
        super.onDestroy()
    }

    private fun startHeartbeatLoop() {
        serviceScope.launch {
            while (isActive) {
                runCatching {
                    val status = if (DeviceLockCommandHandler.isLocked(applicationContext)) "locked" else "active"
                    DeviceRegistrationRepository(DeviceConfigStore(applicationContext)).heartbeat(status)
                }.onFailure { throwable ->
                    MdmLog.warn("Heartbeat failed: ${throwable.message}", throwable)
                    if (throwable is MdmApiException && throwable.statusCode == 401) {
                        DeviceRegistrationWorker.enqueue(applicationContext)
                    }
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun startCommandSyncLoop() {
        serviceScope.launch {
            while (isActive) {
                runCatching {
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
                    }
                }.onFailure { throwable ->
                    MdmLog.warn("Command sync failed: ${throwable.message}", throwable)
                    if (throwable is MdmApiException && throwable.statusCode == 401) {
                        DeviceConfigStore(applicationContext).resetRegistration()
                        DeviceRegistrationWorker.enqueue(applicationContext)
                    }
                }
                delay(COMMAND_SYNC_INTERVAL_MS)
            }
        }
    }

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, BansukeApplication.DEFAULT_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("端末管理サービス実行中")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "bansuke:mdm-service",
        ).apply { acquire() }
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val HEARTBEAT_INTERVAL_MS = 60_000L
        private const val COMMAND_SYNC_INTERVAL_MS = 300_000L

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, MdmForegroundService::class.java)
            )
        }
    }
}
