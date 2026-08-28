package com.example.paymentmonitor.monitor

import com.example.paymentmonitor.model.ParseStatus
import com.example.paymentmonitor.model.PaymentDirection
import com.example.paymentmonitor.model.PaymentPlatform
import com.example.paymentmonitor.model.RawNotificationData
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentNotificationParserTest {
    @Test
    fun parsesWechatCashierIncomeFromBigText() {
        val event = PaymentNotificationParser.parse(
            raw(
                packageName = PaymentNotificationParser.WECHAT_PACKAGE,
                title = "微信收款商业版",
                text = "查看收款详情",
                bigText = "微信收款商业版成功收款88.66元",
            ),
            receivedAt = 10,
        )

        assertNotNull(event)
        assertEquals(PaymentPlatform.WECHAT, event?.platform)
        assertEquals(PaymentDirection.INCOME, event?.direction)
        assertEquals(BigDecimal("88.66"), event?.amount)
        assertEquals(ParseStatus.PARSED, event?.parseStatus)
        assertEquals("3", event?.parserVersion)
    }

    @Test
    fun parsesWechatExpenseAndPrefersMoneyContextOverOrderNumber() {
        val event = PaymentNotificationParser.parse(
            raw(
                packageName = PaymentNotificationParser.WECHAT_PACKAGE,
                title = "微信支付",
                text = "订单号202607160001，向商家付款成功￥12.30",
            ),
        )

        assertEquals(PaymentDirection.EXPENSE, event?.direction)
        assertEquals(BigDecimal("12.30"), event?.amount)
    }

    @Test
    fun parsesAlipayReferenceIncomeRule() {
        val event = PaymentNotificationParser.parse(
            raw(
                packageName = PaymentNotificationParser.ALIPAY_PACKAGE,
                title = "成功收款66.00元",
                text = "已转入余额",
            ),
        )

        assertEquals(PaymentPlatform.ALIPAY, event?.platform)
        assertEquals(PaymentDirection.INCOME, event?.direction)
        assertEquals(BigDecimal("66.00"), event?.amount)
    }

    @Test
    fun parsesAlipayExpense() {
        val event = PaymentNotificationParser.parse(
            raw(
                packageName = PaymentNotificationParser.ALIPAY_PACKAGE,
                title = "支付宝",
                text = "支付成功，付款20元",
            ),
        )

        assertEquals(PaymentDirection.EXPENSE, event?.direction)
        assertEquals(BigDecimal("20.00"), event?.amount)
    }

    @Test
    fun keepsCandidateWhenAmountIsMissing() {
        val event = PaymentNotificationParser.parse(
            raw(
                packageName = PaymentNotificationParser.ALIPAY_PACKAGE,
                title = "店员通",
                text = "支付宝成功收款，点击查看详情",
            ),
        )

        assertNotNull(event)
        assertNull(event?.amount)
        assertEquals(ParseStatus.AMOUNT_NOT_FOUND, event?.parseStatus)
    }

    @Test
    fun marksDirectionAmbiguousWhenIncomeAndExpenseRulesTie() {
        val event = PaymentNotificationParser.parse(
            raw(
                packageName = PaymentNotificationParser.ALIPAY_PACKAGE,
                title = "支付宝",
                text = "成功收款后支付成功10.00元",
            ),
        )

        assertEquals(PaymentDirection.UNKNOWN, event?.direction)
        assertEquals(ParseStatus.AMBIGUOUS, event?.parseStatus)
    }

    @Test
    fun merchantIncomeEvidenceWinsOverGenericPaymentWords() {
        val event = PaymentNotificationParser.parse(
            raw(
                packageName = PaymentNotificationParser.WECHAT_PACKAGE,
                title = "微信收款助手",
                text = "店员收款成功，顾客已支付0.88元，已存入经营账户",
            ),
        )

        assertEquals(PaymentDirection.INCOME, event?.direction)
        assertEquals(BigDecimal("0.88"), event?.amount)
    }

    @Test
    fun ignoresLongOrderNumberAndClockWhenExtractingAmount() {
        val event = PaymentNotificationParser.parse(
            raw(
                packageName = PaymentNotificationParser.ALIPAY_PACKAGE,
                title = "成功收款",
                text = "订单号202607161234567890，时间12:30，成功收款￥6.66",
            ),
        )

        assertEquals(BigDecimal("6.66"), event?.amount)
    }

    @Test
    fun alipayMerchantTitleCanKeepAmountMissingCandidate() {
        val event = PaymentNotificationParser.parse(
            raw(
                packageName = PaymentNotificationParser.ALIPAY_PACKAGE,
                title = "店员通收款到账",
                text = "点击查看收款详情",
            ),
        )

        assertEquals(PaymentDirection.INCOME, event?.direction)
        assertEquals(ParseStatus.AMOUNT_NOT_FOUND, event?.parseStatus)
    }

    @Test
    fun ignoresOrdinaryWechatChatNotification() {
        val event = PaymentNotificationParser.parse(
            raw(
                packageName = PaymentNotificationParser.WECHAT_PACKAGE,
                title = "张三",
                text = "晚上一起吃饭吗？",
            ),
        )

        assertNull(event)
    }

    @Test
    fun ignoresWechatRefundArrivalNotification() {
        val event = PaymentNotificationParser.parse(
            raw(
                packageName = PaymentNotificationParser.WECHAT_PACKAGE,
                title = "微信退款通知",
                text = "退款已到账8.88元",
            ),
        )

        assertNull(event)
    }

    @Test
    fun ignoresOtherPackages() {
        val event = PaymentNotificationParser.parse(
            raw(
                packageName = "com.example.other",
                title = "成功收款99元",
                text = "已转入余额",
            ),
        )

        assertNull(event)
    }

    @Test
    fun fingerprintIsStableForSameNotificationAndChangesWithContent() {
        val firstRaw = raw(
            packageName = PaymentNotificationParser.WECHAT_PACKAGE,
            title = "微信支付",
            text = "支付成功10元",
        )
        val first = PaymentNotificationParser.parse(firstRaw, receivedAt = 1)
        val second = PaymentNotificationParser.parse(firstRaw, receivedAt = 2)
        val changed = PaymentNotificationParser.parse(
            firstRaw.copy(text = "支付成功11元"),
            receivedAt = 2,
        )

        assertEquals(first?.fingerprint, second?.fingerprint)
        assertNotEquals(first?.fingerprint, changed?.fingerprint)
    }

    private fun raw(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String? = null,
    ): RawNotificationData = RawNotificationData(
        packageName = packageName,
        notificationId = 1,
        notificationTag = "test",
        notificationKey = "test-key",
        postTime = 1_700_000_000_000,
        title = title,
        text = text,
        bigText = bigText,
        textLines = emptyList(),
        ticker = null,
        subText = null,
        infoText = null,
        summaryText = null,
        extras = emptyMap(),
    )
}
