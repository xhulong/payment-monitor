package com.example.paymentmonitor.sync

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DeviceApiEnvelopeTest {

    @Test
    fun missingPairingDataProducesActionableProtocolError() {
        val envelope = DeviceApiEnvelope<String>(
            ok = true,
            data = null,
            serverTime = null,
        )

        try {
            envelope.requireData("设备配对")
            fail("Expected ClientApiException")
        } catch (exception: ClientApiException) {
            assertEquals("INVALID_RESPONSE", exception.code)
            assertEquals(
                "服务端响应缺少设备配对数据，请确认服务端与应用版本匹配",
                exception.message,
            )
        }
    }

    @Test
    fun pairingRequestAlwaysContainsMandatoryVersionCode() {
        val request = PairDeviceRequest(
            pairingCode = "12345678",
            deviceName = "test-device",
            androidIdHash = null,
            appVersion = "1.7.0-rc1",
            appVersionCode = 8,
            parserVersion = "3",
        )

        val json = Gson().toJson(request)

        assertTrue(json.contains("\"appVersionCode\":8"))
    }
}
