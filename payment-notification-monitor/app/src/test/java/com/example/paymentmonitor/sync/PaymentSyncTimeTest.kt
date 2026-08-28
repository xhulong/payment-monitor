package com.example.paymentmonitor.sync

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentSyncTimeTest {

    @Test
    fun formatsUtcTimestampWithExactlyThreeMillisecondDigits() {
        val epochMillis = Instant.parse("2026-07-16T06:30:00.123Z").toEpochMilli()

        assertEquals(
            "2026-07-16T06:30:00.123Z",
            epochMillisToUtcIso(epochMillis),
        )
    }

    @Test
    fun keepsZeroMillisecondsInsteadOfDroppingFraction() {
        val epochMillis = Instant.parse("2026-07-16T06:30:00Z").toEpochMilli()

        assertEquals(
            "2026-07-16T06:30:00.000Z",
            epochMillisToUtcIso(epochMillis),
        )
    }
}
