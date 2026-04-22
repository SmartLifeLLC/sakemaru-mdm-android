package com.smartlife.sakemaru.bansuke.fcm

import com.smartlife.sakemaru.bansuke.BuildConfig
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FcmTokenProvider {
    suspend fun currentToken(): String {
        check(BuildConfig.FIREBASE_CONFIGURED) {
            "Firebase config missing: place app/google-services.json and reinstall the debug APK"
        }

        return suspendCancellableCoroutine { continuation ->
            try {
                FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        if (!continuation.isActive) return@addOnCompleteListener

                        if (!task.isSuccessful) {
                            continuation.resumeWithException(
                                task.exception ?: IllegalStateException("Firebase token task failed")
                            )
                            return@addOnCompleteListener
                        }

                        val token = task.result?.takeIf { it.isNotBlank() }
                        if (token == null) {
                            continuation.resumeWithException(IllegalStateException("Firebase returned an empty token"))
                        } else {
                            continuation.resume(token)
                        }
                    }
            } catch (throwable: Throwable) {
                if (continuation.isActive) {
                    continuation.resumeWithException(throwable)
                }
            }
        }
    }

    suspend fun currentTokenOrNull(): String? =
        runCatching { currentToken() }.getOrNull()
}
