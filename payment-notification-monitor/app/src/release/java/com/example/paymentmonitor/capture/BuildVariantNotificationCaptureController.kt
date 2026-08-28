package com.example.paymentmonitor.capture

import android.content.Context
import android.service.notification.StatusBarNotification
import com.example.paymentmonitor.model.PaymentEvent
import com.example.paymentmonitor.model.RawNotificationData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BuildVariantNotificationCaptureController(
    @Suppress("UNUSED_PARAMETER") context: Context,
) : NotificationCaptureController {
    private val stateFlow = MutableStateFlow(NotificationCaptureState())

    override val state: StateFlow<NotificationCaptureState> = stateFlow

    override fun start(sessionId: String, scenario: String) = Unit

    override fun stop() = Unit

    override suspend fun capture(
        statusBarNotification: StatusBarNotification,
        raw: RawNotificationData,
        event: PaymentEvent?,
        parseError: Throwable?,
    ) = Unit
}
