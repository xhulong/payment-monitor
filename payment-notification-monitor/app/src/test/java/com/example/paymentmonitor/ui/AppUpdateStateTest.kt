package com.example.paymentmonitor.ui

import com.example.paymentmonitor.BuildConfig
import com.example.paymentmonitor.sync.AppReleaseData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class AppUpdateStateTest {
    @Test
    fun currentVersionIsNotReportedAsAnUpdate() {
        val state = AppUpdateState(
            latest = AppReleaseData(
                versionCode = BuildConfig.VERSION_CODE,
                versionName = BuildConfig.VERSION_NAME,
            ),
        )

        assertFalse(state.updateAvailable)
        assertFalse(state.required)
    }

    @Test
    fun newerVersionIsAvailableButNotRequiredBeforeEnforcement() {
        val state = AppUpdateState(
            latest = release(
                minSupported = BuildConfig.VERSION_CODE + 1,
                enforcementAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).toString(),
            ),
        )

        assertTrue(state.updateAvailable)
        assertFalse(state.required)
    }

    @Test
    fun outdatedVersionIsRequiredAfterEnforcement() {
        val state = AppUpdateState(
            latest = release(
                minSupported = BuildConfig.VERSION_CODE + 1,
                enforcementAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1).toString(),
            ),
        )

        assertTrue(state.required)
    }

    private fun release(minSupported: Int, enforcementAt: String) = AppReleaseData(
        versionCode = BuildConfig.VERSION_CODE + 1,
        versionName = "next",
        minSupportedVersionCode = minSupported,
        enforcementAt = enforcementAt,
    )
}
