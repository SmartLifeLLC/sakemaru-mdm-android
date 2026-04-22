package com.smartlife.sakemaru.bansuke.config

import com.smartlife.sakemaru.bansuke.BuildConfig

enum class ServerEnvironment(
    val label: String,
) {
    LOCAL("LOCAL"),
    TEST("TEST"),
    PROD("PROD");

    fun baseUrl(): String =
        when (this) {
            LOCAL -> BuildConfig.MDM_URL_LOCAL
            TEST -> BuildConfig.MDM_URL_TEST
            PROD -> BuildConfig.MDM_URL_PROD
        }

    companion object {
        fun fromRaw(value: String?): ServerEnvironment =
            entries.firstOrNull { it.name.equals(value.orEmpty(), ignoreCase = true) } ?: default()

        fun default(): ServerEnvironment = fromRaw(BuildConfig.DEFAULT_MDM_ENVIRONMENT)
    }
}
