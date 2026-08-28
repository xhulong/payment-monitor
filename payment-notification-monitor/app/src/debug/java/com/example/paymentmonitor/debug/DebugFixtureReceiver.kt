package com.example.paymentmonitor.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.paymentmonitor.PaymentMonitorApplication
import com.example.paymentmonitor.monitor.MonitorForegroundService
import com.example.paymentmonitor.monitor.PaymentNotificationParser
import com.example.paymentmonitor.sync.ClientApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DebugFixtureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        val pendingResult = goAsync()
        val application = context.applicationContext as PaymentMonitorApplication
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_DEBUG_PAIR -> {
                        application.deviceRepository.pair(
                            serverUrlInput = requireNotNull(intent.getStringExtra(EXTRA_SERVER_URL)),
                            pairingCode = requireNotNull(intent.getStringExtra(EXTRA_PAIRING_CODE)),
                        )
                        application.syncScheduler.enqueueNow()
                    }

                    ACTION_DEBUG_FIXTURE -> {
                        val fixtureType = DebugFixtureType.valueOf(
                            intent.getStringExtra(EXTRA_TYPE).orEmpty(),
                        )
                        val fixtureTimestamp = intent
                            .takeIf { it.hasExtra(EXTRA_EVENT_TIME_MS) }
                            ?.getLongExtra(EXTRA_EVENT_TIME_MS, System.currentTimeMillis())
                            ?: System.currentTimeMillis()
                        val event = PaymentNotificationParser.parse(
                            raw = DebugFixtureFactory.create(
                                type = fixtureType,
                                timestamp = fixtureTimestamp,
                            ),
                            receivedAt = fixtureTimestamp,
                        )
                        if (event != null) {
                            application.repository.save(event)
                        }
                    }

                    ACTION_DEBUG_SYNC -> application.syncScheduler.enqueueNow()
                    ACTION_DEBUG_HEARTBEAT -> application.deviceRepository.heartbeat()
                    ACTION_DEBUG_START_MONITORING -> {
                        application.monitoringPreferences.setEnabled(true)
                        withContext(Dispatchers.Main.immediate) {
                            MonitorForegroundService.start(application)
                        }
                    }

                    ACTION_DEBUG_STOP_MONITORING -> {
                        application.monitoringPreferences.setEnabled(false)
                        withContext(Dispatchers.Main.immediate) {
                            MonitorForegroundService.stop(application)
                        }
                    }

                    ACTION_DEBUG_CAPTURE_START -> {
                        application.notificationCaptureController.start(
                            sessionId = intent.getStringExtra(EXTRA_CAPTURE_SESSION).orEmpty(),
                            scenario = intent.getStringExtra(EXTRA_CAPTURE_SCENARIO).orEmpty(),
                        )
                    }

                    ACTION_DEBUG_CAPTURE_STOP -> {
                        application.notificationCaptureController.stop()
                    }

                    ACTION_DEBUG_STATE -> Unit
                }
                logState(application, intent.action.orEmpty(), null)
            } catch (throwable: Throwable) {
                logState(application, intent.action.orEmpty(), throwable)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun logState(
        application: PaymentMonitorApplication,
        action: String,
        throwable: Throwable?,
    ) {
        val state = application.deviceStateStore.snapshot()
        val rows = application.database.paymentEventDao().getAll()
        val statusCounts = rows.groupingBy { it.uploadStatus }.eachCount()
        val clientError = throwable as? ClientApiException
        val capture = application.notificationCaptureController.state.value
        Log.i(
            TAG,
            buildString {
                append("action=").append(action)
                append(" success=").append(throwable == null)
                append(" pairingState=").append(state.pairingState)
                append(" deviceId=").append(state.credentials?.deviceId ?: "-")
                append(" serverUrl=").append(state.credentials?.serverUrl ?: "-")
                append(" deviceRole=").append(state.credentials?.deviceRole ?: "-")
                append(" platformScope=").append(state.credentials?.platformScope ?: "-")
                append(" pending=").append(statusCounts["PENDING"] ?: 0)
                append(" uploading=").append(statusCounts["UPLOADING"] ?: 0)
                append(" retrying=").append(statusCounts["RETRYING"] ?: 0)
                append(" uploaded=").append(statusCounts["UPLOADED"] ?: 0)
                append(" rejected=").append(statusCounts["REJECTED"] ?: 0)
                append(" captureEnabled=").append(capture.enabled)
                append(" captureSession=").append(capture.sessionId ?: "-")
                append(" captureScenario=").append(capture.scenario ?: "-")
                append(" captureFile=").append(capture.fileName ?: "-")
                append(" captureRecords=").append(capture.recordCount)
                append(" captureBytes=").append(capture.byteCount)
                append(" captureError=").append(capture.lastError ?: "-")
                append(" errorCode=").append(
                    clientError?.code ?: state.lastErrorCode ?: throwable?.javaClass?.simpleName ?: "-",
                )
            },
        )
    }

    companion object {
        private const val TAG = "PaymentMonitorDebug"
        const val ACTION_DEBUG_PAIR = "com.example.paymentmonitor.DEBUG_PAIR"
        const val ACTION_DEBUG_FIXTURE = "com.example.paymentmonitor.DEBUG_FIXTURE"
        const val ACTION_DEBUG_SYNC = "com.example.paymentmonitor.DEBUG_SYNC"
        const val ACTION_DEBUG_HEARTBEAT = "com.example.paymentmonitor.DEBUG_HEARTBEAT"
        const val ACTION_DEBUG_START_MONITORING =
            "com.example.paymentmonitor.DEBUG_START_MONITORING"
        const val ACTION_DEBUG_STOP_MONITORING =
            "com.example.paymentmonitor.DEBUG_STOP_MONITORING"
        const val ACTION_DEBUG_CAPTURE_START =
            "com.example.paymentmonitor.DEBUG_CAPTURE_START"
        const val ACTION_DEBUG_CAPTURE_STOP =
            "com.example.paymentmonitor.DEBUG_CAPTURE_STOP"
        const val ACTION_DEBUG_STATE = "com.example.paymentmonitor.DEBUG_STATE"
        const val EXTRA_TYPE = "type"
        const val EXTRA_EVENT_TIME_MS = "eventTimeMs"
        const val EXTRA_SERVER_URL = "serverUrl"
        const val EXTRA_PAIRING_CODE = "pairingCode"
        const val EXTRA_CAPTURE_SESSION = "captureSession"
        const val EXTRA_CAPTURE_SCENARIO = "captureScenario"

        private val SUPPORTED_ACTIONS = setOf(
            ACTION_DEBUG_PAIR,
            ACTION_DEBUG_FIXTURE,
            ACTION_DEBUG_SYNC,
            ACTION_DEBUG_HEARTBEAT,
            ACTION_DEBUG_START_MONITORING,
            ACTION_DEBUG_STOP_MONITORING,
            ACTION_DEBUG_CAPTURE_START,
            ACTION_DEBUG_CAPTURE_STOP,
            ACTION_DEBUG_STATE,
        )
    }
}
