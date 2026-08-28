package com.example.paymentmonitor.monitor

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.monitoringDataStore by preferencesDataStore(name = "monitoring")

class MonitoringPreferences(
    private val context: Context,
) {
    val enabledFlow: Flow<Boolean> = context.monitoringDataStore.data.map { preferences ->
        preferences[ENABLED] == true
    }

    val lastNotificationAtFlow: Flow<Long?> = context.monitoringDataStore.data.map { preferences ->
        preferences[LAST_NOTIFICATION_AT]
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.monitoringDataStore.edit { preferences ->
            preferences[ENABLED] = enabled
        }
    }

    suspend fun isEnabled(): Boolean = enabledFlow.first()

    suspend fun markNotificationReceived(timestamp: Long = System.currentTimeMillis()) {
        context.monitoringDataStore.edit { preferences ->
            preferences[LAST_NOTIFICATION_AT] = timestamp
        }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val LAST_NOTIFICATION_AT = longPreferencesKey("last_notification_at")
    }
}
