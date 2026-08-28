package com.example.paymentmonitor.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.paymentmonitor.PaymentMonitorApplication
import com.example.paymentmonitor.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiPreferencesTest {
    private val app =
        ApplicationProvider.getApplicationContext<PaymentMonitorApplication>()
    private val preferences = UiPreferences(app)

    @After
    fun restoreSystemMode() = runBlocking {
        preferences.setThemeMode(ThemeMode.SYSTEM)
        preferences.setAutoUpdateEnabled(true)
    }

    @Test
    fun themeModePersistsAcrossPreferenceInstances() = runBlocking {
        preferences.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, UiPreferences(app).themeModeFlow.first())

        preferences.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, UiPreferences(app).themeModeFlow.first())
    }

    @Test
    fun autoUpdatePreferenceDefaultsToEnabledAndPersists() = runBlocking {
        preferences.setAutoUpdateEnabled(true)
        assertTrue(UiPreferences(app).autoUpdateEnabledFlow.first())

        preferences.setAutoUpdateEnabled(false)
        assertFalse(UiPreferences(app).autoUpdateEnabledFlow.first())
    }
}
