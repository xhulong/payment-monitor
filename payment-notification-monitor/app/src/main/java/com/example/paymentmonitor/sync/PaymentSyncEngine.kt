package com.example.paymentmonitor.sync

import com.example.paymentmonitor.PaymentMonitorApplication
import com.example.paymentmonitor.data.PaymentEventEntity
import com.example.paymentmonitor.data.PaymentRepository
import com.example.paymentmonitor.data.sha256Hex
import com.example.paymentmonitor.data.toCanonicalJson
import com.example.paymentmonitor.data.toRawNotification
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.math.BigDecimal
import java.util.concurrent.CancellationException
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SyncSelection {
    INCOME_ONLY,
    ALL,
}

enum class SyncExecutionResult {
    COMPLETE,
    RETRY,
}

class PaymentSyncEngine(
    private val app: PaymentMonitorApplication,
) {
    private val dao = app.database.paymentEventDao()
    private val gson = Gson()
    private val syncMutex = Mutex()

    suspend fun syncReadyEvents(
        selection: SyncSelection,
        runAttemptCount: Int = 0,
    ): SyncExecutionResult = syncMutex.withLock {
        val connection = app.deviceStateStore.snapshot()
        if (connection.pairingState != PairingState.PAIRED || connection.credentials == null) {
            return@withLock SyncExecutionResult.COMPLETE
        }

        val now = System.currentTimeMillis()
        dao.recoverStaleUploading(now - STALE_UPLOAD_MS, now)
        val maxBatch = connection.credentials.config.maxBatchSize.coerceIn(1, 100)
        val rows = when (selection) {
            SyncSelection.INCOME_ONLY -> dao.getReadyIncomeForUpload(now, maxBatch)
            SyncSelection.ALL -> dao.getReadyForUpload(now, maxBatch)
        }
        if (rows.isEmpty()) {
            return@withLock if (pendingCount(selection) > 0) {
                SyncExecutionResult.RETRY
            } else {
                SyncExecutionResult.COMPLETE
            }
        }

        val selectedRows = selectWithinBodyLimit(
            rows = rows,
            rawPayloadEnabled = connection.credentials.config.rawPayloadUploadEnabled,
        )
        if (selectedRows.isEmpty()) return@withLock SyncExecutionResult.COMPLETE

        val clientEventIds = selectedRows.map { it.first.clientEventId }
        dao.markUploading(clientEventIds, now)
        val request = PaymentEventBatchRequest(
            sentAt = currentUtcIsoMillis(),
            events = selectedRows.map { it.second },
        )

        try {
            val response = uploadWithClockRetry(request)
            val uploaded = (response.accepted + response.duplicates).distinct()
            dao.finishUploadAndTrim(uploaded, System.currentTimeMillis(), PaymentRepository.MAXIMUM_HISTORY)
            response.rejected.forEach { rejected ->
                dao.markRejected(rejected.clientEventId, rejected.code, rejected.message)
            }
            val accounted = (uploaded + response.rejected.map { it.clientEventId }).toSet()
            val missing = clientEventIds.filterNot(accounted::contains)
            if (missing.isNotEmpty()) {
                markTransientFailure(
                    clientEventIds = missing,
                    code = "ACK_MISSING",
                    message = "服务端未返回逐条确认",
                    retryAfterSeconds = null,
                    runAttemptCount = runAttemptCount,
                )
            }
            app.deviceStateStore.markSynced()
            if (pendingCount(selection) > 0) {
                SyncExecutionResult.RETRY
            } else {
                SyncExecutionResult.COMPLETE
            }
        } catch (exception: ClientApiException) {
            if (exception.rePairRequired ||
                exception.code in setOf("DEVICE_DISABLED", "CREDENTIAL_REVOKED")
            ) {
                app.deviceStateStore.markRepairRequired(exception.code, exception.message)
                dao.markRetrying(
                    clientEventIds,
                    System.currentTimeMillis() + MAX_BACKOFF_MS,
                    exception.code,
                    exception.message,
                )
                SyncExecutionResult.COMPLETE
            } else if (exception.retryable || exception.httpStatus == 429 || exception.httpStatus >= 500) {
                markTransientFailure(
                    clientEventIds = clientEventIds,
                    code = exception.code,
                    message = exception.message,
                    retryAfterSeconds = exception.retryAfterSeconds,
                    runAttemptCount = runAttemptCount,
                )
                SyncExecutionResult.RETRY
            } else {
                clientEventIds.forEach { id ->
                    dao.markRejected(id, exception.code, exception.message)
                }
                SyncExecutionResult.COMPLETE
            }
        } catch (exception: CancellationException) {
            dao.markRetrying(
                clientEventIds,
                System.currentTimeMillis(),
                "SYNC_CANCELLED",
                "同步任务已取消，将重新上传",
            )
            throw exception
        } catch (exception: Exception) {
            markTransientFailure(
                clientEventIds = clientEventIds,
                code = "NETWORK_ERROR",
                message = exception.message,
                retryAfterSeconds = null,
                runAttemptCount = runAttemptCount,
            )
            SyncExecutionResult.RETRY
        }
    }

    suspend fun hasPendingIncome(): Boolean = dao.countPendingIncome() > 0

    private suspend fun pendingCount(selection: SyncSelection): Int = when (selection) {
        SyncSelection.INCOME_ONLY -> dao.countPendingIncome()
        SyncSelection.ALL -> dao.countPending()
    }

    private suspend fun uploadWithClockRetry(
        request: PaymentEventBatchRequest,
    ): PaymentEventBatchData = try {
        app.deviceRepository.upload(request)
    } catch (exception: ClientApiException) {
        if (exception.code == "AUTH_TIMESTAMP_EXPIRED") {
            app.deviceStateStore.updateClock(exception.serverTime)
            app.deviceRepository.upload(request.copy(sentAt = currentUtcIsoMillis()))
        } else {
            throw exception
        }
    }

    private suspend fun selectWithinBodyLimit(
        rows: List<PaymentEventEntity>,
        rawPayloadEnabled: Boolean,
    ): List<Pair<PaymentEventEntity, PaymentEventItemData>> {
        val selected = mutableListOf<Pair<PaymentEventEntity, PaymentEventItemData>>()
        for (row in rows) {
            val itemWithRaw = toPayload(row, rawPayloadEnabled)
            var candidate = selected + (row to itemWithRaw)
            var request = PaymentEventBatchRequest(
                currentUtcIsoMillis(),
                candidate.map { it.second },
            )
            if (gson.toJson(request).toByteArray().size > MAX_BODY_BYTES) {
                val withoutRaw = itemWithRaw.copy(rawPayload = null)
                candidate = selected + (row to withoutRaw)
                request = PaymentEventBatchRequest(
                    currentUtcIsoMillis(),
                    candidate.map { it.second },
                )
                if (gson.toJson(request).toByteArray().size > MAX_BODY_BYTES) {
                    if (selected.isEmpty()) {
                        dao.markRejected(
                            row.clientEventId,
                            "LOCAL_PAYLOAD_TOO_LARGE",
                            "单条事件超过上传限制",
                        )
                        continue
                    }
                    break
                }
            }
            selected.clear()
            selected.addAll(candidate)
        }
        return selected
    }

    private suspend fun toPayload(
        row: PaymentEventEntity,
        rawPayloadEnabled: Boolean,
    ): PaymentEventItemData {
        val raw = row.toRawNotification()
        val canonicalRaw = raw.toCanonicalJson()
        val rawHash = row.rawHash ?: sha256Hex(canonicalRaw)
        val notificationKeyHash = row.notificationKeyHash
            ?: row.notificationKey?.let(::sha256Hex)
        if (row.rawHash == null || row.notificationKeyHash != notificationKeyHash) {
            dao.updateHashes(row.clientEventId, notificationKeyHash, rawHash)
        }
        return PaymentEventItemData(
            clientEventId = row.clientEventId,
            deviceSequence = row.deviceSequence,
            platform = row.platform,
            direction = row.direction,
            amountMinor = row.amount?.let {
                BigDecimal(it).movePointRight(2).longValueExact()
            },
            currency = row.currency,
            eventTime = epochMillisToUtcIso(row.postTime),
            eventTimeMs = row.postTime,
            clientReceivedAt = epochMillisToUtcIso(row.receivedAt),
            clientReceivedAtMs = row.receivedAt,
            parseStatus = row.parseStatus,
            parserVersion = row.parserVersion,
            matchedRule = row.matchedRule,
            fingerprint = row.fingerprint,
            notificationKeyHash = notificationKeyHash,
            rawHash = rawHash,
            rawPayload = canonicalRaw
                .takeIf { rawPayloadEnabled }
                ?.let { JsonParser.parseString(it).asJsonObject },
        )
    }

    private suspend fun markTransientFailure(
        clientEventIds: List<String>,
        code: String,
        message: String?,
        retryAfterSeconds: Long?,
        runAttemptCount: Int,
    ) {
        val baseSeconds = retryAfterSeconds ?: min(
            MAX_BACKOFF_SECONDS,
            (30.0 * 2.0.pow(runAttemptCount.coerceAtMost(10))).toLong(),
        )
        val jitter = (baseSeconds * Random.nextDouble(0.0, 0.2)).toLong()
        dao.markRetrying(
            clientEventIds,
            System.currentTimeMillis() + (baseSeconds + jitter) * 1000,
            code,
            message,
        )
    }

    private companion object {
        const val MAX_BODY_BYTES = 768 * 1024
        const val STALE_UPLOAD_MS = 10 * 60 * 1000L
        const val MAX_BACKOFF_SECONDS = 6 * 60 * 60L
        const val MAX_BACKOFF_MS = MAX_BACKOFF_SECONDS * 1000
    }
}
