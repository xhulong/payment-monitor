package com.example.paymentmonitor.model

import java.math.BigDecimal

enum class PaymentPlatform {
    WECHAT,
    ALIPAY,
}

enum class PaymentDirection {
    INCOME,
    EXPENSE,
    UNKNOWN,
}

enum class ParseStatus {
    PARSED,
    AMOUNT_NOT_FOUND,
    AMBIGUOUS,
}

enum class UploadStatus {
    PENDING,
    UPLOADING,
    RETRYING,
    UPLOADED,
    REJECTED,
}

data class RawNotificationData(
    val packageName: String,
    val notificationId: Int,
    val notificationTag: String?,
    val notificationKey: String?,
    val postTime: Long,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val textLines: List<String>,
    val ticker: String?,
    val subText: String?,
    val infoText: String?,
    val summaryText: String?,
    val extras: Map<String, String>,
) {
    fun combinedText(): String = buildList {
        add(title)
        add(text)
        add(bigText)
        addAll(textLines)
        add(ticker)
        add(subText)
        add(infoText)
        add(summaryText)
    }
        .filterNotNull()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .joinToString("\n")
}

data class PaymentEvent(
    val fingerprint: String,
    val platform: PaymentPlatform,
    val direction: PaymentDirection,
    val parseStatus: ParseStatus,
    val amount: BigDecimal?,
    val currency: String,
    val matchedRule: String,
    val receivedAt: Long,
    val raw: RawNotificationData,
    val deviceSequence: Long? = null,
    val clientEventId: String = "",
    val parserVersion: String = "1",
    val notificationKeyHash: String? = null,
    val rawHash: String? = null,
    val uploadStatus: UploadStatus = UploadStatus.PENDING,
    val attemptCount: Int = 0,
    val nextAttemptAt: Long? = null,
    val lastAttemptAt: Long? = null,
    val uploadedAt: Long? = null,
    val lastErrorCode: String? = null,
    val lastErrorMessage: String? = null,
)
