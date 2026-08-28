package com.example.paymentmonitor.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun invalidOrMissingStorageValueFallsBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("unexpected"))
    }

    @Test
    fun storageValuesAndCycleAreStable() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStorage("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage("dark"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.SYSTEM.next())
        assertEquals(ThemeMode.DARK, ThemeMode.LIGHT.next())
        assertEquals(ThemeMode.SYSTEM, ThemeMode.DARK.next())
    }

    @Test
    fun systemModeResolvesWhileExplicitModesOverrideSystem() {
        assertTrue(ThemeMode.SYSTEM.resolve(true))
        assertFalse(ThemeMode.SYSTEM.resolve(false))
        assertFalse(ThemeMode.LIGHT.resolve(true))
        assertTrue(ThemeMode.DARK.resolve(false))
    }
}
