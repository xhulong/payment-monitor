package com.example.paymentmonitor.monitor

import com.example.paymentmonitor.data.CURRENT_PARSER_VERSION
import com.example.paymentmonitor.model.ParseStatus
import com.example.paymentmonitor.model.PaymentDirection
import com.example.paymentmonitor.model.PaymentEvent
import com.example.paymentmonitor.model.PaymentPlatform
import com.example.paymentmonitor.model.RawNotificationData
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min

object PaymentNotificationParser {
    const val WECHAT_PACKAGE = "com.tencent.mm"
    const val ALIPAY_PACKAGE = "com.eg.android.AlipayGphone"

    private val wechatTitleMarkers = listOf(
        "微信支付",
        "微信收款助手",
        "微信收款商业版",
        "对外收款",
        "Weixin Cashier Assistant",
    )

    private val wechatIncomeMarkers = listOf(
        "成功收款",
        "收款成功",
        "收款到账",
        "收款通知",
        "店员收款",
        "顾客付款",
        "经营账户",
        "收款",
        "到账",
        "向你付款",
        "收到付款",
        "已收钱",
    )

    private val wechatExpenseMarkers = listOf(
        "已支付",
        "支付成功",
        "付款成功",
        "向商家付款",
        "扣款成功",
        "消费成功",
    )

    private val alipayIncomeMarkers = listOf(
        "成功收款",
        "收款成功",
        "已转入余额",
        "通过扫码向你付款",
        "店员通",
        "支付宝成功收款",
        "收款到账",
        "收钱到账",
        "收钱码到账",
        "商家服务收款",
    )

    private val alipayExpenseMarkers = listOf(
        "支付成功",
        "付款成功",
        "已付款",
        "消费成功",
        "扣款成功",
        "支出",
    )

    private val amountPattern = Regex("""(?<!\d)\d{1,9}(?:\.\d{1,2})?(?!\d)""")
    private val moneyContextMarkers = listOf(
        "¥",
        "￥",
        "元",
        "人民币",
        "金额",
        "收款",
        "付款",
        "支付",
        "到账",
        "消费",
        "支出",
    )
    private val nonMoneyContextMarkers = listOf(
        "订单号",
        "交易号",
        "流水号",
        "编号",
        "尾号",
        "时间",
        "版本",
        "年",
        "月",
        "日",
        "时",
        "分",
        "秒",
    )

    fun parse(
        raw: RawNotificationData,
        receivedAt: Long = System.currentTimeMillis(),
    ): PaymentEvent? {
        val combined = raw.combinedText()
        if (combined.isBlank()) return null
        if (refundMarkers.any(combined::contains)) return null

        val classification = when (raw.packageName) {
            WECHAT_PACKAGE -> classifyWechat(raw, combined)
            ALIPAY_PACKAGE -> classifyAlipay(raw, combined)
            else -> null
        } ?: return null

        val amount = extractAmount(combined)
        val parseStatus = when {
            amount == null -> ParseStatus.AMOUNT_NOT_FOUND
            classification.direction == PaymentDirection.UNKNOWN -> ParseStatus.AMBIGUOUS
            else -> ParseStatus.PARSED
        }
        val fingerprint = createFingerprint(
            raw = raw,
            platform = classification.platform,
            direction = classification.direction,
            amount = amount,
            combined = combined,
        )

        return PaymentEvent(
            fingerprint = fingerprint,
            platform = classification.platform,
            direction = classification.direction,
            parseStatus = parseStatus,
            amount = amount,
            currency = "CNY",
            matchedRule = classification.rule,
            receivedAt = receivedAt,
            raw = raw,
            parserVersion = CURRENT_PARSER_VERSION,
        )
    }

    private fun classifyWechat(
        raw: RawNotificationData,
        combined: String,
    ): Classification? {
        val title = raw.title.orEmpty()
        val titleMatches = wechatTitleMarkers.filter { marker ->
            title.contains(marker, ignoreCase = true)
        }
        val incomeMatches = wechatIncomeMarkers.filter(combined::contains)
        val expenseMatches = wechatExpenseMarkers.filter(combined::contains)

        if (titleMatches.isEmpty() && incomeMatches.isEmpty() && expenseMatches.isEmpty()) {
            return null
        }

        val incomeScore = incomeMatches.size * 3 +
            titleMatches.count { it.contains("收款") || it == "对外收款" } * 5 +
            wechatMerchantMarkers.count(combined::contains) * 6
        val expenseScore = expenseMatches.size * 5
        val direction = if (
            incomeMatches.isNotEmpty() &&
            expenseMatches.isNotEmpty() &&
            wechatMerchantMarkers.none(combined::contains)
        ) {
            PaymentDirection.UNKNOWN
        } else {
            directionFromScores(incomeScore, expenseScore)
        }
        val rule = when (direction) {
            PaymentDirection.INCOME ->
                "wechat_income:${incomeMatches.firstOrNull() ?: titleMatches.firstOrNull() ?: "title"}"

            PaymentDirection.EXPENSE ->
                "wechat_expense:${expenseMatches.firstOrNull() ?: "expense"}"

            PaymentDirection.UNKNOWN ->
                "wechat_ambiguous:${(incomeMatches + expenseMatches + titleMatches).firstOrNull() ?: "candidate"}"
        }
        return Classification(PaymentPlatform.WECHAT, direction, rule)
    }

