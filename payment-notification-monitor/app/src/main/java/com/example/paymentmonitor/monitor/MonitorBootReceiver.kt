package com.example.paymentmonitor.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.paymentmonitor.PaymentMonitorApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MonitorBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as PaymentMonitorApplication
                if (app.monitoringPreferences.isEnabled()) {
                    MonitorForegroundService.start(context)
                }
                app.syncScheduler.enqueueNow()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
