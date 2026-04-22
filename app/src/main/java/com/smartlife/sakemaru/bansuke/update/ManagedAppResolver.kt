package com.smartlife.sakemaru.bansuke.update

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object ManagedAppResolver {
    fun packageNameHint(appName: String, payload: JsonObject?): String? {
        val payloadPackageName = payload
            ?.get("package_name")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
        if (payloadPackageName != null) return payloadPackageName

        return when (appName) {
            "handy" -> "com.smartlife.handy"
            "delivery" -> "ai.sakemaru.delivery.handy"
            else -> null
        }
    }
}

