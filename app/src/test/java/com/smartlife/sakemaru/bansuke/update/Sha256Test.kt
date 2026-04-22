package com.smartlife.sakemaru.bansuke.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Sha256Test {
    @Test
    fun computesExpectedHex() {
        val file = File.createTempFile("sha256", ".txt")
        try {
            file.writeText("sakemaru")
            val expected = "300c933ce1975a48ded58ff0cab6527edfcba44b2eac33124a415a84492c006a"
            assertEquals(expected, Sha256.hex(file))
            assertTrue(Sha256.matches(file, expected.uppercase()))
        } finally {
            file.delete()
        }
    }
}
