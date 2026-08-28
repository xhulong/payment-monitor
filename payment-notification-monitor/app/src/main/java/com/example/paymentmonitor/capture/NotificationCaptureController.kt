package com.example.paymentmonitor.capture

import android.service.notification.StatusBarNotification
import com.example.paymentmonitor.model.PaymentEvent
import com.example.paymentmonitor.model.RawNotificationData
import kotlinx.coroutines.flow.StateFlow

data class NotificationCaptureState(
    val enabled: Boolean = false,
    val sessionId: String? = null,
    val scenario: String? = null,
    val fileName: String? = null,
    val recordCount: Int = 0,
    val byteCount: Long = 0,
    val lastError: String? = null,
)

interface NotificationCaptureController {
    val state: StateFlow<NotificationCaptureState>

    fun start(sessionId: String, scenario: String)

    fun stop()

    suspend fun capture(
        statusBarNotification: StatusBarNotification,
        raw: RawNotificationData,
        event: PaymentEvent?,
        parseError: Throwable? = null,
    )
}
