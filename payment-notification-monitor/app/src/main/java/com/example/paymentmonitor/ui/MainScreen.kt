package com.example.paymentmonitor.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.paymentmonitor.BuildConfig
import com.example.paymentmonitor.debug.DebugFixtureType
import com.example.paymentmonitor.model.PaymentDirection
import com.example.paymentmonitor.model.PaymentEvent
import com.example.paymentmonitor.model.PaymentPlatform
import com.example.paymentmonitor.model.UploadStatus
import com.example.paymentmonitor.sync.DeviceConnectionState
import com.example.paymentmonitor.sync.PairingState
import com.example.paymentmonitor.ui.theme.ThemeMode
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private enum class MainTab(
    val title: String,
    val subtitle: String,
    val navLabel: String,
    val icon: ImageVector,
    val testTag: String,
) {
    MONITOR(
        "LuLuPay",
        "码支付通知监听",
        "监听",
        Icons.Rounded.NotificationsActive,
        "tab-monitor",
    ),
    SYNC("设备同步", "安全连接与上传队列", "同步", Icons.Rounded.Sync, "tab-sync"),
    EVENTS("支付记录", "本地事件与上传状态", "记录", Icons.Rounded.ReceiptLong, "tab-events"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean = false,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onRequestNotificationPermission: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSystemStatus()
                viewModel.resumePendingInstall()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    GlassStyleProvider(darkTheme = darkTheme) {
        GlassBackground {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    GlassTopHeader(
                        tab = MainTab.entries[selectedTab],
                        themeMode = themeMode,
                        onThemeModeChange = { onThemeModeChange(themeMode.next()) },
                    )
                },
                bottomBar = {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        FloatingGlassNavigation(
                            items = MainTab.entries.map {
                                GlassNavigationItem(it.navLabel, it.icon, it.testTag)
                            },
                            selectedIndex = selectedTab,
                            onSelected = { selectedTab = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
            ) { padding ->
                AnimatedContent(
                    targetState = selectedTab,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (
                            slideInHorizontally { it / 8 * direction } + fadeIn()
                            ).togetherWith(
                            slideOutHorizontally { -it / 10 * direction } + fadeOut(),
                        ).using(SizeTransform(clip = false))
                    },
                    label = "main-tab-content",
                ) { tab ->
                    when (tab) {
                        0 -> MonitorTab(viewModel, onRequestNotificationPermission, padding)
                        1 -> SyncTab(viewModel, padding)
                        else -> EventsTab(viewModel, padding)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassTopHeader(
    tab: MainTab,
    themeMode: ThemeMode,
    onThemeModeChange: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(25.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = tab.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = tab.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ThemeModeButton(
            mode = themeMode,
            onClick = onThemeModeChange,
            modifier = Modifier.testTag("theme-mode"),
        )
    }
}

@Composable
private fun MonitorTab(
    viewModel: MainViewModel,
    onRequestNotificationPermission: () -> Unit,
    padding: PaddingValues,
) {
    val monitoringEnabled by viewModel.monitoringEnabled.collectAsStateWithLifecycle()
    val listenerConnected by viewModel.listenerConnected.collectAsStateWithLifecycle()
    val foregroundRunning by viewModel.foregroundRunning.collectAsStateWithLifecycle()
    val notificationAccessGranted by viewModel.notificationAccessGranted.collectAsStateWithLifecycle()
    val postPermissionGranted by viewModel.postNotificationPermissionGranted.collectAsStateWithLifecycle()
    val batteryOptimizationIgnored by viewModel.batteryOptimizationIgnored.collectAsStateWithLifecycle()
    val lastTestAt by viewModel.lastListenerTestAt.collectAsStateWithLifecycle()
    val lastNotificationAt by viewModel.lastNotificationAt.collectAsStateWithLifecycle()
    val captureState by viewModel.notificationCaptureState.collectAsStateWithLifecycle()
    var captureSession by remember { mutableStateOf("phase-c") }
    var captureScenario by remember { mutableStateOf("WECHAT_CLERK_INCOME") }
    var debugExpanded by remember { mutableStateOf(false) }
    val activelyMonitoring = monitoringEnabled && foregroundRunning

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            AnimatedSection(delayMillis = 0) {
                MonitoringHero(
                    monitoringEnabled = monitoringEnabled,
                    listenerConnected = listenerConnected,
                    foregroundRunning = foregroundRunning,
                    onToggle = {
                        if (monitoringEnabled || foregroundRunning) {
                            viewModel.stopMonitoring()
                        } else if (postPermissionGranted) {
                            viewModel.startMonitoring()
                        } else {
                            onRequestNotificationPermission()
                        }
                    },
                )
            }
        }
        item {
            AnimatedSection(delayMillis = 70) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SectionHeading(
                            title = "运行健康",
                            subtitle = "关键权限与后台链路",
                            trailing = {
                                Icon(
                                    imageVector = if (activelyMonitoring) {
                                        Icons.Outlined.CheckCircle
                                    } else {
                                        Icons.Outlined.WarningAmber
                                    },
                                    contentDescription = null,
                                    tint = if (activelyMonitoring) {
                                        glassColors.positive
                                    } else {
                                        glassColors.warning
                                    },
                                )
                            },
                        )
                        StatusGrid(
                            items = listOf(
                                "通知使用权" to notificationAccessGranted,
                                "监听连接" to listenerConnected,
                                "前台服务" to foregroundRunning,
                                "通知权限" to postPermissionGranted,
                                "后台保护" to batteryOptimizationIgnored,
                            ),
                        )
                    }
                }
            }
        }
        item {
            AnimatedSection(delayMillis = 130) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SectionHeading(title = "最近活动", subtitle = "用于判断监听链路是否持续工作")
                        ActivityTimelineItem(
                            icon = Icons.Outlined.Notifications,
                            title = "最近支付应用通知",
                            value = lastNotificationAt?.let(::formatTime) ?: "尚未收到",
                            active = lastNotificationAt != null,
                        )
                        ActivityTimelineItem(
                            icon = Icons.Outlined.Security,
                            title = "最近权限测试",
                            value = lastTestAt?.let(::formatTime) ?: "尚未测试",
                            active = lastTestAt != null,
                        )
                    }
                }
            }
        }
        item {
            AnimatedSection(delayMillis = 190) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        SectionHeading(title = "权限与后台保护")
                        GlassButton(
                            text = "通知使用权设置",
                            icon = Icons.Outlined.Security,
                            onClick = viewModel::openNotificationListenerSettings,
                            modifier = Modifier.fillMaxWidth(),
                            style = GlassButtonStyle.SECONDARY,
                        )
                        GlassButton(
                            text = if (postPermissionGranted) "发送监听权限测试通知" else "授予通知权限",
                            icon = Icons.Outlined.Send,
                            onClick = {
                                if (postPermissionGranted) viewModel.sendListenerTestNotification()
                                else onRequestNotificationPermission()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            style = GlassButtonStyle.SECONDARY,
                        )
                        if (!batteryOptimizationIgnored) {
                            GlassButton(
                                text = "关闭电池优化限制",
                                icon = Icons.Outlined.BatterySaver,
                                onClick = viewModel::openBatteryOptimizationSettings,
                                modifier = Modifier.fillMaxWidth(),
                                style = GlassButtonStyle.SECONDARY,
                            )
                            WarningMessage("红米等设备建议同时开启自启动，并在最近任务中锁定本应用。")
                        }
                    }
                }
            }
        }
        if (BuildConfig.DEBUG) {
            item {
                AnimatedSection(delayMillis = 250) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth().testTag("debug-tools"),
                        onClick = { debugExpanded = !debugExpanded },
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.BugReport,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text("开发工具", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (captureState.enabled) "原始通知采集中" else "采集与支付测试样本",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    if (debugExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    contentDescription = if (debugExpanded) "收起开发工具" else "展开开发工具",
                                )
                            }
                            AnimatedVisibility(
                                visible = debugExpanded,
                                modifier = Modifier.testTag("debug-tools-content"),
                                enter = fadeIn() + slideInVertically { -it / 8 },
                                exit = fadeOut(),
                            ) {
                                DebugToolsContent(
                                    captureState = captureState,
                                    captureSession = captureSession,
                                    onCaptureSessionChange = { captureSession = it.take(80) },
                                    captureScenario = captureScenario,
                                    onCaptureScenarioChange = { captureScenario = it.take(120) },
                                    onStartCapture = {
                                        viewModel.startNotificationCapture(captureSession, captureScenario)
                                    },
                                    onStopCapture = viewModel::stopNotificationCapture,
                                    onInsertFixture = viewModel::insertDebugFixture,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitoringHero(
    monitoringEnabled: Boolean,
    listenerConnected: Boolean,
    foregroundRunning: Boolean,
    onToggle: () -> Unit,
) {
    val running = monitoringEnabled && foregroundRunning
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(running) {
        if (!running) {
            pulse.snapTo(0f)
        } else {
            repeat(2) {
                pulse.animateTo(1f, tween(900))
                pulse.animateTo(0f, tween(900))
            }
        }
    }
    val status = when {
        running && listenerConnected -> "监听运行中"
        running -> "正在自动重连"
        else -> "监听已停止"
    }
    val description = when {
        running && listenerConnected -> "微信与支付宝支付通知会实时解析并同步"
        running -> "前台服务正在恢复系统通知监听连接"
        else -> "启动后才会保存新的支付候选通知"
    }
    val accent = when {
        running && listenerConnected -> glassColors.positive
        running -> glassColors.warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        elevated = true,
        shape = RoundedCornerShape(32.dp),
    ) {
        Box {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer { alpha = 0.08f + pulse.value * 0.12f }
                    .size(180.dp)
                    .background(accent, CircleShape),
            )
            Column(
                Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (running) Icons.Rounded.NotificationsActive
                            else Icons.Outlined.CloudOff,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(29.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                GlassButton(
                    text = if (running) "停止监听" else "开始监听",
                    icon = if (running) Icons.Rounded.StopCircle else Icons.Rounded.PlayArrow,
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth().testTag("monitor-primary-action"),
                    style = if (running) GlassButtonStyle.DANGER else GlassButtonStyle.PRIMARY,
                )
            }
        }
    }
}

@Composable
private fun StatusGrid(
    items: List<Pair<String, Boolean>>,
) {
    items.chunked(2).forEach { rowItems ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            rowItems.forEach { (label, active) ->
                AnimatedStatusPill(label, active, Modifier.weight(1f))
            }
            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ActivityTimelineItem(
    icon: ImageVector,
    title: String,
    value: String,
    active: Boolean,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    if (active) MaterialTheme.colorScheme.secondary.copy(alpha = 0.13f)
                    else glassColors.card,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WarningMessage(message: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(glassColors.warning.copy(alpha = 0.12f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = glassColors.warning,
            modifier = Modifier.size(19.dp),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DebugToolsContent(
    captureState: com.example.paymentmonitor.capture.NotificationCaptureState,
    captureSession: String,
    onCaptureSessionChange: (String) -> Unit,
    captureScenario: String,
    onCaptureScenarioChange: (String) -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onInsertFixture: (DebugFixtureType) -> Unit,
) {
    Column(
        Modifier.padding(top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(color = glassColors.border)
        Text("原始通知采集", style = MaterialTheme.typography.titleMedium)
        Text(
            "仅 Debug：写入应用私有目录，不上传服务端。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = captureSession,
            onValueChange = onCaptureSessionChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("采集会话") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )
        OutlinedTextField(
            value = captureScenario,
            onValueChange = onCaptureScenarioChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("场景标签") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )
        Text(
            if (captureState.enabled) {
                "采集中 · ${captureState.recordCount} 条 · ${captureState.byteCount} 字节"
            } else {
                "未采集 · 最近文件 ${captureState.fileName ?: "-"}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (captureState.enabled) glassColors.positive
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        captureState.lastError?.let { WarningMessage(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                text = "开始采集",
                onClick = onStartCapture,
                enabled = !captureState.enabled,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                text = "停止采集",
                onClick = onStopCapture,
                enabled = captureState.enabled,
                modifier = Modifier.weight(1f),
                style = GlassButtonStyle.SECONDARY,
            )
        }
        HorizontalDivider(color = glassColors.border)
        Text("支付测试样本", style = MaterialTheme.typography.titleMedium)
        DebugFixtureType.entries.chunked(2).forEach { types ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                types.forEach { type ->
                    GlassButton(
                        text = type.displayName,
                        onClick = { onInsertFixture(type) },
                        modifier = Modifier.weight(1f),
                        style = GlassButtonStyle.SECONDARY,
                    )
                }
                if (types.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SyncTab(
    viewModel: MainViewModel,
    padding: PaddingValues,
) {
    val state by viewModel.deviceConnectionState.collectAsStateWithLifecycle()
    val counts by viewModel.uploadStatusCounts.collectAsStateWithLifecycle()
    val operation by viewModel.pairingOperationState.collectAsStateWithLifecycle()
    val appUpdate by viewModel.appUpdateState.collectAsStateWithLifecycle()
    val autoUpdateEnabled by viewModel.autoUpdateEnabled.collectAsStateWithLifecycle()
    var serverUrl by remember { mutableStateOf(BuildConfig.DEFAULT_SERVER_URL) }
    var pairingCode by remember { mutableStateOf("") }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(viewModel::pairFromQr)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            AnimatedSection(0) {
                AppUpdateCard(
                    state = appUpdate,
                    autoUpdateEnabled = autoUpdateEnabled,
                    viewModel = viewModel,
                )
            }
        }
        item {
            AnimatedSection(70) {
                ConnectionHero(state)
            }
        }
        item {
            AnimatedSection(100) {
                QueueMetrics(counts)
            }
        }
        item {
            AnimatedSection(130) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevated = state.pairingState != PairingState.PAIRED,
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        SectionHeading(
                            title = "设备配对",
                            subtitle = if (state.pairingState == PairingState.PAIRED) {
                                "已配对，可使用新配对码迁移设备"
                            } else {
                                "扫描后台二维码或手动输入"
                            },
                            trailing = {
                                Icon(
                                    Icons.Outlined.Link,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                        )
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("服务地址") },
                            leadingIcon = {
                                Icon(Icons.Outlined.CloudDone, contentDescription = null)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                        )
                        OutlinedTextField(
                            value = pairingCode,
                            onValueChange = { pairingCode = it.filter(Char::isDigit).take(8) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("8 位配对码") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Link, contentDescription = null)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassButton(
                                text = if (operation.inProgress) "处理中…" else "手动配对",
                                onClick = { viewModel.pair(serverUrl, pairingCode) },
                                enabled = !operation.inProgress,
                                modifier = Modifier.weight(1f),
                                icon = Icons.Outlined.Link,
                            )
                            GlassButton(
                                text = "扫码配对",
                                onClick = {
                                    scanner.launch(
                                        ScanOptions()
                                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                            .setPrompt("扫描支付监控配对二维码")
                                            .setBeepEnabled(false),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                icon = Icons.Outlined.QrCodeScanner,
                                style = GlassButtonStyle.SECONDARY,
                            )
                        }
                        OperationFeedback(operation)
                    }
                }
            }
        }
        item {
            AnimatedSection(190) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        SectionHeading(title = "同步操作", subtitle = "任务支持断网恢复和进程重启")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassButton(
                                text = "立即同步",
                                onClick = viewModel::syncNow,
                                enabled = state.pairingState == PairingState.PAIRED,
                                modifier = Modifier.weight(1f),
                                icon = Icons.Outlined.Upload,
                            )
                            GlassButton(
                                text = "刷新配置",
                                onClick = viewModel::refreshServerConfig,
                                enabled = state.pairingState == PairingState.PAIRED,
                                modifier = Modifier.weight(1f),
                                icon = Icons.Outlined.Refresh,
                                style = GlassButtonStyle.SECONDARY,
                            )
                        }
                        GlassButton(
                            text = "解除本机配对",
                            onClick = viewModel::clearPairing,
                            enabled = state.pairingState != PairingState.UNPAIRED,
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Outlined.LinkOff,
                            style = GlassButtonStyle.DANGER,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUpdateCard(
    state: AppUpdateState,
    autoUpdateEnabled: Boolean,
    viewModel: MainViewModel,
) {
    val release = state.latest
    val statusText = when {
        state.checking -> "正在检查服务器版本"
        state.downloading -> "正在下载并校验更新包"
        state.updateAvailable -> "发现新版本 ${release?.versionName.orEmpty()}"
        release != null -> "当前已是最新版本"
        state.lastCheckedAt != null -> "服务器暂未发布可用版本"
        else -> "等待检查更新"
    }
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app-version-card"),
        elevated = state.required,
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeading(
                title = "应用版本",
                subtitle = "当前 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                trailing = {
                    Icon(
                        Icons.Outlined.PhoneAndroid,
                        contentDescription = null,
                        tint = if (state.required) glassColors.negative else glassColors.positive,
                    )
                },
            )

            DetailGrid(
                listOf(
                    "版本状态" to statusText,
                    "最近检查" to (state.lastCheckedAt?.let(::formatTime) ?: "尚未检查"),
                ),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("自动更新", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "启动时检查，发现新版本后自动下载并打开系统安装器",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = autoUpdateEnabled,
                    onCheckedChange = viewModel::setAutoUpdateEnabled,
                    modifier = Modifier.testTag("auto-update-switch"),
                )
            }

            state.message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.updateAvailable) {
                        glassColors.positive
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            state.error?.let { WarningMessage(it) }

            release?.releaseNotes
                ?.takeIf { state.updateAvailable && it.isNotBlank() }
                ?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            GlassButton(
                text = if (state.checking) "正在检查更新" else "检查更新",
                onClick = viewModel::checkForUpdate,
                enabled = !state.checking && !state.downloading,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Outlined.Refresh,
                style = GlassButtonStyle.SECONDARY,
            )

            if (state.updateAvailable && release != null) {
                GlassButton(
                    text = when {
                        state.downloading -> "正在下载并校验"
                        state.downloadedFile != null -> "打开系统安装器"
                        else -> "更新到 ${release.versionName}"
                    },
                    onClick = {
                        if (state.downloadedFile != null) {
                            viewModel.installDownloadedUpdate()
                        } else {
                            viewModel.downloadUpdate()
                        }
                    },
                    enabled = !state.downloading && release.downloadUrl != null,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Download,
                    style = if (state.required) {
                        GlassButtonStyle.DANGER
                    } else {
                        GlassButtonStyle.PRIMARY
                    },
                )
            }
        }
    }
}

@Composable
private fun ConnectionHero(state: DeviceConnectionState) {
    val paired = state.pairingState == PairingState.PAIRED
    val accent = when (state.pairingState) {
        PairingState.PAIRED -> glassColors.positive
        PairingState.REPAIR_REQUIRED -> glassColors.negative
        PairingState.UNPAIRED -> glassColors.warning
    }
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        elevated = true,
        shape = RoundedCornerShape(32.dp),
    ) {
        Column(
            Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (paired) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        state.pairingState.displayName(),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        state.credentials?.serverUrl ?: "尚未绑定服务器",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(color = glassColors.border)
            DetailGrid(
                listOf(
                    "所属商户" to listOfNotNull(
                        state.credentials?.merchantName,
                        state.credentials?.merchantCode?.let { "($it)" },
                    ).joinToString(" ").ifBlank { "-" },
                    "设备 ID" to (state.credentials?.deviceId?.toString() ?: "-"),
                    "设备角色" to when (state.credentials?.deviceRole) {
                        "PRIMARY" -> "主设备"
                        "BACKUP" -> "备用设备"
                        else -> "未配置"
                    },
                    "监听平台" to (state.credentials?.platformScope
                        ?.replace("WECHAT", "微信")
                        ?.replace("ALIPAY", "支付宝")
                        ?: "全部"),
                    "最后心跳" to formatTimeOrDash(state.lastHeartbeatAt),
                    "最后同步" to formatTimeOrDash(state.lastSyncAt),
                ),
            )
            state.lastErrorMessage?.let { WarningMessage(it) }
            Text(
                if (state.credentials?.config?.rawPayloadUploadEnabled == true) {
                    "服务器允许上传通知原文"
                } else {
                    "仅上传解析字段和哈希"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QueueMetrics(counts: Map<String, Int>) {
    val pending = counts["PENDING"] ?: 0
    val retrying = counts["RETRYING"] ?: 0
    val rejected = counts["REJECTED"] ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeading(title = "上传队列", subtitle = "本地事件同步概览")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassMetric("待上传", pending.toString(), Modifier.weight(1f))
            GlassMetric("重试", retrying.toString(), Modifier.weight(1f), glassColors.warning)
            GlassMetric("拒绝", rejected.toString(), Modifier.weight(1f), glassColors.negative)
        }
        if (pending + retrying > 1_000) {
            WarningMessage("未同步事件超过 1,000 条，请检查网络、配对状态和服务地址。")
        }
    }
}

@Composable
private fun DetailGrid(items: List<Pair<String, String>>) {
    items.chunked(2).forEach { rowItems ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            rowItems.forEach { (label, value) ->
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun OperationFeedback(operation: PairingOperationState) {
    AnimatedVisibility(
        visible = operation.message != null || operation.error != null,
        enter = fadeIn() + slideInVertically { -it / 5 },
        exit = fadeOut(),
    ) {
        val error = operation.error
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (error != null) glassColors.negative.copy(alpha = 0.12f)
                    else glassColors.positive.copy(alpha = 0.12f),
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (error != null) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = if (error != null) glassColors.negative else glassColors.positive,
            )
            Text(
                error ?: operation.message.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventsTab(
    viewModel: MainViewModel,
    padding: PaddingValues,
) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val platform by viewModel.selectedPlatformFilter.collectAsStateWithLifecycle()
    val direction by viewModel.selectedDirectionFilter.collectAsStateWithLifecycle()
    val upload by viewModel.selectedUploadFilter.collectAsStateWithLifecycle()
    var selectedEvent by remember { mutableStateOf<PaymentEvent?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AnimatedSection(0) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GlassFilterRow(
                            entries = PlatformFilter.entries,
                            selected = platform,
                            onSelect = viewModel::setPlatformFilter,
                            label = PlatformFilter::displayName,
                        )
                        GlassFilterRow(
                            entries = DirectionFilter.entries,
                            selected = direction,
                            onSelect = viewModel::setDirectionFilter,
                            label = DirectionFilter::displayName,
                        )
                        GlassFilterRow(
                            entries = UploadFilter.entries,
                            selected = upload,
                            onSelect = viewModel::setUploadFilter,
                            label = UploadFilter::displayName,
                        )
                    }
                }
            }
        }
        item {
            SectionHeading(
                title = "支付记录",
                subtitle = "当前筛选 ${events.size} 条",
                trailing = {
                    TextButton(onClick = viewModel::clearHistory) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("清除终态")
                    }
                },
            )
        }
        if (events.isEmpty()) {
            item {
                EmptyEvents()
            }
        } else {
            itemsIndexed(
                items = events,
                key = { _, event -> event.clientEventId.ifBlank { event.fingerprint } },
            ) { index, event ->
                AnimatedEventCard(
                    event = event,
                    index = index,
                    onClick = { selectedEvent = event },
                )
            }
        }
    }

    selectedEvent?.let { event ->
        EventDetailSheet(
            event = event,
            onDismiss = { selectedEvent = null },
            onRetry = {
                viewModel.retryEvent(event.clientEventId)
                selectedEvent = null
            },
        )
    }
}

@Composable
internal fun <T> GlassFilterRow(
    entries: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        entries.forEach { item ->
            val isSelected = selected == item
            val background by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    glassColors.card
                },
                label = "filter-background",
            )
            val horizontalPadding by animateDpAsState(
                targetValue = if (isSelected) 17.dp else 13.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
                label = "filter-padding",
            )
            Row(
                Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(background)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(item) },
                    )
                    .padding(horizontal = horizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isSelected) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
                Text(
                    label(item),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AnimatedEventCard(
    event: PaymentEvent,
    index: Int,
    onClick: () -> Unit,
) {
    var visible by remember(event.clientEventId) { mutableStateOf(false) }
    LaunchedEffect(event.clientEventId) {
        delay((index.coerceAtMost(8) * 35L))
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 4 },
    ) {
        PaymentEventCard(event, onClick)
    }
}

@Composable
private fun PaymentEventCard(
    event: PaymentEvent,
    onClick: () -> Unit,
) {
    val income = event.direction == PaymentDirection.INCOME
    val accent = when (event.direction) {
        PaymentDirection.INCOME -> glassColors.positive
        PaymentDirection.EXPENSE -> glassColors.negative
        PaymentDirection.UNKNOWN -> glassColors.warning
    }
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        when (event.direction) {
                            PaymentDirection.INCOME -> Icons.Rounded.ArrowDownward
                            PaymentDirection.EXPENSE -> Icons.Rounded.ArrowUpward
                            PaymentDirection.UNKNOWN -> Icons.Outlined.MoreHoriz
                        },
                        contentDescription = event.direction.displayName(),
                        tint = accent,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "${event.platform.displayName()} · ${event.direction.displayName()}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        formatTime(event.receivedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    event.amount?.let { "${if (income) "+" else if (event.direction == PaymentDirection.EXPENSE) "-" else ""}￥${it.toPlainString()}" }
                        ?: "金额未识别",
                    style = MaterialTheme.typography.titleLarge,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                event.raw.text ?: event.raw.bigText ?: "无通知正文",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UploadStatusBadge(event.uploadStatus)
                Icon(
                    Icons.Outlined.History,
                    contentDescription = "查看详情",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@Composable
private fun UploadStatusBadge(status: UploadStatus) {
    val accent = when (status) {
        UploadStatus.UPLOADED -> glassColors.positive
        UploadStatus.REJECTED -> glassColors.negative
        UploadStatus.RETRYING -> glassColors.warning
        UploadStatus.PENDING, UploadStatus.UPLOADING -> MaterialTheme.colorScheme.primary
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(accent, CircleShape))
        Text(status.displayName(), style = MaterialTheme.typography.labelSmall, color = accent)
    }
}

@Composable
private fun EmptyEvents() {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 38.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Inbox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(31.dp),
                )
            }
            Text("暂无支付记录", style = MaterialTheme.typography.titleMedium)
            Text(
                "收到符合规则的微信或支付宝通知后会显示在这里",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventDetailSheet(
    event: PaymentEvent,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        scrimColor = Color.Black.copy(alpha = 0.48f),
        modifier = Modifier.testTag("event-detail-sheet"),
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 42.dp, height = 4.dp)
                    .background(glassColors.strongBorder, CircleShape),
            )
        },
    ) {
        SelectionContainer {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionHeading(
                    title = "支付通知详情",
                    subtitle = event.clientEventId,
                    trailing = {
                        Icon(
                            Icons.Rounded.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        DetailGrid(
                            listOf(
                                "平台" to event.platform.displayName(),
                                "方向" to event.direction.displayName(),
                                "金额" to (event.amount?.let { "￥${it.toPlainString()}" } ?: "未识别"),
                                "上传状态" to event.uploadStatus.displayName(),
                                "尝试次数" to event.attemptCount.toString(),
                                "匹配规则" to event.matchedRule,
                            ),
                        )
                    }
                }
                event.lastErrorMessage?.let {
                    WarningMessage("${event.lastErrorCode.orEmpty()} $it".trim())
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        DetailLine("标题", event.raw.title.orEmpty())
                        DetailLine("正文", event.raw.text.orEmpty())
                        DetailLine("大文本", event.raw.bigText.orEmpty())
                        HorizontalDivider(color = glassColors.border)
                        DetailLine("事件指纹", event.fingerprint)
                        DetailLine("原文哈希", event.rawHash.orEmpty())
                    }
                }
                if (event.uploadStatus == UploadStatus.REJECTED ||
                    event.uploadStatus == UploadStatus.RETRYING
                ) {
                    GlassButton(
                        text = "重新上传",
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.Replay,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value.ifBlank { "-" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun PlatformFilter.displayName() = when (this) {
    PlatformFilter.ALL -> "全部平台"
    PlatformFilter.WECHAT -> "微信"
    PlatformFilter.ALIPAY -> "支付宝"
}

private fun DirectionFilter.displayName() = when (this) {
    DirectionFilter.ALL -> "全部方向"
    DirectionFilter.INCOME -> "收入"
    DirectionFilter.EXPENSE -> "支出"
    DirectionFilter.UNKNOWN -> "未知"
}

private fun UploadFilter.displayName() = when (this) {
    UploadFilter.ALL -> "全部状态"
    UploadFilter.PENDING -> "待上传"
    UploadFilter.RETRYING -> "重试中"
    UploadFilter.UPLOADED -> "已上传"
    UploadFilter.REJECTED -> "已拒绝"
}

private fun PaymentPlatform.displayName() = if (name == "WECHAT") "微信" else "支付宝"

private fun PaymentDirection.displayName() = when (name) {
    "INCOME" -> "收入"
    "EXPENSE" -> "支出"
    else -> "未知"
}

private fun UploadStatus.displayName() = when (this) {
    UploadStatus.PENDING -> "待上传"
    UploadStatus.UPLOADING -> "上传中"
    UploadStatus.RETRYING -> "等待重试"
    UploadStatus.UPLOADED -> "已上传"
    UploadStatus.REJECTED -> "已拒绝"
}

private fun PairingState.displayName() = when (this) {
    PairingState.UNPAIRED -> "未配对"
    PairingState.PAIRED -> "已安全连接"
    PairingState.REPAIR_REQUIRED -> "需要重新配对"
}

private fun formatTime(value: Long): String =
    SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(value))

private fun formatTimeOrDash(value: Long?): String = value?.let(::formatTime) ?: "-"
