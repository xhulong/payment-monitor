package com.example.paymentmonitor.monitor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object MonitorRuntimeState {
    private val _listenerConnected = MutableStateFlow(false)
    val listenerConnected = _listenerConnected.asStateFlow()

    private val _foregroundRunning = MutableStateFlow(false)
    val foregroundRunning = _foregroundRunning.asStateFlow()

    private val _lastListenerTestAt = MutableStateFlow<Long?>(null)
    val lastListenerTestAt = _lastListenerTestAt.asStateFlow()

    private val _lastNotificationAt = MutableStateFlow<Long?>(null)
    val lastNotificationAt = _lastNotificationAt.asStateFlow()

    fun setListenerConnected(connected: Boolean) {
        _listenerConnected.value = connected
    }

    fun setForegroundRunning(running: Boolean) {
        _foregroundRunning.value = running
    }

    fun markListenerTestCaptured(timestamp: Long = System.currentTimeMillis()) {
        _lastListenerTestAt.value = timestamp
    }

    fun markNotificationReceived(timestamp: Long = System.currentTimeMillis()) {
        _lastNotificationAt.value = timestamp
    }
}
