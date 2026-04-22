package com.smartlife.sakemaru.bansuke.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat

class AppVersionReader(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    fun installedVersionCode(packageName: String): Int {
        val info = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return 0
        }
        return PackageInfoCompat.getLongVersionCode(info).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}

