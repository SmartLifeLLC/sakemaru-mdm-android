package com.smartlife.sakemaru.bansuke.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.smartlife.sakemaru.bansuke.BuildConfig
import com.smartlife.sakemaru.bansuke.R
import com.smartlife.sakemaru.bansuke.config.DeviceConfig
import com.smartlife.sakemaru.bansuke.config.DeviceConfigStore
import com.smartlife.sakemaru.bansuke.config.ServerEnvironment
import com.smartlife.sakemaru.bansuke.device.DeviceRegistrationRepository
import com.smartlife.sakemaru.bansuke.fcm.FcmTokenProvider
import com.smartlife.sakemaru.bansuke.location.LocationSnapshotProvider
import com.smartlife.sakemaru.bansuke.network.MdmApiException
import com.smartlife.sakemaru.bansuke.provisioning.ManagedDevicePermissionGranter
import com.smartlife.sakemaru.bansuke.worker.MdmWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var configStore: DeviceConfigStore
    private lateinit var statusText: TextView
    private lateinit var environmentSpinner: Spinner
    private lateinit var deviceNameInput: EditText

    companion object {
        private const val REQUEST_LOCATION_PERMISSIONS = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configStore = DeviceConfigStore(applicationContext)
        buildContentView()
    }

    override fun onResume() {
        super.onResume()
        val granter = ManagedDevicePermissionGranter(applicationContext)
        granter.grantAllManagedPermissions()
        granter.blockUninstall()
        requestForegroundLocationPermissionIfNeeded()
        refreshStatus()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSIONS) {
            refreshStatus("Location permission updated")
        }
    }

    private fun buildContentView() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        })

        root.addView(TextView(this).apply {
            text = "Deploy: ${BuildConfig.DEPLOY_CUSTOMER_DISPLAY_NAME}"
            textSize = 14f
            setPadding(0, dp(8), 0, 0)
        })

        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(16), 0, dp(16))
        }
        root.addView(statusText)

        root.addView(TextView(this).apply {
            text = "API Environment"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })

        environmentSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                ServerEnvironment.entries.map { it.label },
            ).also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        root.addView(environmentSpinner)

        deviceNameInput = editText("device_name (optional)", InputType.TYPE_CLASS_TEXT)
        root.addView(deviceNameInput)

        root.addView(button("Save config") { saveConfig() })
        root.addView(button("Refresh FCM token") { refreshFcmToken() })
        root.addView(button("Register device") { registerDevice() })
        root.addView(button("Send heartbeat") { sendHeartbeat() })
        root.addView(button("Sync commands") { syncCommands() })
        root.addView(button("Report installed apps") { reportInstalledApps() })
        root.addView(button("Reset registration") { confirmResetRegistration() })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun editText(hintText: String, inputTypeValue: Int): EditText =
        EditText(this).apply {
            hint = hintText
            inputType = inputTypeValue
            setSingleLine(true)
        }

    private fun button(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }

    private fun saveConfig() {
        scope.launch {
            runAction("Saving config") {
                configStore.saveProvisioningConfig(
                    mdmEnvironment = selectedEnvironment().name,
                    deviceName = deviceNameInput.text.toString(),
                )
                MdmWorkScheduler.scheduleLoops(applicationContext)
                MdmWorkScheduler.enqueueRegistration(applicationContext)
            }
        }
    }

    private fun registerDevice() {
        scope.launch {
            runAction("Registering device") {
                val repository = DeviceRegistrationRepository(configStore)
                val fcmToken = runCatching { FcmTokenProvider().currentTokenOrNull() }.getOrNull()
                if (!fcmToken.isNullOrBlank()) {
                    try {
                        repository.registerFcmToken(fcmToken)
                    } catch (exception: MdmApiException) {
                        if (exception.statusCode != 401) throw exception
                    }
                }
                repository.registerDevice()
                MdmWorkScheduler.enqueueImmediateSync(applicationContext)
            }
        }
    }

    private fun refreshFcmToken() {
        scope.launch {
            runAction("Refreshing FCM token") {
                val token = FcmTokenProvider().currentToken()
                DeviceRegistrationRepository(configStore).registerFcmToken(token)
            }
        }
    }

    private fun sendHeartbeat() {
        scope.launch {
            runAction("Sending heartbeat") {
                DeviceRegistrationRepository(configStore).heartbeat()
            }
        }
    }

    private fun syncCommands() {
        MdmWorkScheduler.enqueueImmediateSync(applicationContext)
        refreshStatus("Command sync queued")
    }

    private fun reportInstalledApps() {
        MdmWorkScheduler.enqueueInstalledAppsReport(applicationContext)
        refreshStatus("Installed app report queued")
    }

    private fun confirmResetRegistration() {
        AlertDialog.Builder(this)
            .setTitle("Reset registration")
            .setMessage("Clear the saved device access token and re-register this device. Environment, registration key, FCM token, and assigned device code are kept.")
            .setPositiveButton("Reset") { _, _ -> resetRegistration() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetRegistration() {
        scope.launch {
            runAction("Resetting registration") {
                DeviceRegistrationRepository(configStore).resetRegistration()
                MdmWorkScheduler.enqueueRegistration(applicationContext)
            }
        }
    }

    private suspend fun runAction(label: String, block: suspend () -> Unit) {
        refreshStatus("$label...")
        try {
            withContext(Dispatchers.IO) { block() }
            refreshStatus("$label done")
        } catch (throwable: Throwable) {
            refreshStatus("$label failed: ${throwable.message ?: throwable::class.java.simpleName}")
        }
    }

    private fun refreshStatus(prefix: String? = null) {
        scope.launch {
            val config = withContext(Dispatchers.IO) { configStore.read() }
            bindConfig(config, prefix)
        }
    }

    private fun bindConfig(config: DeviceConfig, prefix: String?) {
        environmentSpinner.setSelection(ServerEnvironment.fromRaw(config.mdmEnvironment).ordinal, false)
        if (deviceNameInput.text.isBlank()) {
            deviceNameInput.setText(config.deviceName)
        }

        statusText.text = buildString {
            if (!prefix.isNullOrBlank()) {
                append(prefix).append('\n')
            }
            append("Deploy: ").append(BuildConfig.DEPLOY_CUSTOMER_DISPLAY_NAME).append('\n')
            append("Provisioning: ").append(if (config.hasProvisioningConfig) "ready" else "missing").append('\n')
            append("Registered: ").append(if (config.isRegistered) "yes" else "no").append('\n')
            append("Environment: ").append(ServerEnvironment.fromRaw(config.mdmEnvironment).label).append('\n')
            append("MDM: ").append(config.mdmBaseUrl.ifBlank { BuildConfig.DEFAULT_MDM_BASE_URL }).append('\n')
            append("Device code: ").append(config.deviceCode.ifBlank { "-" }).append('\n')
            append("Name: ").append(config.displayName.ifBlank { "-" }).append('\n')
            append("Location permission: ").append(LocationSnapshotProvider.permissionStatusLabel(this@MainActivity)).append('\n')
            append("Firebase config: ").append(if (BuildConfig.FIREBASE_CONFIGURED) "ready" else "missing").append('\n')
            append("FCM token: ").append(if (config.fcmToken.isBlank()) "missing" else "saved")
        }
    }

    private fun selectedEnvironment(): ServerEnvironment {
        val selected = environmentSpinner.selectedItem?.toString()
        return ServerEnvironment.fromRaw(selected)
    }

    private fun requestForegroundLocationPermissionIfNeeded() {
        if (LocationSnapshotProvider.hasAnyLocationPermission(this)) {
            return
        }

        val permissions = buildList {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), REQUEST_LOCATION_PERMISSIONS)
        } else if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !LocationSnapshotProvider.hasBackgroundLocationPermission(this)
        ) {
            refreshStatus("Background location is not granted")
        }
    }
}
