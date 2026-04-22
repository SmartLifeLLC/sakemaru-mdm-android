package com.smartlife.sakemaru.bansuke.update

import java.io.File
import java.security.MessageDigest

object Sha256 {
    fun hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    fun matches(file: File, expectedHex: String): Boolean {
        val normalized = expectedHex.trim().lowercase()
        return normalized.isNotEmpty() && hex(file) == normalized
    }
}

