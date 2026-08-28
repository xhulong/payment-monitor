package com.example.paymentmonitor.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class AppReleaseSecurityTest {

    @Test
    fun normalizesCertificateDigest() {
        assertEquals(
            "aabbccdd",
            normalizeSha256("AA:BB CC:DD"),
        )
    }
}
