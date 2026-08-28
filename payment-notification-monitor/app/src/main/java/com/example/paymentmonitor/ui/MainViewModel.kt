package com.example.paymentmonitor.ui

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.paymentmonitor.PaymentMonitorApplication
import com.example.paymentmonitor.BuildConfig
import com.example.paymentmonitor.R
import com.example.paymentmonitor.debug.DebugFixtureFactory
import com.example.paymentmonitor.debug.DebugFixtureType
import com.example.paymentmonitor.model.PaymentDirection
import com.example.paymentmonitor.model.PaymentEvent
import com.example.paymentmonitor.model.PaymentPlatform
import com.example.paymentmonitor.model.UploadStatus
import com.example.paymentmonitor.monitor.MonitorForegroundService
import com.example.paymentmonitor.monitor.MonitorRuntimeState
import com.example.paymentmonitor.monitor.PaymentNotificationListenerService
import com.example.paymentmonitor.monitor.PaymentNotificationParser
import com.example.paymentmonitor.sync.AppReleaseData
import com.example.paymentmonitor.sync.AppReleaseDownloader
import com.example.paymentmonitor.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class PlatformFilter {
    ALL,
    WECHAT,
    ALIPAY,
}

enum class DirectionFilter {
    ALL,
    INCOME,
    EXPENSE,
    UNKNOWN,
}

enum class UploadFilter {
    ALL,
    PENDING,
    RETRYING,
    UPLOADED,
    REJECTED,
}

