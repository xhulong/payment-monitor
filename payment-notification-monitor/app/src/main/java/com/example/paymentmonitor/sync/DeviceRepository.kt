package com.example.paymentmonitor.sync

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.example.paymentmonitor.BuildConfig
import com.example.paymentmonitor.data.CURRENT_PARSER_VERSION
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import com.example.paymentmonitor.monitor.MonitorRuntimeState
import retrofit2.Response

class DeviceRepository(
    private val context: Context,
    private val stateStore: DeviceStateStore,
    private val apiFactory: PaymentApiFactory,
) {
    suspend fun latestRelease(serverUrlInput: String? = null): AppReleaseData? {
        val configured = serverUrlInput
            ?: stateStore.snapshot().credentials?.serverUrl
            ?: BuildConfig.DEFAULT_SERVER_URL
        val serverUrl = ServerUrlValidator.normalize(configured)
        val response = apiFactory.create(serverUrl, authenticated = false).latestRelease()
        if (!response.isSuccessful || response.body()?.code != 200) {
            return null
        }
        return response.body()?.data
    }

    suspend fun pair(serverUrlInput: String, pairingCode: String): PairDeviceData {
        val serverUrl = ServerUrlValidator.normalize(serverUrlInput)
        val current = stateStore.snapshot()
        val request = PairDeviceRequest(
            previousDeviceId = current.credentials?.deviceId
                .takeIf { current.pairingState == PairingState.REPAIR_REQUIRED },
            pairingCode = pairingCode.trim(),
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            androidIdHash = androidIdHash(),
            appVersion = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            parserVersion = CURRENT_PARSER_VERSION,
        )
        return runCatching {
            val envelope = requireSuccess(
                apiFactory.create(serverUrl, authenticated = false).pair(request),
            )
            val data = envelope.requireData("设备配对")
            stateStore.savePairing(
                serverUrl = serverUrl,
                data = data,
                serverTime = envelope.serverTime,
                protocolVersion = envelope.protocolVersion,
            )
            data
        }.onFailure(::recordFailure).getOrThrow()
    }

    suspend fun fetchConfig(): DeviceConfigData {
        val credentials = requireCredentials()
        return runCatching {
            val envelope = requireSuccess(
                apiFactory.create(credentials.serverUrl, authenticated = true).config(),
            )
            val data = envelope.requireData("设备配置")
            stateStore.updateConfig(data, envelope.serverTime)
            data
        }.onFailure(::recordFailure).getOrThrow()
    }

    suspend fun heartbeat(): DeviceConfigData {
        val credentials = requireCredentials()
        val dao = (context.applicationContext as com.example.paymentmonitor.PaymentMonitorApplication)
            .database.paymentEventDao()
        val connectionState = stateStore.snapshot()
        val application = context.applicationContext as com.example.paymentmonitor.PaymentMonitorApplication
        val monitoringEnabled = application.monitoringPreferences.isEnabled()
        val listenerConnected = MonitorRuntimeState.listenerConnected.value
        val foregroundRunning = MonitorRuntimeState.foregroundRunning.value
        val notificationAccessGranted =
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        val batteryOptimizationIgnored =
            context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName)
                ?: false
        val lastNotificationAt = application.monitoringPreferences
            .lastNotificationAtFlow
            .first()
        val healthIssue = when {
            !monitoringEnabled -> "MONITORING_DISABLED"
            !notificationAccessGranted -> "NOTIFICATION_ACCESS_MISSING"
            !foregroundRunning -> "FOREGROUND_SERVICE_STOPPED"
            !listenerConnected -> "LISTENER_DISCONNECTED"
            !batteryOptimizationIgnored -> "BATTERY_OPTIMIZATION_ACTIVE"
            else -> null
        }
        return runCatching {
            val envelope = requireSuccess(
                apiFactory.create(credentials.serverUrl, authenticated = true)
                    .heartbeat(
                        HeartbeatRequest(
                            appVersion = BuildConfig.VERSION_NAME,
                            appVersionCode = BuildConfig.VERSION_CODE,
                            parserVersion = CURRENT_PARSER_VERSION,
                            pendingCount = dao.countPendingHeartbeat(),
                            retryingCount = dao.countRetryingHeartbeat(),
                            rejectedCount = dao.countRejectedHeartbeat(),
                            lastSyncAt = connectionState.lastSyncAt?.let(::epochMillisToUtcIso),
                            monitoringEnabled = monitoringEnabled,
                            listenerConnected = listenerConnected,
                            foregroundRunning = foregroundRunning,
                            notificationAccessGranted = notificationAccessGranted,
                            batteryOptimizationIgnored = batteryOptimizationIgnored,
                            lastNotificationAt = lastNotificationAt?.let(::epochMillisToUtcIso),
                            healthIssue = healthIssue,
                        ),
                    ),
            )
            val data = envelope.requireData("心跳配置")
            stateStore.updateConfig(data, envelope.serverTime)
            stateStore.markHeartbeat()
            data
        }.onFailure(::recordFailure).getOrThrow()
    }

    suspend fun upload(request: PaymentEventBatchRequest): PaymentEventBatchData {
        val credentials = requireCredentials()
        return runCatching {
            val envelope = requireSuccess(
                apiFactory.create(credentials.serverUrl, authenticated = true).upload(request),
            )
            stateStore.updateClock(envelope.serverTime)
            envelope.requireData("支付事件上传")
        }.onFailure(::recordFailure).getOrThrow()
    }

    fun clearPairing() = stateStore.clearPairing()

    private fun requireCredentials(): DeviceCredentials {
        val state = stateStore.snapshot()
        if (state.pairingState != PairingState.PAIRED || state.credentials == null) {
            throw IllegalStateException("设备尚未配对或需要重新配对")
        }
        return state.credentials
    }

    private fun <T> requireSuccess(response: Response<DeviceApiEnvelope<T>>): DeviceApiEnvelope<T> {
        val body = response.body()
        if (response.isSuccessful && body?.ok == true) {
            stateStore.updateClock(body.serverTime)
            return body
        }
        val errorEnvelope = body ?: apiFactory.parseError(response.errorBody())
        stateStore.updateClock(errorEnvelope?.serverTime)
        val error = errorEnvelope?.error
        throw ClientApiException(
            httpStatus = response.code(),
            code = error?.code ?: "HTTP_${response.code()}",
            message = error?.message ?: response.message(),
            retryable = error?.retryable ?: response.code().let { it == 429 || it >= 500 },
            rePairRequired = error?.rePairRequired ?: response.code() == 401,
            retryAfterSeconds = error?.retryAfterSeconds,
            serverTime = errorEnvelope?.serverTime,
        )
    }

    private fun recordFailure(throwable: Throwable) {
        if (
            throwable is ClientApiException &&
            (
                throwable.rePairRequired ||
                    throwable.httpStatus == 401 ||
                    throwable.code in REPAIR_REQUIRED_ERROR_CODES
                )
        ) {
            stateStore.markRepairRequired(throwable.code, throwable.message)
        } else {
            stateStore.markError(
                (throwable as? ClientApiException)?.code ?: "NETWORK_ERROR",
                throwable.message,
            )
        }
    }

    private fun androidIdHash(): String? {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        ) ?: return null
        return MessageDigest.getInstance("SHA-256")
            .digest(androidId.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        val REPAIR_REQUIRED_ERROR_CODES = setOf(
            "DEVICE_DISABLED",
            "CREDENTIAL_REVOKED",
        )
    }
}

internal fun <T> DeviceApiEnvelope<T>.requireData(operation: String): T {
    return data ?: throw ClientApiException(
        httpStatus = 502,
        code = "INVALID_RESPONSE",
        message = "服务端响应缺少${operation}数据，请确认服务端与应用版本匹配",
        retryable = false,
        rePairRequired = false,
        retryAfterSeconds = null,
        serverTime = serverTime,
    )
}
