package com.example.paymentmonitor.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.paymentmonitor.model.ParseStatus
import com.example.paymentmonitor.model.PaymentDirection
import com.example.paymentmonitor.model.PaymentEvent
import com.example.paymentmonitor.model.PaymentPlatform
import com.example.paymentmonitor.model.RawNotificationData
import com.example.paymentmonitor.model.UploadStatus
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

@Entity(
    tableName = "payment_events",
    indices = [
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["clientEventId"], unique = true),
        Index(value = ["deviceSequence"], unique = true),
        Index(value = ["receivedAt"]),
        Index(value = ["uploadStatus", "nextAttemptAt", "receivedAt"]),
    ],
)
data class PaymentEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceSequence: Long? = null,
    val fingerprint: String,
    val clientEventId: String,
    val parserVersion: String,
    val notificationKeyHash: String?,
    val rawHash: String?,
    val uploadStatus: String,
    val attemptCount: Int,
    val nextAttemptAt: Long?,
    val lastAttemptAt: Long?,
    val uploadedAt: Long?,
    val lastErrorCode: String?,
    val lastErrorMessage: String?,
    val platform: String,
    val direction: String,
    val parseStatus: String,
    val amount: String?,
    val currency: String,
    val matchedRule: String,
    val receivedAt: Long,
    val sourcePackage: String,
    val notificationId: Int,
    val notificationTag: String?,
    val notificationKey: String?,
    val postTime: Long,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val textLines: String,
    val ticker: String?,
    val subText: String?,
    val infoText: String?,
    val summaryText: String?,
    val extrasText: String,
)

data class UploadStatusCount(
    val uploadStatus: String,
    val count: Int,
)

fun PaymentEvent.toEntity(): PaymentEventEntity {
    val rawJson = raw.toCanonicalJson()
    return PaymentEventEntity(
        fingerprint = fingerprint,
        deviceSequence = deviceSequence,
        clientEventId = clientEventId.ifBlank { UUID.randomUUID().toString() },
        parserVersion = parserVersion.ifBlank { CURRENT_PARSER_VERSION },
        notificationKeyHash = notificationKeyHash
            ?: raw.notificationKey?.let(::sha256Hex),
        rawHash = rawHash ?: sha256Hex(rawJson),
        uploadStatus = uploadStatus.name,
        attemptCount = attemptCount,
        nextAttemptAt = nextAttemptAt,
        lastAttemptAt = lastAttemptAt,
        uploadedAt = uploadedAt,
        lastErrorCode = lastErrorCode,
        lastErrorMessage = lastErrorMessage,
        platform = platform.name,
        direction = direction.name,
        parseStatus = parseStatus.name,
        amount = amount?.toPlainString(),
        currency = currency,
        matchedRule = matchedRule,
        receivedAt = receivedAt,
        sourcePackage = raw.packageName,
        notificationId = raw.notificationId,
        notificationTag = raw.notificationTag,
        notificationKey = raw.notificationKey,
        postTime = raw.postTime,
        title = raw.title,
        text = raw.text,
        bigText = raw.bigText,
        textLines = JSONArray(raw.textLines).toString(),
        ticker = raw.ticker,
        subText = raw.subText,
        infoText = raw.infoText,
        summaryText = raw.summaryText,
        extrasText = JSONObject().apply {
            raw.extras.toSortedMap().forEach { (key, value) -> put(key, value) }
        }.toString(),
    )
}

fun PaymentEventEntity.toModel(): PaymentEvent = PaymentEvent(
    fingerprint = fingerprint,
    platform = PaymentPlatform.valueOf(platform),
    direction = PaymentDirection.valueOf(direction),
    parseStatus = ParseStatus.valueOf(parseStatus),
    amount = amount?.let(::BigDecimal),
    currency = currency,
    matchedRule = matchedRule,
    receivedAt = receivedAt,
    raw = toRawNotification(),
    deviceSequence = deviceSequence,
    clientEventId = clientEventId,
    parserVersion = parserVersion,
    notificationKeyHash = notificationKeyHash,
    rawHash = rawHash,
    uploadStatus = UploadStatus.valueOf(uploadStatus),
    attemptCount = attemptCount,
    nextAttemptAt = nextAttemptAt,
    lastAttemptAt = lastAttemptAt,
    uploadedAt = uploadedAt,
    lastErrorCode = lastErrorCode,
    lastErrorMessage = lastErrorMessage,
)

fun PaymentEventEntity.toRawNotification(): RawNotificationData = RawNotificationData(
    packageName = sourcePackage,
    notificationId = notificationId,
    notificationTag = notificationTag,
    notificationKey = notificationKey,
    postTime = postTime,
    title = title,
    text = text,
    bigText = bigText,
    textLines = runCatching {
        val array = JSONArray(textLines)
        (0 until array.length()).map { index -> array.optString(index) }
    }.getOrElse { textLines.lines().filter(String::isNotEmpty) },
    ticker = ticker,
    subText = subText,
    infoText = infoText,
    summaryText = summaryText,
    extras = runCatching {
        val json = JSONObject(extrasText)
        json.keys().asSequence().associateWith { key -> json.optString(key) }
    }.getOrElse { emptyMap() },
)

fun RawNotificationData.toCanonicalJson(): String = JSONObject().apply {
    put("packageName", packageName)
    put("notificationId", notificationId)
    put("notificationTag", notificationTag)
    put("notificationKey", notificationKey)
    put("postTime", postTime)
    put("title", title)
    put("text", text)
    put("bigText", bigText)
    put("textLines", JSONArray(textLines))
    put("ticker", ticker)
    put("subText", subText)
    put("infoText", infoText)
    put("summaryText", summaryText)
    put("extras", JSONObject().apply {
        extras.toSortedMap().forEach { (key, value) -> put(key, value) }
    })
}.toString()

fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }

const val CURRENT_PARSER_VERSION = "3"
