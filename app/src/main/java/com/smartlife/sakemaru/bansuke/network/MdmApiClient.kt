package com.smartlife.sakemaru.bansuke.network

import android.util.Log
import com.smartlife.sakemaru.bansuke.network.dto.ApiEnvelope
import com.smartlife.sakemaru.bansuke.network.dto.AppUpdateCheckData
import com.smartlife.sakemaru.bansuke.network.dto.AppUpdateCheckRequest
import com.smartlife.sakemaru.bansuke.network.dto.CommandResultRequest
import com.smartlife.sakemaru.bansuke.network.dto.DeviceCommandDto
import com.smartlife.sakemaru.bansuke.network.dto.FcmTokenData
import com.smartlife.sakemaru.bansuke.network.dto.FcmTokenRequest
import com.smartlife.sakemaru.bansuke.network.dto.HeartbeatRequest
import com.smartlife.sakemaru.bansuke.network.dto.InstalledAppsReportData
import com.smartlife.sakemaru.bansuke.network.dto.InstalledAppsReportRequest
import com.smartlife.sakemaru.bansuke.network.dto.RegisterData
import com.smartlife.sakemaru.bansuke.network.dto.RegisterRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl

class MdmApiClient(
    private val serverBaseUrl: String,
    private val httpClient: OkHttpClient = defaultHttpClient,
    private val json: Json = defaultJson,
) {
    private val apiBaseUrl: HttpUrl = normalizeApiBaseUrl(serverBaseUrl)

    suspend fun healthCheck(): String {
        val request = Request.Builder()
            .url(endpoint("health"))
            .get()
            .build()
        return execute(request)
    }

    suspend fun register(request: RegisterRequest): RegisterData {
        val body = execute(post(endpoint("device", "register"), requestBody(request)))
        return json.decodeFromString<ApiEnvelope<RegisterData>>(body).data
    }

    suspend fun registerFcmToken(authorization: String, request: FcmTokenRequest): FcmTokenData {
        val body = execute(post(endpoint("device", "token"), requestBody(request), authorization))
        return json.decodeFromString<ApiEnvelope<FcmTokenData>>(body).data
    }

    suspend fun heartbeat(authorization: String, request: HeartbeatRequest) {
        execute(post(endpoint("device", "heartbeat"), requestBody(request), authorization))
    }

    suspend fun reportInstalledApps(
        authorization: String,
        request: InstalledAppsReportRequest,
    ): InstalledAppsReportData {
        val body = execute(post(endpoint("device", "apps"), requestBody(request), authorization))
        return json.decodeFromString<ApiEnvelope<InstalledAppsReportData>>(body).data
    }

    suspend fun commands(
        authorization: String,
        deviceCode: String,
        registrationKey: String? = null,
    ): List<DeviceCommandDto> {
        val url = endpoint("device", "commands")
            .newBuilder()
            .addQueryParameter("device_code", deviceCode)
            .apply {
                if (!registrationKey.isNullOrBlank()) {
                    addQueryParameter("registration_key", registrationKey)
                }
            }
            .build()
        val body = execute(get(url, authorization))
        return json.decodeFromString<ApiEnvelope<List<DeviceCommandDto>>>(body).data
    }

    suspend fun commandResult(authorization: String, request: CommandResultRequest) {
        execute(post(endpoint("device", "command", "result"), requestBody(request), authorization))
    }

    suspend fun appUpdateCheck(
        authorization: String,
        request: AppUpdateCheckRequest,
    ): AppUpdateCheckData {
        val body = execute(post(endpoint("device", "app", "update-check"), requestBody(request), authorization))
        return json.decodeFromString<ApiEnvelope<AppUpdateCheckData>>(body).data
    }

    private fun endpoint(vararg pathSegments: String): HttpUrl {
        val builder = apiBaseUrl.newBuilder()
        pathSegments.forEach { builder.addPathSegment(it) }
        return builder.build()
    }

    private inline fun <reified T> requestBody(value: T) =
        json.encodeToString(value).toRequestBody(JSON_MEDIA_TYPE)

    private fun get(url: HttpUrl, authorization: String): Request =
        Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", authorization)
            .get()
            .build()

    private fun post(url: HttpUrl, body: okhttp3.RequestBody, authorization: String? = null): Request {
        val builder = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .post(body)
        if (authorization != null) {
            builder.addHeader("Authorization", authorization)
        }
        return builder.build()
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        // リクエストURLをデバッグ出力
        Log.d("Bansuke", "Sending request: ${request.method} ${request.url}")

        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                Log.e("Bansuke", "Request failed: ${response.code} ${response.message}")
                throw MdmApiException(response.code, body.take(MAX_ERROR_BODY_LENGTH))
            }
            body
        }
    }

    companion object {
        private const val MAX_ERROR_BODY_LENGTH = 512
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        val defaultJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

        val defaultHttpClient: OkHttpClient = OkHttpClient.Builder().build()

        fun normalizeApiBaseUrl(serverBaseUrl: String): HttpUrl {
            val trimmed = serverBaseUrl.trim().trimEnd('/')
            // すでに /api で終わっている場合はそのまま、そうでなければ追加
            val apiBase = if (trimmed.endsWith("/api")) trimmed else "$trimmed/api"
            return "$apiBase/".toHttpUrl()
        }
    }
}

class MdmApiException(
    val statusCode: Int,
    responseBody: String,
) : RuntimeException("MDM API request failed with HTTP $statusCode: $responseBody")
