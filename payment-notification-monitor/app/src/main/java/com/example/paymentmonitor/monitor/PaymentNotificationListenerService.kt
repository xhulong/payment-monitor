package com.example.paymentmonitor.monitor

import android.app.Notification
import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.paymentmonitor.PaymentMonitorApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class PaymentNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val applicationContainer: PaymentMonitorApplication
        get() = application as PaymentMonitorApplication

    override fun onListenerConnected() {
        super.onListenerConnected()
        MonitorRuntimeState.setListenerConnected(true)
    }

    override fun onListenerDisconnected() {
        MonitorRuntimeState.setListenerConnected(false)
        super.onListenerDisconnected()
        serviceScope.launch {
            delay(REBIND_DELAY_MS)
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                applicationContainer.monitoringPreferences.isEnabled()
            ) {
                requestRebind(
                    ComponentName(
                        this@PaymentNotificationListenerService,
                        PaymentNotificationListenerService::class.java,
                    ),
                )
            }
        }
    }

    override fun onNotificationPosted(statusBarNotification: StatusBarNotification?) {
        val sbn = statusBarNotification ?: return
        val notification = sbn.notification ?: return

        if (
            sbn.packageName == packageName &&
            notification.extras?.getBoolean(TEST_NOTIFICATION_EXTRA, false) == true
        ) {
            MonitorRuntimeState.markListenerTestCaptured()
            runCatching { cancelNotification(sbn.key) }
            return
        }

        if (
            sbn.packageName != PaymentNotificationParser.WECHAT_PACKAGE &&
            sbn.packageName != PaymentNotificationParser.ALIPAY_PACKAGE
        ) {
            return
        }

        serviceScope.launch {
            val raw = NotificationDataMapper.fromStatusBarNotification(sbn) ?: return@launch
            MonitorRuntimeState.markNotificationReceived(raw.postTime)
            applicationContainer.monitoringPreferences.markNotificationReceived(raw.postTime)
            var parseError: Throwable? = null
            val event = runCatching {
                PaymentNotificationParser.parse(raw)
            }.onFailure {
                parseError = it
            }.getOrNull()
            applicationContainer.notificationCaptureController.capture(
                statusBarNotification = sbn,
                raw = raw,
                event = event,
                parseError = parseError,
            )
            if (!applicationContainer.monitoringPreferences.isEnabled()) return@launch
            if (event == null) return@launch
            applicationContainer.repository.save(event)
        }
    }

    override fun onDestroy() {
        MonitorRuntimeState.setListenerConnected(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val TEST_NOTIFICATION_EXTRA = "payment_monitor_listener_test"
        private const val REBIND_DELAY_MS = 5_000L
    }
}
