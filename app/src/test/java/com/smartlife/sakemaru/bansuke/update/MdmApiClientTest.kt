package com.smartlife.sakemaru.bansuke.update

import com.smartlife.sakemaru.bansuke.network.MdmApiClient
import com.smartlife.sakemaru.bansuke.network.dto.ApiEnvelope
import com.smartlife.sakemaru.bansuke.network.dto.DeviceCommandDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MdmApiClientTest {
    @Test
    fun normalizesServerRootUrlToApiBase() {
        assertEquals(
            "https://mdm.sakemaru.test/api/",
            MdmApiClient.normalizeApiBaseUrl("https://mdm.sakemaru.test").toString(),
        )
    }

    @Test
    fun keepsApiBaseUrlWhenAlreadyProvided() {
        assertEquals(
            "https://mdm.sakemaru.test/api/",
            MdmApiClient.normalizeApiBaseUrl("https://mdm.sakemaru.test/api/").toString(),
        )
    }

    @Test
    fun parsesCommandsWhenPayloadShapeVaries() {
        val response = """
            {
              "data": [
                {
                  "id": 1,
                  "device_id": 1,
                  "type": "app_update",
                  "payload": { "app_name": "handy" },
                  "status": "pending"
                },
                {
                  "id": 2,
                  "device_id": 1,
                  "type": "app_update",
                  "payload": [],
                  "status": "sent"
                }
              ]
            }
        """.trimIndent()

        val envelope = MdmApiClient.defaultJson.decodeFromString<ApiEnvelope<List<DeviceCommandDto>>>(response)

        assertTrue(envelope.data[0].payload is JsonObject)
        assertTrue(envelope.data[1].payload is JsonArray)
    }
}
