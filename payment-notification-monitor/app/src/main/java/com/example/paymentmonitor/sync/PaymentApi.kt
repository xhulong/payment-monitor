package com.example.paymentmonitor.sync

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okio.Buffer
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class DeviceApiEnvelope<T>(
    val ok: Boolean = false,
    val data: T? = null,
    val error: DeviceApiErrorData? = null,
    val serverTime: String? = null,
    val protocolVersion: Int = 1,
)

data class DeviceApiErrorData(
    val code: String,
    val message: String?,
    val retryable: Boolean = false,
    val rePairRequired: Boolean = false,
    val retryAfterSeconds: Long? = null,
)

data class PublicApiResponse<T>(
    val code: Int = 500,
    val msg: String? = null,
    val data: T? = null,
)

data class AppReleaseData(
    val id: Long? = null,
    val platform: String = "ANDROID",
    val versionCode: Int = 0,
    val versionName: String = "",
    val minSupportedVersionCode: Int = 0,
    val enforcementAt: String? = null,
    val downloadUrl: String? = null,
    val fileSize: Long = 0,
    val sha256: String = "",
    val signingCertificateSha256: String = "",
    val verifiedPackageName: String? = null,
    val verificationStatus: String? = null,
    val updateMode: String = "REQUIRED",
    val releaseNotes: String? = null,
    val status: String = "",
    val publishedAt: String? = null,
)

data class PairDeviceRequest(
    val protocolVersion: Int = 1,
    val previousDeviceId: Long? = null,
    val pairingCode: String,
    val deviceName: String,
    val androidIdHash: String?,
    val appVersion: String,
    val appVersionCode: Int,
    val parserVersion: String,
)

data class PairDeviceData(
    val deviceId: Long,
    val deviceSecret: String,
    val credentialVersion: Int,
    val heartbeatIntervalSeconds: Int,
    val onlineThresholdSeconds: Int,
    val maxBatchSize: Int,
    val maxRequestBytes: Int,
    val rawPayloadUploadEnabled: Boolean,
    val merchantCode: String? = null,
    val merchantName: String? = null,
    val deviceRole: String? = null,
    val platformScope: String? = null,
    val minSupportedVersionCode: Int? = null,
    val enforcementAt: String? = null,
    val downloadUrl: String? = null,
    val updateMode: String? = null,
) {
    fun toRuntimeConfig() = DeviceRuntimeConfig(
        heartbeatIntervalSeconds = heartbeatIntervalSeconds,
        onlineThresholdSeconds = onlineThresholdSeconds,
        maxBatchSize = maxBatchSize,
        maxRequestBytes = maxRequestBytes,
        rawPayloadUploadEnabled = rawPayloadUploadEnabled,
    )
}

data class DeviceConfigData(
    val heartbeatIntervalSeconds: Int,
    val onlineThresholdSeconds: Int,
    val maxBatchSize: Int,
    val maxRequestBytes: Int,
    val rawPayloadUploadEnabled: Boolean,
    val deviceRole: String? = null,
    val platformScope: String? = null,
    val minSupportedVersionCode: Int? = null,
    val enforcementAt: String? = null,
    val downloadUrl: String? = null,
    val updateMode: String? = null,
) {
    fun toRuntimeConfig() = DeviceRuntimeConfig(
        heartbeatIntervalSeconds = heartbeatIntervalSeconds,
        onlineThresholdSeconds = onlineThresholdSeconds,
        maxBatchSize = maxBatchSize,
        maxRequestBytes = maxRequestBytes,
        rawPayloadUploadEnabled = rawPayloadUploadEnabled,
    )
}

data class HeartbeatRequest(
    val appVersion: String,
    val appVersionCode: Int,
    val parserVersion: String,
    val pendingCount: Int = 0,
    val retryingCount: Int = 0,
    val rejectedCount: Int = 0,
    val lastSyncAt: String? = null,
    val monitoringEnabled: Boolean = false,
    val listenerConnected: Boolean = false,
    val foregroundRunning: Boolean = false,
    val notificationAccessGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val lastNotificationAt: String? = null,
    val healthIssue: String? = null,
)

data class PaymentEventBatchRequest(
    val sentAt: String,
    val events: List<PaymentEventItemData>,
)

