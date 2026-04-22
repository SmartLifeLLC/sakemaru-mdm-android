package com.smartlife.sakemaru.bansuke.provisioning

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.smartlife.sakemaru.bansuke.BansukeDeviceAdminReceiver
import com.smartlife.sakemaru.bansuke.diagnostics.MdmLog
import com.smartlife.sakemaru.bansuke.location.LocationSnapshotProvider

class ManagedDevicePermissionGranter(context: Context) {
    private val appContext = context.applicationContext

    fun grantLocationPermissions() {
        val devicePolicyManager = appContext.getSystemService(DevicePolicyManager::class.java) ?: return
        if (!devicePolicyManager.isDeviceOwnerApp(appContext.packageName)) {
            return
        }

        val adminComponent = ComponentName(appContext, BansukeDeviceAdminReceiver::class.java)

        LocationSnapshotProvider.managedDevicePermissions().forEach { permission ->
            runCatching {
                devicePolicyManager.setPermissionGrantState(
                    adminComponent,
                    appContext.packageName,
                    permission,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
                )
            }.onFailure { throwable ->
                MdmLog.warn("Failed to grant managed permission: $permission", throwable)
            }
        }
    }
}
