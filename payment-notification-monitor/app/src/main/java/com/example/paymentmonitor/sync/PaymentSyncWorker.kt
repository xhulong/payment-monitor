package com.example.paymentmonitor.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.paymentmonitor.PaymentMonitorApplication

class PaymentSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    private val app = appContext.applicationContext as PaymentMonitorApplication

    override suspend fun doWork(): Result {
        val selection = inputData.getString(SyncScheduler.INPUT_SELECTION)
            ?.let { value -> runCatching { SyncSelection.valueOf(value) }.getOrNull() }
            ?: SyncSelection.ALL
        return when (
        app.paymentSyncEngine.syncReadyEvents(
            selection = selection,
            runAttemptCount = runAttemptCount,
        )
        ) {
            SyncExecutionResult.COMPLETE -> Result.success()
            SyncExecutionResult.RETRY -> Result.retry()
        }
    }
}