data class PaymentEventItemData(
    val clientEventId: String,
    val deviceSequence: Long?,
    val platform: String,
    val direction: String,
    val amountMinor: Long?,
    val currency: String,
    val eventTime: String?,
    val eventTimeMs: Long,
    val clientReceivedAt: String,
    val clientReceivedAtMs: Long,
    val parseStatus: String,
    val parserVersion: String,
    val matchedRule: String,
    val fingerprint: String,
    val notificationKeyHash: String?,
    val rawHash: String,
    val rawPayload: JsonObject?,
)

data class PaymentEventBatchData(
    val accepted: List<String> = emptyList(),
    val duplicates: List<String> = emptyList(),
    val rejected: List<RejectedEventData> = emptyList(),
)

data class RejectedEventData(
    val clientEventId: String,
    val code: String,
    val message: String?,
)

interface PaymentDeviceApi {
    @GET("api/v1/public/app-releases/latest?platform=ANDROID")
    suspend fun latestRelease(): Response<PublicApiResponse<AppReleaseData>>

    @POST("api/v1/devices/pair")
    suspend fun pair(@Body request: PairDeviceRequest): Response<DeviceApiEnvelope<PairDeviceData>>

    @GET("api/v1/device/config")
    suspend fun config(): Response<DeviceApiEnvelope<DeviceConfigData>>

    @POST("api/v1/device/heartbeat")
    suspend fun heartbeat(
        @Body request: HeartbeatRequest,
    ): Response<DeviceApiEnvelope<DeviceConfigData>>

    @POST("api/v1/payment-events/batch")
    suspend fun upload(
        @Body request: PaymentEventBatchRequest,
    ): Response<DeviceApiEnvelope<PaymentEventBatchData>>
}

class HmacSigningInterceptor(
    private val stateStore: DeviceStateStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val credentials = stateStore.snapshot().credentials
            ?: throw IllegalStateException("设备尚未配对")
        val original = chain.request()
        val bodyBytes = original.body?.let { body ->
            Buffer().use { buffer ->
                body.writeTo(buffer)
                buffer.readByteArray()
            }
        } ?: ByteArray(0)
        val timestamp = System.currentTimeMillis() / 1000 + credentials.clockOffsetSeconds
        val nonce = UUID.randomUUID().toString()
        val canonical = listOf(
            original.method.uppercase(),
            original.url.encodedPath,
            timestamp.toString(),
            nonce,
            sha256Hex(bodyBytes),
        ).joinToString("\n")
        val signature = hmacSha256Hex(credentials.deviceSecret, canonical)
        val signed = original.newBuilder()
            .header("X-Device-Id", credentials.deviceId.toString())
            .header("X-Credential-Version", credentials.credentialVersion.toString())
            .header("X-Timestamp", timestamp.toString())
            .header("X-Nonce", nonce)
            .header("X-Signature", signature)
            .build()
        return chain.proceed(signed)
    }
}

class PaymentApiFactory(
    private val stateStore: DeviceStateStore,
) {
    private val gson = Gson()

    fun create(serverUrl: String, authenticated: Boolean): PaymentDeviceApi {
        val clientBuilder = OkHttpClient.Builder()
        if (authenticated) clientBuilder.addInterceptor(HmacSigningInterceptor(stateStore))
        return Retrofit.Builder()
            .baseUrl(serverUrl.trimEnd('/') + "/")
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(PaymentDeviceApi::class.java)
    }

    fun parseError(body: ResponseBody?): DeviceApiEnvelope<JsonObject>? =
        runCatching {
            gson.fromJson(body?.string(), DeviceApiEnvelope::class.java)
                as DeviceApiEnvelope<JsonObject>
        }.getOrNull()

    fun toJson(value: Any): String = gson.toJson(value)
}

class ClientApiException(
    val httpStatus: Int,
    val code: String,
    override val message: String?,
    val retryable: Boolean,
    val rePairRequired: Boolean,
    val retryAfterSeconds: Long?,
    val serverTime: String?,
) : Exception(message)

private fun sha256Hex(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(value)
    .joinToString("") { byte -> "%02x".format(byte) }

private fun hmacSha256Hex(secret: String, value: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
    return mac.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
