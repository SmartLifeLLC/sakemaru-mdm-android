package com.smartlife.sakemaru.bansuke.provisioning

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.UserManager
import com.smartlife.sakemaru.bansuke.BansukeDeviceAdminReceiver
import com.smartlife.sakemaru.bansuke.diagnostics.MdmLog
import com.smartlife.sakemaru.bansuke.location.LocationSnapshotProvider

class ManagedDevicePermissionGranter(context: Context) {
    private val appContext = context.applicationContext

    fun grantAllManagedPermissions() {
        val dpm = devicePolicyManager() ?: return
        val adminComponent = ComponentName(appContext, BansukeDeviceAdminReceiver::class.java)

        val permissions = LocationSnapshotProvider.managedDevicePermissions() +
            listOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_PHONE_STATE)

        permissions.forEach { permission ->
            runCatching {
                dpm.setPermissionGrantState(
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

    fun blockUninstall() {
        val dpm = devicePolicyManager() ?: return
        val adminComponent = ComponentName(appContext, BansukeDeviceAdminReceiver::class.java)
        runCatching {
            dpm.setUninstallBlocked(adminComponent, appContext.packageName, true)
        }.onFailure { throwable ->
            MdmLog.warn("Failed to block uninstall", throwable)
        }
    }

    fun applyUserRestrictions() {
        val dpm = devicePolicyManager() ?: return
        val adminComponent = ComponentName(appContext, BansukeDeviceAdminReceiver::class.java)
        val restrictions = listOf(
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_ADD_USER,
        )
        restrictions.forEach { restriction ->
            runCatching {
                dpm.addUserRestriction(adminComponent, restriction)
            }.onFailure { throwable ->
                MdmLog.warn("Failed to add user restriction: $restriction", throwable)
            }
        }
    }

    fun disablePackageVerifier() {
        val dpm = devicePolicyManager() ?: return
        val adminComponent = ComponentName(appContext, BansukeDeviceAdminReceiver::class.java)
        val settings = mapOf(
            "package_verifier_enable" to "0",
            "verifier_verify_adb_installs" to "0",
            "upload_apk_enable" to "0",
        )
        settings.forEach { (key, value) ->
            runCatching {
                dpm.setGlobalSetting(adminComponent, key, value)
            }.onFailure { throwable ->
                MdmLog.warn("Failed to set $key=$value", throwable)
            }
        }
        MdmLog.info("Package verifier disabled")
    }

    fun hideLauncherIcon() {
        val dpm = devicePolicyManager() ?: return
        val alias = ComponentName(appContext, "com.smartlife.sakemaru.bansuke.ui.LauncherAlias")
        appContext.packageManager.setComponentEnabledSetting(
            alias,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        MdmLog.info("Launcher icon hidden")
    }

    private fun devicePolicyManager(): DevicePolicyManager? {
        val dpm = appContext.getSystemService(DevicePolicyManager::class.java) ?: return null
        if (!dpm.isDeviceOwnerApp(appContext.packageName)) return null
        return dpm
    }
}
