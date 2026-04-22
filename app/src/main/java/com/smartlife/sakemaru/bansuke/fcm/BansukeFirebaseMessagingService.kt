package com.smartlife.sakemaru.bansuke.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.smartlife.sakemaru.bansuke.config.DeviceConfigStore
import com.smartlife.sakemaru.bansuke.diagnostics.MdmLog
import com.smartlife.sakemaru.bansuke.device.DeviceRegistrationRepository
import com.smartlife.sakemaru.bansuke.network.MdmApiException
import com.smartlife.sakemaru.bansuke.worker.MdmWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BansukeFirebaseMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        MdmLog.info(
            "FCM received: messageId=${message.messageId ?: "-"}, " +
                "from=${message.from ?: "-"}, dataKeys=${message.data.keys.sorted()}"
        )
        MdmWorkScheduler.enqueueImmediateSync(applicationContext)
    }

    override fun onNewToken(token: String) {
        MdmLog.info("FCM token refreshed: length=${token.length}")
        serviceScope.launch {
            runCatching {
                DeviceRegistrationRepository(DeviceConfigStore(applicationContext)).registerFcmToken(token)
            }.onSuccess {
                MdmLog.info("FCM token saved and registration attempted")
            }.onFailure { throwable ->
                MdmLog.warn("FCM token registration failed: ${throwable.message}", throwable)
                if (throwable is MdmApiException && throwable.statusCode == 401) {
                    MdmWorkScheduler.enqueueRegistration(applicationContext)
                }
            }
        }
    }
}
