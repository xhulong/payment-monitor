package com.example.paymentmonitor.ui

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.paymentmonitor.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.uiDataStore by preferencesDataStore(name = "ui_preferences")

class UiPreferences(
    private val context: Context,
) {
    val themeModeFlow: Flow<ThemeMode> = context.uiDataStore.data.map { preferences ->
        ThemeMode.fromStorage(preferences[THEME_MODE])
    }

    val autoUpdateEnabledFlow: Flow<Boolean> = context.uiDataStore.data.map { preferences ->
        preferences[AUTO_UPDATE_ENABLED] ?: true
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.uiDataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.storageValue
        }
    }

    suspend fun setAutoUpdateEnabled(enabled: Boolean) {
        context.uiDataStore.edit { preferences ->
            preferences[AUTO_UPDATE_ENABLED] = enabled
        }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
    }
}
