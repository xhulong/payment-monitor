package com.example.paymentmonitor.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class SyncScheduler(
    context: Context,
) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun enqueueNow() {
        enqueueUnique(
            name = RECOVERY_WORK_NAME,
            initialDelaySeconds = 0,
            policy = ExistingWorkPolicy.KEEP,
            selection = SyncSelection.ALL,
        )
    }

    fun enqueueDeferred() {
        enqueueUnique(
            name = DEFERRED_WORK_NAME,
            initialDelaySeconds = DEFERRED_DELAY_SECONDS,
            policy = ExistingWorkPolicy.KEEP,
            selection = SyncSelection.ALL,
        )
    }

    fun enqueueIncomeFallback() {
        enqueueUnique(
            name = INCOME_FALLBACK_WORK_NAME,
            initialDelaySeconds = INCOME_FALLBACK_DELAY_SECONDS,
            policy = ExistingWorkPolicy.REPLACE,
            selection = SyncSelection.INCOME_ONLY,
        )
    }

    fun cancelIncomeFallback() {
        workManager.cancelUniqueWork(INCOME_FALLBACK_WORK_NAME)
    }

    private fun enqueueUnique(
        name: String,
        initialDelaySeconds: Long,
        policy: ExistingWorkPolicy,
        selection: SyncSelection,
    ) {
        val builder = OneTimeWorkRequestBuilder<PaymentSyncWorker>()
            .setInputData(workDataOf(INPUT_SELECTION to selection.name))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        if (initialDelaySeconds > 0) {
            builder.setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
        }
        workManager.enqueueUniqueWork(name, policy, builder.build())
    }

    companion object {
        const val WORK_NAME = "payment-event-sync"
        const val RECOVERY_WORK_NAME = WORK_NAME
        const val INCOME_FALLBACK_WORK_NAME = "payment-income-sync-fallback"
        const val DEFERRED_WORK_NAME = "payment-event-deferred-sync"
        const val INCOME_FALLBACK_DELAY_SECONDS = 5L
        const val DEFERRED_DELAY_SECONDS = 60L
        const val INPUT_SELECTION = "sync-selection"
    }
}