data class PairingOperationState(
    val inProgress: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

data class AppUpdateState(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val latest: AppReleaseData? = null,
    val error: String? = null,
    val message: String? = null,
    val downloadedFile: String? = null,
    val downloadedVersionCode: Int? = null,
    val lastCheckedAt: Long? = null,
    val awaitingInstallPermission: Boolean = false,
) {
    val updateAvailable: Boolean
        get() = latest?.versionCode?.let { it > BuildConfig.VERSION_CODE } == true
    val required: Boolean
        get() {
            val release = latest ?: return false
            if (release.updateMode.equals("OPTIONAL", ignoreCase = true)) return false
            if (release.minSupportedVersionCode <= BuildConfig.VERSION_CODE) return false
            val enforcedAt = release.enforcementAt ?: return true
            return runCatching {
                !OffsetDateTime.parse(enforcedAt)
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .isAfter(OffsetDateTime.now(ZoneOffset.UTC))
            }.getOrDefault(true)
        }
    val securityBlocked: Boolean
        get() = required &&
            latest?.updateMode.equals("SECURITY_BLOCK", ignoreCase = true)
}

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val app = application as PaymentMonitorApplication
    private val repository = app.repository
    private val preferences = app.monitoringPreferences

    private val platformFilter = MutableStateFlow(PlatformFilter.ALL)
    private val directionFilter = MutableStateFlow(DirectionFilter.ALL)
    private val uploadFilter = MutableStateFlow(UploadFilter.ALL)
    private val pairingOperation = MutableStateFlow(PairingOperationState())
    private val _appUpdateState = MutableStateFlow(AppUpdateState())

    val selectedPlatformFilter = platformFilter
    val selectedDirectionFilter = directionFilter
    val selectedUploadFilter = uploadFilter
    val pairingOperationState: StateFlow<PairingOperationState> = pairingOperation
    val appUpdateState: StateFlow<AppUpdateState> = _appUpdateState
    val deviceConnectionState = app.deviceStateStore.stateFlow
    val notificationCaptureState = app.notificationCaptureController.state
    val themeMode = app.uiPreferences.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
    val autoUpdateEnabled = app.uiPreferences.autoUpdateEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val uploadStatusCounts = repository.observeStatusCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val events = combine(
        repository.observeEvents(),
        platformFilter,
        directionFilter,
        uploadFilter,
    ) { events, platform, direction, upload ->
        events.filter { event ->
            val platformMatches = when (platform) {
                PlatformFilter.ALL -> true
                PlatformFilter.WECHAT -> event.platform == PaymentPlatform.WECHAT
                PlatformFilter.ALIPAY -> event.platform == PaymentPlatform.ALIPAY
            }
            val directionMatches = when (direction) {
                DirectionFilter.ALL -> true
                DirectionFilter.INCOME -> event.direction == PaymentDirection.INCOME
                DirectionFilter.EXPENSE -> event.direction == PaymentDirection.EXPENSE
                DirectionFilter.UNKNOWN -> event.direction == PaymentDirection.UNKNOWN
            }
            val uploadMatches = when (upload) {
                UploadFilter.ALL -> true
                UploadFilter.PENDING -> event.uploadStatus == UploadStatus.PENDING
                UploadFilter.RETRYING ->
                    event.uploadStatus == UploadStatus.RETRYING ||
                        event.uploadStatus == UploadStatus.UPLOADING
                UploadFilter.UPLOADED -> event.uploadStatus == UploadStatus.UPLOADED
                UploadFilter.REJECTED -> event.uploadStatus == UploadStatus.REJECTED
            }
            platformMatches && directionMatches && uploadMatches
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monitoringEnabled = preferences.enabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val listenerConnected = MonitorRuntimeState.listenerConnected
    val foregroundRunning = MonitorRuntimeState.foregroundRunning
    val lastListenerTestAt = MonitorRuntimeState.lastListenerTestAt
    val lastNotificationAt = preferences.lastNotificationAtFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _notificationAccessGranted = MutableStateFlow(false)
    val notificationAccessGranted = _notificationAccessGranted

    private val _postNotificationPermissionGranted = MutableStateFlow(false)
    val postNotificationPermissionGranted = _postNotificationPermissionGranted

    private val _batteryOptimizationIgnored = MutableStateFlow(false)
    val batteryOptimizationIgnored = _batteryOptimizationIgnored

    init {
        refreshSystemStatus()
        viewModelScope.launch {
            checkForUpdate(
                automaticDownload = app.uiPreferences.autoUpdateEnabledFlow.first(),
            )
        }
    }

    fun checkForUpdate() {
        checkForUpdate(automaticDownload = false)
    }

    private fun checkForUpdate(automaticDownload: Boolean) {
        if (_appUpdateState.value.checking || _appUpdateState.value.downloading) return
        viewModelScope.launch(Dispatchers.IO) {
            _appUpdateState.value = _appUpdateState.value.copy(
                checking = true,
                error = null,
                message = null,
            )
            runCatching { app.deviceRepository.latestRelease() }
                .onSuccess { latest ->
                    val previous = _appUpdateState.value
                    val downloadedFile = previous.downloadedFile
                        ?.takeIf {
                            previous.downloadedVersionCode == latest?.versionCode &&
                                File(it).isFile
                        }
                    val state = previous.copy(
                        checking = false,
                        latest = latest,
                        error = null,
                        message = when {
                            latest == null -> "服务器暂未发布可用版本"
                            latest.versionCode <= BuildConfig.VERSION_CODE -> "当前已是最新版本"
                            automaticDownload -> "发现新版本，正在自动下载"
                            else -> "发现新版本 ${latest.versionName}"
                        },
                        downloadedFile = downloadedFile,
                        downloadedVersionCode = downloadedFile
                            ?.let { previous.downloadedVersionCode },
                        lastCheckedAt = System.currentTimeMillis(),
                        awaitingInstallPermission = false,
                    )
                    _appUpdateState.value = state
                    if (automaticDownload && state.updateAvailable) {
                        downloadUpdate(openInstallerAfterDownload = true)
                    }
                }
                .onFailure {
                    _appUpdateState.value = _appUpdateState.value.copy(
                        checking = false,
                        error = it.message ?: "检查更新失败，请稍后重试",
                        message = null,
                        lastCheckedAt = System.currentTimeMillis(),
                    )
                }
        }
    }

    fun downloadUpdate() {
        downloadUpdate(openInstallerAfterDownload = true)
    }

    private fun downloadUpdate(openInstallerAfterDownload: Boolean) {
        val release = _appUpdateState.value.latest ?: return
        if (_appUpdateState.value.downloading) return
        val cachedFile = _appUpdateState.value.downloadedFile
            ?.takeIf {
                _appUpdateState.value.downloadedVersionCode == release.versionCode &&
                    File(it).isFile
            }
        if (cachedFile != null) {
            if (openInstallerAfterDownload) {
                installDownloadedUpdate()
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _appUpdateState.value = _appUpdateState.value.copy(
                downloading = true,
                error = null,
                message = "正在下载并校验更新包",
            )
            runCatching {
                AppReleaseDownloader().download(getApplication(), release)
            }.onSuccess { file ->
                _appUpdateState.value = _appUpdateState.value.copy(
                    downloading = false,
                    downloadedFile = file.absolutePath,
                    downloadedVersionCode = release.versionCode,
                    message = "更新包已下载并通过安全校验",
                )
                if (openInstallerAfterDownload) {
                    withContext(Dispatchers.Main.immediate) {
                        installDownloadedUpdate()
                    }
                }
            }.onFailure {
                _appUpdateState.value = _appUpdateState.value.copy(
                    downloading = false,
                    error = it.message ?: "APK 下载失败",
                    message = null,
                )
            }
        }
    }

    fun installDownloadedUpdate() {
        val path = _appUpdateState.value.downloadedFile ?: return
        val context = getApplication<Application>()
        val file = File(path)
        if (!file.exists()) {
            _appUpdateState.value = _appUpdateState.value.copy(
                error = "已下载的 APK 文件不存在，请重新下载",
                downloadedFile = null,
                downloadedVersionCode = null,
                awaitingInstallPermission = false,
            )
            return
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            _appUpdateState.value = _appUpdateState.value.copy(
                error = null,
                message = "请允许本应用安装更新，返回后将继续打开系统安装器",
                awaitingInstallPermission = true,
            )
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${BuildConfig.APPLICATION_ID}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.apkprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            _appUpdateState.value = _appUpdateState.value.copy(
                error = null,
                message = "已打开系统安装器，请确认安装新版本",
                awaitingInstallPermission = false,
            )
        }.onFailure {
            _appUpdateState.value = _appUpdateState.value.copy(
                error = it.message ?: "无法打开系统安装器",
                message = null,
                awaitingInstallPermission = false,
            )
        }
    }

    fun resumePendingInstall() {
        val state = _appUpdateState.value
        if (!state.awaitingInstallPermission) return
        val context = getApplication<Application>()
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
        ) {
            installDownloadedUpdate()
        }
    }

    fun refreshSystemStatus() {
        val application = getApplication<Application>()
        _notificationAccessGranted.value =
            NotificationManagerCompat.getEnabledListenerPackages(application)
                .contains(application.packageName)
        _postNotificationPermissionGranted.value =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    application,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
        _batteryOptimizationIgnored.value =
            application.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(application.packageName)
                ?: false
    }

    fun openNotificationListenerSettings() {
        getApplication<Application>().startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun openBatteryOptimizationSettings() {
        getApplication<Application>().startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun startMonitoring() {
        viewModelScope.launch {
            preferences.setEnabled(true)
            withContext(Dispatchers.Main.immediate) {
                val context = getApplication<Application>()
                MonitorForegroundService.start(context)
                if (
                    _notificationAccessGranted.value &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ) {
                    NotificationListenerService.requestRebind(
                        ComponentName(context, PaymentNotificationListenerService::class.java),
                    )
                }
            }
        }
    }

    fun stopMonitoring() {
        viewModelScope.launch {
            preferences.setEnabled(false)
            withContext(Dispatchers.Main.immediate) {
                MonitorForegroundService.stop(getApplication())
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            app.uiPreferences.setThemeMode(mode)
        }
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        viewModelScope.launch {
            app.uiPreferences.setAutoUpdateEnabled(enabled)
            if (enabled) {
                checkForUpdate(automaticDownload = true)
            }
        }
    }

    fun setPlatformFilter(filter: PlatformFilter) {
        platformFilter.value = filter
    }

    fun setDirectionFilter(filter: DirectionFilter) {
        directionFilter.value = filter
    }

    fun setUploadFilter(filter: UploadFilter) {
        uploadFilter.value = filter
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clear()
        }
    }

    fun pair(serverUrl: String, pairingCode: String) {
        if (serverUrl.isBlank() || pairingCode.length != 8) {
            pairingOperation.value = PairingOperationState(error = "请输入服务地址和 8 位配对码")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            pairingOperation.value = PairingOperationState(inProgress = true)
            runCatching {
                app.deviceRepository.pair(serverUrl, pairingCode)
                app.syncScheduler.enqueueNow()
            }.onSuccess {
                pairingOperation.value = PairingOperationState(message = "设备配对成功")
            }.onFailure {
                pairingOperation.value = PairingOperationState(error = it.message ?: "设备配对失败")
            }
        }
    }

    fun pairFromQr(payload: String) {
        runCatching {
            val json = JSONObject(payload)
            require(json.optInt("schema") == 1) { "二维码版本不受支持" }
            json.getString("serverUrl") to json.getString("pairingCode")
        }.onSuccess { (serverUrl, code) ->
            pair(serverUrl, code)
        }.onFailure {
            pairingOperation.value = PairingOperationState(error = it.message ?: "二维码内容无效")
        }
    }

    fun clearPairing() {
        app.deviceRepository.clearPairing()
        pairingOperation.value = PairingOperationState(message = "本机配对信息已清除")
    }

    fun syncNow() {
        app.syncScheduler.enqueueNow()
        pairingOperation.value = PairingOperationState(message = "已提交同步任务")
    }

    fun refreshServerConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            pairingOperation.value = PairingOperationState(inProgress = true)
            runCatching { app.deviceRepository.fetchConfig() }
                .onSuccess {
                    pairingOperation.value = PairingOperationState(message = "服务器配置已更新")
                }
                .onFailure {
                    pairingOperation.value = PairingOperationState(error = it.message ?: "配置更新失败")
                }
        }
    }

    fun retryEvent(clientEventId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.retry(clientEventId)
        }
    }

    fun insertDebugFixture(type: DebugFixtureType) {
        viewModelScope.launch(Dispatchers.IO) {
            val event = PaymentNotificationParser.parse(DebugFixtureFactory.create(type))
            if (event != null) {
                repository.save(event)
            }
        }
    }

    fun startNotificationCapture(sessionId: String, scenario: String) {
        app.notificationCaptureController.start(sessionId, scenario)
    }

    fun stopNotificationCapture() {
        app.notificationCaptureController.stop()
    }

    fun sendListenerTestNotification() {
        val context = getApplication<Application>()
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createTestChannel(context)
        val notification = NotificationCompat.Builder(context, TEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_payment_monitor)
            .setContentTitle("监听权限测试")
            .setContentText("这是一条 LuLuPay 通知监听测试消息")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addExtras(
                android.os.Bundle().apply {
                    putBoolean(
                        PaymentNotificationListenerService.TEST_NOTIFICATION_EXTRA,
                        true,
                    )
                },
            )
            .build()
        NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
    }

    private fun createTestChannel(context: Application) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            TEST_CHANNEL_ID,
            "监听权限测试",
            NotificationManager.IMPORTANCE_HIGH,
        )
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        private const val TEST_CHANNEL_ID = "listener_test"
        private const val TEST_NOTIFICATION_ID = 2001
    }
}
