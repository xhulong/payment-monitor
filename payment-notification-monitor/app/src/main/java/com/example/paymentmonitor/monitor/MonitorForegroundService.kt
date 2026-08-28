package com.example.paymentmonitor.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.example.paymentmonitor.MainActivity
import com.example.paymentmonitor.PaymentMonitorApplication
import com.example.paymentmonitor.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MonitorForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null

    private val applicationContainer: PaymentMonitorApplication
        get() = application as PaymentMonitorApplication

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            serviceScope.launch {
                applicationContainer.monitoringPreferences.setEnabled(false)
                withContext(Dispatchers.Main.immediate) {
                    stopForegroundAndSelf()
                }
            }
            return START_NOT_STICKY
        }

        startAsForeground()
        ensureListenerConnection()
        startHeartbeatLoop()
        serviceScope.launch {
            if (!applicationContainer.monitoringPreferences.isEnabled()) {
                withContext(Dispatchers.Main.immediate) {
                    stopForegroundAndSelf()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        heartbeatJob?.cancel()
        MonitorRuntimeState.setForegroundRunning(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            FOREGROUND_NOTIFICATION_ID,
            buildForegroundNotification(),
            foregroundServiceType,
        )
        MonitorRuntimeState.setForegroundRunning(true)
    }

    private fun startHeartbeatLoop() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = serviceScope.launch {
            while (isActive && applicationContainer.monitoringPreferences.isEnabled()) {
                ensureListenerConnection()
                refreshForegroundNotification()
                val state = applicationContainer.deviceStateStore.snapshot()
                if (state.credentials != null &&
                    state.pairingState == com.example.paymentmonitor.sync.PairingState.PAIRED
                ) {
                    runCatching { applicationContainer.deviceRepository.heartbeat() }
                    applicationContainer.syncScheduler.enqueueNow()
                }
                val interval = applicationContainer.deviceStateStore.snapshot()
                    .credentials?.config?.heartbeatIntervalSeconds
                    ?.coerceIn(30, 3600)
                    ?: 60
                delay(interval * 1000L)
            }
        }
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        MonitorRuntimeState.setForegroundRunning(false)
        stopSelf()
    }

    private fun ensureListenerConnection() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        if (MonitorRuntimeState.listenerConnected.value) return
        if (!NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
            return
        }
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(this, PaymentNotificationListenerService::class.java),
            )
        }
    }

    private fun refreshForegroundNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification())
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MonitorForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_payment_monitor)
            .setContentTitle("LuLuPay 通知监听运行中")
            .setContentText(
                if (MonitorRuntimeState.listenerConnected.value) {
                    "通知监听已连接，正在等待支付通知"
                } else {
                    "通知监听连接异常，正在自动重连"
                },
            )
            .setContentIntent(openAppIntent)
            .addAction(0, "停止监听", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            FOREGROUND_CHANNEL_ID,
            "LuLuPay 通知监听状态",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "显示 LuLuPay 通知监听服务的运行状态"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val ACTION_START = "com.example.paymentmonitor.action.START_MONITORING"
        private const val ACTION_STOP = "com.example.paymentmonitor.action.STOP_MONITORING"
        private const val FOREGROUND_CHANNEL_ID = "payment_monitor_foreground"
        private const val FOREGROUND_NOTIFICATION_ID = 1001

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MonitorForegroundService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorForegroundService::class.java))
            MonitorRuntimeState.setForegroundRunning(false)
        }
    }
}
