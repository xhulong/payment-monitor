package com.example.paymentmonitor.capture

import android.app.Notification
import android.content.Context
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.paymentmonitor.PaymentMonitorApplication
import com.example.paymentmonitor.model.RawNotificationData
import com.example.paymentmonitor.monitor.PaymentNotificationParser
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationCaptureTest {
    @Test
    fun debugCaptureWritesUnmatchedTargetNotificationToPrivateJsonl() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<PaymentMonitorApplication>()
        val controller = app.notificationCaptureController
        controller.start("instrumentation", "WECHAT_NEGATIVE")

        val raw = RawNotificationData(
            packageName = PaymentNotificationParser.WECHAT_PACKAGE,
            notificationId = 99,
            notificationTag = "capture-test",
            notificationKey = "capture-test-key",
            postTime = 1_700_000_000_000,
            title = "联系人",
            text = "普通聊天通知",
            bigText = null,
            textLines = emptyList(),
            ticker = null,
            subText = null,
            infoText = null,
            summaryText = null,
            extras = emptyMap(),
        )
        controller.capture(
            statusBarNotification = statusBarNotification(app, raw),
            raw = raw,
            event = null,
        )
        controller.stop()

        val state = controller.state.value
        val file = File(app.filesDir, "phase-c-captures/${state.fileName}")
        assertTrue(file.exists())
        val content = file.readText()
        assertTrue(content.contains("\"matched\":false"))
        assertTrue(content.contains("\"scenario\":\"WECHAT_NEGATIVE\""))
        assertFalse(controller.state.value.enabled)
    }

    @Suppress("DEPRECATION")
    private fun statusBarNotification(
        context: Context,
        raw: RawNotificationData,
    ): StatusBarNotification {
        val notification = Notification.Builder(context)
            .setContentTitle(raw.title)
            .setContentText(raw.text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        return StatusBarNotification(
            raw.packageName,
            raw.packageName,
            raw.notificationId,
            raw.notificationTag,
            Process.myUid(),
            Process.myPid(),
            0,
            notification,
            Process.myUserHandle(),
            raw.postTime,
        )
    }
}