    private fun classifyAlipay(
        raw: RawNotificationData,
        combined: String,
    ): Classification? {
        val title = raw.title.orEmpty()
        val titleMatches = alipayIncomeTitleMarkers.filter(title::contains)
        val incomeMatches = alipayIncomeMarkers.filter(combined::contains)
        val expenseMatches = alipayExpenseMarkers.filter(combined::contains)
        if (titleMatches.isEmpty() && incomeMatches.isEmpty() && expenseMatches.isEmpty()) return null

        val merchantEvidence = alipayMerchantMarkers.filter(combined::contains)
        val direction = if (
            incomeMatches.isNotEmpty() &&
            expenseMatches.isNotEmpty() &&
            merchantEvidence.isEmpty() &&
            titleMatches.isEmpty()
        ) {
            PaymentDirection.UNKNOWN
        } else {
            directionFromScores(
                incomeScore = incomeMatches.size * 4 +
                    merchantEvidence.size * 6 +
                    titleMatches.size * 6,
                expenseScore = expenseMatches.size * 5,
            )
        }
        val rule = when (direction) {
            PaymentDirection.INCOME ->
                "alipay_income:${merchantEvidence.firstOrNull() ?: incomeMatches.firstOrNull() ?: titleMatches.firstOrNull() ?: "income"}"

            PaymentDirection.EXPENSE ->
                "alipay_expense:${expenseMatches.firstOrNull() ?: "expense"}"

            PaymentDirection.UNKNOWN ->
                "alipay_ambiguous:${(incomeMatches + expenseMatches).firstOrNull() ?: "candidate"}"
        }
        return Classification(PaymentPlatform.ALIPAY, direction, rule)
    }

    private fun directionFromScores(
        incomeScore: Int,
        expenseScore: Int,
    ): PaymentDirection = when {
        incomeScore > expenseScore -> PaymentDirection.INCOME
        expenseScore > incomeScore -> PaymentDirection.EXPENSE
        else -> PaymentDirection.UNKNOWN
    }

    private fun extractAmount(text: String): BigDecimal? {
        val candidates = amountPattern.findAll(text).mapNotNull { match ->
            val value = runCatching { BigDecimal(match.value) }.getOrNull() ?: return@mapNotNull null
            if (value < MINIMUM_AMOUNT || value > MAXIMUM_AMOUNT) return@mapNotNull null

            val contextStart = max(0, match.range.first - 16)
            val contextEnd = min(text.length, match.range.last + 17)
            val context = text.substring(contextStart, contextEnd)
            val immediateStart = max(0, match.range.first - 4)
            val immediateEnd = min(text.length, match.range.last + 5)
            val immediateContext = text.substring(immediateStart, immediateEnd)
            var score = moneyContextMarkers.count(context::contains) * 3
            score -= nonMoneyContextMarkers.count(context::contains) * 8
            if (match.value.contains('.')) score += 1
            if (
                immediateContext.contains('¥') ||
                immediateContext.contains('￥') ||
                immediateContext.contains('元')
            ) {
                score += 10
            }
            if (isTimeLike(text, match.range.first, match.range.last, value)) {
                score -= 12
            }
            if (
                value.scale() == 0 &&
                value >= BigDecimal("1900") &&
                value <= BigDecimal("2100") &&
                immediateContext.none { it == '¥' || it == '￥' || it == '元' }
            ) {
                score -= 10
            }

            AmountCandidate(
                value = value.setScale(2, RoundingMode.UNNECESSARY),
                score = score,
                index = match.range.first,
            )
        }.toList()

        val best = candidates.maxWithOrNull(
            compareBy<AmountCandidate> { it.score }
                .thenByDescending { -it.index },
        ) ?: return null
        return best.value.takeIf { best.score >= 0 }
    }

    private fun isTimeLike(
        text: String,
        start: Int,
        end: Int,
        value: BigDecimal,
    ): Boolean {
        if (value > BigDecimal("59")) return false
        val before = text.getOrNull(start - 1)
        val after = text.getOrNull(end + 1)
        return before == ':' || after == ':'
    }

    private fun createFingerprint(
        raw: RawNotificationData,
        platform: PaymentPlatform,
        direction: PaymentDirection,
        amount: BigDecimal?,
        combined: String,
    ): String {
        val source = listOf(
            raw.packageName,
            raw.notificationKey.orEmpty(),
            raw.postTime.toString(),
            platform.name,
            direction.name,
            amount?.toPlainString().orEmpty(),
            combined.trim(),
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private data class Classification(
        val platform: PaymentPlatform,
        val direction: PaymentDirection,
        val rule: String,
    )

    private data class AmountCandidate(
        val value: BigDecimal,
        val score: Int,
        val index: Int,
    )

    private val MINIMUM_AMOUNT = BigDecimal("0.01")
    private val MAXIMUM_AMOUNT = BigDecimal("999999.99")
    private val wechatMerchantMarkers = listOf(
        "微信收款助手",
        "微信收款商业版",
        "店员收款",
        "顾客付款",
        "经营账户",
        "对外收款",
    )
    private val alipayIncomeTitleMarkers = listOf(
        "成功收款",
        "收款到账",
        "收钱到账",
        "店员通",
    )
    private val alipayMerchantMarkers = listOf(
        "店员通",
        "收钱码到账",
        "商家服务收款",
        "通过扫码向你付款",
    )
    private val refundMarkers = listOf(
        "退款成功",
        "退款已到账",
        "退款到账",
        "已退款",
        "原路退回",
    )
}
