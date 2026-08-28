package com.example.paymentmonitor.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.example.paymentmonitor.data.PaymentEventDao
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 网络恢复时只加速因断网失败的收款通知。
 *
 * 服务端限流和 5xx 仍遵守原有退避时间，非收款通知继续走延迟批量通道。
 */
class NetworkSyncObserver(
    context: Context,
    private val dao: PaymentEventDao,
    private val engine: PaymentSyncEngine,
    private val scheduler: SyncScheduler,
    private val stateStore: DeviceStateStore,
) {
    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastTriggeredAt = AtomicLong(0)

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            onNetworkAvailable()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            if (
                networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET,
                )
            ) {
                onNetworkAvailable()
            }
        }
    }

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } else {
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback,
            )
        }
    }

    private fun onNetworkAvailable() {
        val now = System.currentTimeMillis()
        val previous = lastTriggeredAt.get()
        if (now - previous < CALLBACK_DEBOUNCE_MS) return
        if (!lastTriggeredAt.compareAndSet(previous, now)) return

        scope.launch {
            scheduler.enqueueDeferred()
            if (stateStore.snapshot().pairingState != PairingState.PAIRED) return@launch

            dao.expediteIncomeNetworkRetries(System.currentTimeMillis())
            scheduler.enqueueIncomeFallback()
            val result = engine.syncReadyEvents(SyncSelection.INCOME_ONLY)
            if (result == SyncExecutionResult.COMPLETE && !engine.hasPendingIncome()) {
                scheduler.cancelIncomeFallback()
            }
        }
    }

    private companion object {
        const val CALLBACK_DEBOUNCE_MS = 1_000L
    }
}
