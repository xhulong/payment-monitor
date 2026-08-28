package com.example.paymentmonitor.capture

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.service.notification.StatusBarNotification
import com.example.paymentmonitor.data.CURRENT_PARSER_VERSION
import com.example.paymentmonitor.data.toCanonicalJson
import com.example.paymentmonitor.model.PaymentEvent
import com.example.paymentmonitor.model.RawNotificationData
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

class BuildVariantNotificationCaptureController(
    context: Context,
) : NotificationCaptureController {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val captureDirectory = File(appContext.filesDir, CAPTURE_DIRECTORY)
    private val writeMutex = Mutex()
    private val stateFlow = MutableStateFlow(loadState())

    override val state: StateFlow<NotificationCaptureState> = stateFlow

    override fun start(sessionId: String, scenario: String) {
        val safeSessionId = sessionId.trim()
            .ifBlank { newSessionId() }
            .replace(UNSAFE_FILE_CHARS, "_")
            .take(80)
        val safeScenario = scenario.trim().ifBlank { "UNSPECIFIED" }.take(120)
        captureDirectory.mkdirs()
        val fileName = "${safeSessionId}_${System.currentTimeMillis()}.jsonl"
        val next = NotificationCaptureState(
            enabled = true,
            sessionId = safeSessionId,
            scenario = safeScenario,
            fileName = fileName,
        )
        persist(next)
    }

    override fun stop() {
        persist(stateFlow.value.copy(enabled = false))
    }

    override suspend fun capture(
        statusBarNotification: StatusBarNotification,
        raw: RawNotificationData,
        event: PaymentEvent?,
        parseError: Throwable?,
    ) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val current = stateFlow.value
            if (!current.enabled || current.fileName.isNullOrBlank()) return@withLock
            val output = File(captureDirectory, current.fileName)
            val currentBytes = output.takeIf(File::exists)?.length() ?: 0L
            if (current.recordCount >= MAX_RECORDS || currentBytes >= MAX_BYTES) {
                persist(
                    current.copy(
                        enabled = false,
                        byteCount = currentBytes,
                        lastError = "CAPTURE_LIMIT_REACHED",
                    ),
                )
                return@withLock
            }

            val line = createRecord(
                state = current,
                statusBarNotification = statusBarNotification,
                raw = raw,
                event = event,
                parseError = parseError,
            ).toString() + "\n"
            output.appendText(line, StandardCharsets.UTF_8)
            persist(
                current.copy(
                    recordCount = current.recordCount + 1,
                    byteCount = output.length(),
                    lastError = null,
                ),
            )
        }
    }

    private fun createRecord(
        state: NotificationCaptureState,
        statusBarNotification: StatusBarNotification,
        raw: RawNotificationData,
        event: PaymentEvent?,
        parseError: Throwable?,
    ): JSONObject {
        val notification = statusBarNotification.notification
        return JSONObject().apply {
            put("schema", CAPTURE_SCHEMA)
            put("sessionId", state.sessionId)
            put("scenario", state.scenario)
            put("capturedAtMs", System.currentTimeMillis())
            put("sourcePackageVersion", packageVersion(raw.packageName))
            put(
                "diagnostics",
                JSONObject().apply {
                    put("notificationWhen", notification.`when`)
                    put("category", notification.category)
                    put("group", notification.group)
                    put("sortKey", notification.sortKey)
                    put("flags", notification.flags)
                    put("groupKey", statusBarNotification.groupKey)
                    put("isOngoing", statusBarNotification.isOngoing)
                    put("isClearable", statusBarNotification.isClearable)
                },
            )
            put("raw", JSONObject(raw.toCanonicalJson()))
            put(
                "parser",
                JSONObject().apply {
                    put("parserVersion", CURRENT_PARSER_VERSION)
                    put("matched", event != null)
                    put("platform", event?.platform?.name)
                    put("direction", event?.direction?.name)
                    put("parseStatus", event?.parseStatus?.name)
                    put("amount", event?.amount?.toPlainString())
                    put("matchedRule", event?.matchedRule)
                    put("fingerprint", event?.fingerprint)
                    put("errorType", parseError?.javaClass?.simpleName)
                },
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun packageVersion(packageName: String): JSONObject {
        val info: PackageInfo? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(
                    packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                appContext.packageManager.getPackageInfo(packageName, 0)
            }
        }.getOrNull()
        return JSONObject().apply {
            put("name", info?.versionName)
            put(
                "code",
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info?.longVersionCode
                } else {
                    info?.versionCode?.toLong()
                },
            )
        }
    }

    private fun loadState(): NotificationCaptureState = NotificationCaptureState(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        sessionId = preferences.getString(KEY_SESSION_ID, null),
        scenario = preferences.getString(KEY_SCENARIO, null),
        fileName = preferences.getString(KEY_FILE_NAME, null),
        recordCount = preferences.getInt(KEY_RECORD_COUNT, 0),
        byteCount = preferences.getLong(KEY_BYTE_COUNT, 0),
        lastError = preferences.getString(KEY_LAST_ERROR, null),
    )

    private fun persist(state: NotificationCaptureState) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, state.enabled)
            .putString(KEY_SESSION_ID, state.sessionId)
            .putString(KEY_SCENARIO, state.scenario)
            .putString(KEY_FILE_NAME, state.fileName)
            .putInt(KEY_RECORD_COUNT, state.recordCount)
            .putLong(KEY_BYTE_COUNT, state.byteCount)
            .putString(KEY_LAST_ERROR, state.lastError)
            .commit()
        stateFlow.value = state
    }

    private fun newSessionId(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private companion object {
        const val CAPTURE_SCHEMA = 1
        const val PREFERENCES_NAME = "phase_c_capture"
        const val CAPTURE_DIRECTORY = "phase-c-captures"
        const val MAX_RECORDS = 1_000
        const val MAX_BYTES = 10L * 1024 * 1024
        const val KEY_ENABLED = "enabled"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_SCENARIO = "scenario"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_RECORD_COUNT = "record_count"
        const val KEY_BYTE_COUNT = "byte_count"
        const val KEY_LAST_ERROR = "last_error"
        val UNSAFE_FILE_CHARS = Regex("""[^A-Za-z0-9._-]""")
    }
}
