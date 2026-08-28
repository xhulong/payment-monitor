package com.example.paymentmonitor.data

import com.example.paymentmonitor.model.PaymentEvent
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine

class PaymentRepository(
    private val dao: PaymentEventDao,
    private val nowProvider: () -> Long = System::currentTimeMillis,
    private val onEventSaved: (PaymentEvent) -> Unit = {},
    private val onRetryRequested: () -> Unit = {},
) {
    private val recentFingerprints = ConcurrentHashMap<String, Long>()

    fun observeEvents(): Flow<List<PaymentEvent>> =
        dao.observeAll().map { entities -> entities.map(PaymentEventEntity::toModel) }

    fun observeStatusCounts(): Flow<Map<String, Int>> =
        dao.observeStatusCounts().map { rows -> rows.associate { it.uploadStatus to it.count } }

    suspend fun save(event: PaymentEvent): Boolean {
        val now = nowProvider()
        pruneRecent(now)
        val notificationKey = event.raw.notificationKey
        if (
            !notificationKey.isNullOrBlank() &&
            dao.existsNotificationIdentity(
                sourcePackage = event.raw.packageName,
                notificationKey = notificationKey,
                postTime = event.raw.postTime,
                direction = event.direction.name,
                amount = event.amount?.toPlainString(),
            )
        ) {
            return false
        }
        val lastSeen = recentFingerprints[event.fingerprint]
        if (lastSeen != null && now - lastSeen < DEDUPE_WINDOW_MS) return false

        recentFingerprints[event.fingerprint] = now
        val inserted = dao.insertAndTrim(event.toEntity(), MAXIMUM_HISTORY)
        if (!inserted) {
            recentFingerprints.remove(event.fingerprint, now)
        } else {
            onEventSaved(event)
        }
        return inserted
    }

    suspend fun clear() {
        dao.clearTerminalHistory()
    }

    suspend fun retry(clientEventId: String) {
        dao.retry(clientEventId, nowProvider())
        onRetryRequested()
    }

    private fun pruneRecent(now: Long) {
        val iterator = recentFingerprints.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value >= DEDUPE_WINDOW_MS) {
                recentFingerprints.remove(entry.key, entry.value)
            }
        }
    }

    companion object {
        const val MAXIMUM_HISTORY = 500
        private const val DEDUPE_WINDOW_MS = 30_000L
    }
}
