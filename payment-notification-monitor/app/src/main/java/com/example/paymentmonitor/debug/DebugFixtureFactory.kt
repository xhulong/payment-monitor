package com.example.paymentmonitor.debug

import com.example.paymentmonitor.model.RawNotificationData
import com.example.paymentmonitor.monitor.PaymentNotificationParser

enum class DebugFixtureType(
    val displayName: String,
) {
    WECHAT_INCOME("微信收款"),
    WECHAT_EXPENSE("微信付款"),
    ALIPAY_INCOME("支付宝收款"),
    ALIPAY_EXPENSE("支付宝付款"),
}

object DebugFixtureFactory {
    fun create(
        type: DebugFixtureType,
        timestamp: Long = System.currentTimeMillis(),
    ): RawNotificationData {
        val fixture = when (type) {
            DebugFixtureType.WECHAT_INCOME -> FixtureContent(
                packageName = PaymentNotificationParser.WECHAT_PACKAGE,
                title = "微信收款商业版",
                text = "收款88.66元，已存入经营账户",
                bigText = "微信收款商业版成功收款88.66元",
            )

            DebugFixtureType.WECHAT_EXPENSE -> FixtureContent(
                packageName = PaymentNotificationParser.WECHAT_PACKAGE,
                title = "微信支付",
                text = "向商家付款成功￥12.30",
                bigText = "微信支付：已支付12.30元",
            )

            DebugFixtureType.ALIPAY_INCOME -> FixtureContent(
                packageName = PaymentNotificationParser.ALIPAY_PACKAGE,
                title = "成功收款66.00元",
                text = "已转入余额",
                bigText = "支付宝成功收款66.00元，款项已转入余额",
            )

            DebugFixtureType.ALIPAY_EXPENSE -> FixtureContent(
                packageName = PaymentNotificationParser.ALIPAY_PACKAGE,
                title = "支付宝",
                text = "支付成功，付款20.00元",
                bigText = "本次消费成功，支出20.00元",
            )
        }
        return RawNotificationData(
            packageName = fixture.packageName,
            notificationId = type.ordinal + 1,
            notificationTag = "debug-fixture",
            notificationKey = "debug-${type.name}-$timestamp",
            postTime = timestamp,
            title = fixture.title,
            text = fixture.text,
            bigText = fixture.bigText,
            textLines = listOf(fixture.text),
            ticker = fixture.title,
            subText = "Debug 测试样本",
            infoText = null,
            summaryText = fixture.bigText,
            extras = mapOf(
                "fixtureType" to type.name,
                "android.title" to fixture.title,
                "android.text" to fixture.text,
                "android.bigText" to fixture.bigText,
            ),
        )
    }

    private data class FixtureContent(
        val packageName: String,
        val title: String,
        val text: String,
        val bigText: String,
    )
}

