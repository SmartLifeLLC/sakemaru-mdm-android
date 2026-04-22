package com.smartlife.sakemaru.bansuke.diagnostics

import android.util.Log

object MdmLog {
    private const val TAG = "BansukeMdm"

    fun info(message: String) {
        Log.i(TAG, message)
    }

    fun warn(message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.w(TAG, message)
        } else {
            Log.w(TAG, message, throwable)
        }
    }
}

