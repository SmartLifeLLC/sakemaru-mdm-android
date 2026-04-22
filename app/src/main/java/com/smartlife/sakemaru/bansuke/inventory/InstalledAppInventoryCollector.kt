package com.smartlife.sakemaru.bansuke.inventory

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.smartlife.sakemaru.bansuke.network.dto.InstalledAppDto

class InstalledAppInventoryCollector(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    fun collect(): List<InstalledAppDto> =
        installedPackages()
            .filter { it.applicationInfo?.isSystemApp() != true }
            .mapNotNull { packageInfo -> packageInfo.toInstalledAppDto() }
            .sortedBy { it.packageName }

    private fun installedPackages(): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(0)
        }

    private fun PackageInfo.toInstalledAppDto(): InstalledAppDto? {
        val packageName = packageName?.takeIf { it.isNotBlank() } ?: return null
        val applicationInfo = applicationInfo

        return InstalledAppDto(
            packageName = packageName,
            appName = applicationInfo?.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() },
            versionCode = longVersionCodeCompat(),
            versionName = versionName?.takeIf { it.isNotBlank() },
            isSystem = applicationInfo?.isSystemApp() ?: false,
        )
    }

    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        }

    private fun ApplicationInfo.isSystemApp(): Boolean =
        flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
            flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
}
