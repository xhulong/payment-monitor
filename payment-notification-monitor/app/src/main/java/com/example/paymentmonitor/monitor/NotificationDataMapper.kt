package com.example.paymentmonitor.monitor

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.example.paymentmonitor.model.RawNotificationData
import java.lang.reflect.Array as ReflectArray

object NotificationDataMapper {
    fun fromStatusBarNotification(
        statusBarNotification: StatusBarNotification,
    ): RawNotificationData? {
        val notification = statusBarNotification.notification ?: return null
        val extras = notification.extras ?: Bundle.EMPTY
        return RawNotificationData(
            packageName = statusBarNotification.packageName,
            notificationId = statusBarNotification.id,
            notificationTag = statusBarNotification.tag,
            notificationKey = statusBarNotification.key,
            postTime = statusBarNotification.postTime,
            title = extras.charSequence(Notification.EXTRA_TITLE),
            text = extras.charSequence(Notification.EXTRA_TEXT),
            bigText = extras.charSequence(Notification.EXTRA_BIG_TEXT),
            textLines = extras.textLines(Notification.EXTRA_TEXT_LINES),
            ticker = notification.tickerText?.toString(),
            subText = extras.charSequence(Notification.EXTRA_SUB_TEXT),
            infoText = extras.charSequence(Notification.EXTRA_INFO_TEXT),
            summaryText = extras.charSequence(Notification.EXTRA_SUMMARY_TEXT),
            extras = extras.keySet()
                .sorted()
                .associateWith { key -> safeStringify(runCatching { extras.get(key) }.getOrNull()) },
        )
    }

    private fun Bundle.charSequence(key: String): String? =
        runCatching { getCharSequence(key)?.toString() }.getOrNull()

    private fun Bundle.textLines(key: String): List<String> {
        val value = runCatching { get(key) }.getOrNull() ?: return emptyList()
        return when (value) {
            is kotlin.Array<*> -> value.mapNotNull { it?.toString() }
            is Collection<*> -> value.mapNotNull { it?.toString() }
            else -> listOf(value.toString())
        }
    }

    private fun safeStringify(value: Any?, depth: Int = 0): String {
        if (value == null) return "null"
        if (depth >= MAX_DEPTH) return "<${value.javaClass.name}>"
        return runCatching {
            when {
                value is CharSequence -> value.toString()
                value is Bundle -> value.keySet()
                    .sorted()
                    .joinToString(prefix = "{", postfix = "}") { key ->
                        "$key=${safeStringify(value.get(key), depth + 1)}"
                    }

                value is Collection<*> -> value.joinToString(
                    prefix = "[",
                    postfix = "]",
                ) { item -> safeStringify(item, depth + 1) }

                value.javaClass.isArray -> {
                    val length = ReflectArray.getLength(value)
                    (0 until length).joinToString(prefix = "[", postfix = "]") { index ->
                        safeStringify(ReflectArray.get(value, index), depth + 1)
                    }
                }

                else -> value.toString()
            }
        }.getOrElse { error -> "<${value.javaClass.name}: ${error.javaClass.simpleName}>" }
    }

    private const val MAX_DEPTH = 6
}
