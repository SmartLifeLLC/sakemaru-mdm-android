package com.smartlife.sakemaru.bansuke.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

class ApkDownloader(
    private val httpClient: OkHttpClient = OkHttpClient.Builder().build(),
) {
    suspend fun download(apkUrl: String, destination: File): File = withContext(Dispatchers.IO) {
        val url = apkUrl.toHttpUrlOrNull()
            ?: throw IOException("Invalid APK URL")
        if (url.scheme != "https") {
            throw IOException("APK URL must use HTTPS")
        }

        destination.parentFile?.mkdirs()
        val tempFile = File(destination.parentFile, "${destination.name}.download")
        val request = Request.Builder().url(url).get().build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("APK download failed with HTTP ${response.code}")
                }
                response.body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            if (destination.exists()) destination.delete()
            if (!tempFile.renameTo(destination)) {
                throw IOException("Failed to move downloaded APK")
            }
            destination
        } catch (throwable: Throwable) {
            tempFile.delete()
            destination.delete()
            throw throwable
        }
    }
}

