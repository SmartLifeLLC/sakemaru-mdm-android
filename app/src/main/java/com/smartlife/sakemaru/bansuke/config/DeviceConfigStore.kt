package com.smartlife.sakemaru.bansuke.config

import android.content.Context
import android.os.PersistableBundle
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartlife.sakemaru.bansuke.BuildConfig
import com.smartlife.sakemaru.bansuke.network.dto.RegisterData
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.deviceConfigDataStore by preferencesDataStore(name = "device_config")

class DeviceConfigStore(context: Context) {
    private val appContext = context.applicationContext

    val configFlow: Flow<DeviceConfig> = appContext.deviceConfigDataStore.data.map { preferences ->
        val environment = ServerEnvironment.fromRaw(preferences[Keys.MDM_ENVIRONMENT]).name

        DeviceConfig(
            mdmEnvironment = environment,
            mdmBaseUrl = preferences[Keys.MDM_BASE_URL].orEmpty().ifBlank {
                ServerEnvironment.fromRaw(environment).baseUrl()
            },
            registrationKey = preferences[Keys.REGISTRATION_KEY].orEmpty(),
            deviceCode = preferences[Keys.DEVICE_CODE].orEmpty(),
            deviceName = preferences[Keys.DEVICE_NAME].orEmpty(),
            deviceAccessToken = preferences[Keys.DEVICE_ACCESS_TOKEN].orEmpty(),
            fcmToken = preferences[Keys.FCM_TOKEN].orEmpty(),
            registeredDeviceId = preferences[Keys.REGISTERED_DEVICE_ID],
        )
    }

    suspend fun read(): DeviceConfig = configFlow.first()

    suspend fun ensureRegistrationKey(): String {
        val current = read().registrationKey
        if (current.isNotBlank()) {
            return current
        }

        val generated = "reg-${UUID.randomUUID()}"
        appContext.deviceConfigDataStore.edit { preferences ->
            preferences[Keys.REGISTRATION_KEY] = generated
        }

        return generated
    }

    suspend fun saveProvisioningConfig(
        mdmEnvironment: String,
        deviceName: String = "",
        mdmBaseUrlOverride: String = "",
    ) {
        val environment = ServerEnvironment.fromRaw(mdmEnvironment)
        val resolvedBaseUrl = mdmBaseUrlOverride.ifBlank { environment.baseUrl() }

        appContext.deviceConfigDataStore.edit { preferences ->
            preferences[Keys.MDM_ENVIRONMENT] = environment.name
            preferences[Keys.MDM_BASE_URL] = resolvedBaseUrl.ifBlank { BuildConfig.DEFAULT_MDM_BASE_URL }
            preferences[Keys.DEVICE_NAME] = deviceName.trim()
            if (preferences[Keys.REGISTRATION_KEY].isNullOrBlank()) {
                preferences[Keys.REGISTRATION_KEY] = "reg-${UUID.randomUUID()}"
            }
        }
    }

    suspend fun saveProvisioningExtras(extras: PersistableBundle) {
        val baseUrl = extras.getString(EXTRA_MDM_BASE_URL).orEmpty()
        val environment = extras.getString(EXTRA_MDM_ENVIRONMENT, BuildConfig.DEFAULT_MDM_ENVIRONMENT).orEmpty()
        val deviceName = extras.getString(EXTRA_DEVICE_NAME).orEmpty()

        saveProvisioningConfig(
            mdmEnvironment = environment,
            deviceName = deviceName,
            mdmBaseUrlOverride = baseUrl,
        )
    }

    suspend fun saveRegistration(data: RegisterData) {
        appContext.deviceConfigDataStore.edit { preferences ->
            preferences[Keys.REGISTERED_DEVICE_ID] = data.id
            preferences[Keys.DEVICE_ACCESS_TOKEN] = data.deviceAccessToken
            preferences[Keys.DEVICE_CODE] = data.deviceCode
            preferences[Keys.DEVICE_NAME] = data.name.orEmpty()
        }
    }

    suspend fun saveFcmToken(token: String) {
        appContext.deviceConfigDataStore.edit { preferences ->
            preferences[Keys.FCM_TOKEN] = token
        }
    }

    suspend fun resetRegistration() {
        appContext.deviceConfigDataStore.edit { preferences ->
            preferences.remove(Keys.DEVICE_ACCESS_TOKEN)
            preferences.remove(Keys.REGISTERED_DEVICE_ID)
        }
    }

    private object Keys {
        val MDM_ENVIRONMENT = stringPreferencesKey("mdm_environment")
        val MDM_BASE_URL = stringPreferencesKey("mdm_base_url")
        val REGISTRATION_KEY = stringPreferencesKey("registration_key")
        val DEVICE_CODE = stringPreferencesKey("device_code")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val DEVICE_ACCESS_TOKEN = stringPreferencesKey("device_access_token")
        val FCM_TOKEN = stringPreferencesKey("fcm_token")
        val REGISTERED_DEVICE_ID = intPreferencesKey("registered_device_id")
    }

    companion object {
        const val EXTRA_MDM_ENVIRONMENT = "mdm_environment"
        const val EXTRA_MDM_BASE_URL = "mdm_base_url"
        const val EXTRA_DEVICE_NAME = "device_name"
    }
}
