package com.example.paymentmonitor.sync

import com.example.paymentmonitor.model.PaymentDirection
import com.example.paymentmonitor.model.PaymentEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ImmediateIncomeSyncCoordinator(
    private val engine: PaymentSyncEngine,
    private val scheduler: SyncScheduler,
    private val stateStore: DeviceStateStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun onEventSaved(event: PaymentEvent) {
        if (event.direction != PaymentDirection.INCOME) {
            scheduler.enqueueDeferred()
            return
        }
        if (stateStore.snapshot().pairingState != PairingState.PAIRED) return

        scheduler.enqueueIncomeFallback()
        scope.launch {
            val result = engine.syncReadyEvents(SyncSelection.INCOME_ONLY)
            if (result == SyncExecutionResult.COMPLETE && !engine.hasPendingIncome()) {
                scheduler.cancelIncomeFallback()
            }
        }
    }
}
