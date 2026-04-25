package com.smartlife.sakemaru.bansuke.device

import android.annotation.SuppressLint
import android.os.Build

object DeviceIdentifier {
    @SuppressLint("HardwareIds")
    fun serial(): String = try {
        Build.getSerial()
    } catch (_: SecurityException) {
        ""
    }
}
