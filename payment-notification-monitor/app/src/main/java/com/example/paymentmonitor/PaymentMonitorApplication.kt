package com.example.paymentmonitor

import android.app.Application
import com.example.paymentmonitor.capture.BuildVariantNotificationCaptureController
import com.example.paymentmonitor.capture.NotificationCaptureController
import com.example.paymentmonitor.data.PaymentDatabase
import com.example.paymentmonitor.data.PaymentRepository
import com.example.paymentmonitor.monitor.MonitoringPreferences
import com.example.paymentmonitor.monitor.MonitorForegroundService
import com.example.paymentmonitor.sync.ImmediateIncomeSyncCoordinator
import com.example.paymentmonitor.sync.DeviceRepository
import com.example.paymentmonitor.sync.DeviceStateStore
import com.example.paymentmonitor.sync.PaymentApiFactory
import com.example.paymentmonitor.sync.NetworkSyncObserver
import com.example.paymentmonitor.sync.PaymentSyncEngine
import com.example.paymentmonitor.sync.SyncScheduler
import com.example.paymentmonitor.ui.UiPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

open class PaymentMonitorApplication : Application() {
    val notificationCaptureController: NotificationCaptureController by lazy {
        BuildVariantNotificationCaptureController(this)
    }

    val database: PaymentDatabase by lazy {
        PaymentDatabase.create(this)
    }

    val deviceStateStore: DeviceStateStore by lazy {
        DeviceStateStore(this)
    }

    val paymentApiFactory: PaymentApiFactory by lazy {
        PaymentApiFactory(deviceStateStore)
    }

    val deviceRepository: DeviceRepository by lazy {
        DeviceRepository(this, deviceStateStore, paymentApiFactory)
    }

    val syncScheduler: SyncScheduler by lazy {
        SyncScheduler(this)
    }

    val paymentSyncEngine: PaymentSyncEngine by lazy {
        PaymentSyncEngine(this)
    }

    val immediateIncomeSyncCoordinator: ImmediateIncomeSyncCoordinator by lazy {
        ImmediateIncomeSyncCoordinator(
            engine = paymentSyncEngine,
            scheduler = syncScheduler,
            stateStore = deviceStateStore,
        )
    }

    val networkSyncObserver: NetworkSyncObserver by lazy {
        NetworkSyncObserver(
            context = this,
            dao = database.paymentEventDao(),
            engine = paymentSyncEngine,
            scheduler = syncScheduler,
            stateStore = deviceStateStore,
        )
    }

    val repository: PaymentRepository by lazy {
        PaymentRepository(
            dao = database.paymentEventDao(),
            onEventSaved = immediateIncomeSyncCoordinator::onEventSaved,
            onRetryRequested = syncScheduler::enqueueNow,
        )
    }

    val monitoringPreferences: MonitoringPreferences by lazy {
        MonitoringPreferences(this)
    }

    val uiPreferences: UiPreferences by lazy {
        UiPreferences(this)
    }

    override fun onCreate() {
        super.onCreate()
        if (!shouldStartRuntimeServices()) {
            return
        }
        networkSyncObserver.start()
        syncScheduler.enqueueNow()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (monitoringPreferences.isEnabled()) {
                runCatching { MonitorForegroundService.start(this@PaymentMonitorApplication) }
            }
        }
    }

    protected open fun shouldStartRuntimeServices(): Boolean = true
}
