package com.example.paymentmonitor.sync

import android.app.Instrumentation
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.paymentmonitor.PaymentMonitorApplication
import com.example.paymentmonitor.debug.DebugFixtureFactory
import com.example.paymentmonitor.debug.DebugFixtureType
import com.example.paymentmonitor.monitor.MonitorForegroundService
import com.example.paymentmonitor.monitor.MonitorRuntimeState
import com.example.paymentmonitor.monitor.PaymentNotificationParser
import java.io.FileInputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceEndToEndTest {

    private val instrumentation: Instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val app: PaymentMonitorApplication
        get() = instrumentation.targetContext.applicationContext as PaymentMonitorApplication

    @Test
    fun pairAndUploadFourFixtures() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val serverUrl = requireNotNull(arguments.getString("serverUrl"))
        val pairingCode = requireNotNull(arguments.getString("pairingCode"))
        app.deviceRepository.clearPairing()

        val pairing = app.deviceRepository.pair(serverUrl, pairingCode)
        assertTrue(pairing.deviceId > 0)
        assertTrue(pairing.credentialVersion > 0)

        val clientEventIds = DebugFixtureType.entries.mapIndexed { index, type ->
            val raw = DebugFixtureFactory.create(
                type = type,
                timestamp = System.currentTimeMillis() + index,
            )
            val event = requireNotNull(PaymentNotificationParser.parse(raw))
            assertTrue(app.repository.save(event))
            requireNotNull(
                app.database.paymentEventDao().getAll()
                    .firstOrNull { row -> row.notificationKey == raw.notificationKey },
            ).clientEventId
        }
        app.syncScheduler.enqueueNow()

        var lastForcedAt = 0L
        waitUntil(TimeUnit.MINUTES.toMillis(2)) {
            val now = System.currentTimeMillis()
            if (now - lastForcedAt >= 5_000) {
                forceScheduledSync()
                lastForcedAt = now
            }
            val rows = app.database.paymentEventDao().getAll()
                .filter { it.clientEventId in clientEventIds }
            rows.size == clientEventIds.size && rows.all { it.uploadStatus == "UPLOADED" }
        }
        val uploadedRows = app.database.paymentEventDao().getAll()
            .filter { it.clientEventId in clientEventIds }
        assertEquals(4, uploadedRows.size)
        assertEquals(setOf("WECHAT", "ALIPAY"), uploadedRows.map { it.platform }.toSet())
        assertEquals(setOf("INCOME", "EXPENSE"), uploadedRows.map { it.direction }.toSet())
        println("E2E_DEVICE_ID=${pairing.deviceId}")
        println("E2E_CLIENT_EVENT_IDS=${clientEventIds.joinToString(",")}")
    }

    @Test
    fun incomeUsesImmediateSyncWhileExpenseStaysDeferred() = runBlocking {
        assertEquals(PairingState.PAIRED, app.deviceStateStore.snapshot().pairingState)
        val baseTimestamp = System.currentTimeMillis()
        val expenseRaw = DebugFixtureFactory.create(
            type = DebugFixtureType.WECHAT_EXPENSE,
            timestamp = baseTimestamp,
        )
        val incomeRaw = DebugFixtureFactory.create(
            type = DebugFixtureType.WECHAT_INCOME,
            timestamp = baseTimestamp + 1,
        )
        assertTrue(app.repository.save(requireNotNull(PaymentNotificationParser.parse(expenseRaw))))
        assertTrue(app.repository.save(requireNotNull(PaymentNotificationParser.parse(incomeRaw))))

        val rows = app.database.paymentEventDao().getAll()
        val expenseId = requireNotNull(
            rows.firstOrNull { it.notificationKey == expenseRaw.notificationKey },
        ).clientEventId
        val incomeId = requireNotNull(
            rows.firstOrNull { it.notificationKey == incomeRaw.notificationKey },
        ).clientEventId

        waitUntil(TimeUnit.SECONDS.toMillis(10)) {
            app.database.paymentEventDao().getAll()
                .firstOrNull { it.clientEventId == incomeId }
                ?.uploadStatus == "UPLOADED"
        }
        val deferredExpense = requireNotNull(
            app.database.paymentEventDao().getAll()
                .firstOrNull { it.clientEventId == expenseId },
        )
        assertTrue(deferredExpense.uploadStatus in setOf("PENDING", "RETRYING"))

        val worker = TestListenableWorkerBuilder
            .from(app, PaymentSyncWorker::class.java)
            .setInputData(
                androidx.work.workDataOf(
                    SyncScheduler.INPUT_SELECTION to SyncSelection.ALL.name,
                ),
            )
            .build()
        assertTrue(worker.doWork() is ListenableWorker.Result.Success)
        waitUntil(TimeUnit.SECONDS.toMillis(10)) {
            app.database.paymentEventDao().getAll()
                .firstOrNull { it.clientEventId == expenseId }
                ?.uploadStatus == "UPLOADED"
        }
        println("IMMEDIATE_INCOME_CLIENT_EVENT_ID=$incomeId")
        println("DEFERRED_EXPENSE_CLIENT_EVENT_ID=$expenseId")
    }

    @Test
    fun offlineQueueUploadsAfterWifiReturns() = runBlocking {
        assertEquals(PairingState.PAIRED, app.deviceStateStore.snapshot().pairingState)
        val wifiWasEnabled = readGlobalSetting("wifi_on") == "1"
        val mobileDataWasEnabled = readGlobalSetting("mobile_data") == "1"
        val emulator = Build.FINGERPRINT.contains("generic") ||
            Build.MODEL.contains("sdk_gphone")
        val airplaneModeWasEnabled = emulator &&
            readGlobalSetting("airplane_mode_on") == "1"
        if (emulator) setAirplaneModeEnabled(true)
        setWifiEnabled(false)
        setMobileDataEnabled(false)
        try {
            waitUntil(TimeUnit.SECONDS.toMillis(30)) { !internetConnected() }
            val raw = DebugFixtureFactory.create(
                type = DebugFixtureType.WECHAT_INCOME,
                timestamp = System.currentTimeMillis(),
            )
            val event = requireNotNull(PaymentNotificationParser.parse(raw))
            assertTrue(app.repository.save(event))
            val clientEventId = requireNotNull(
                app.database.paymentEventDao().getAll()
                    .firstOrNull { row -> row.notificationKey == raw.notificationKey },
            ).clientEventId

            delay(8_000)
            val offlineRow = requireNotNull(
                app.database.paymentEventDao().getAll()
                    .firstOrNull { it.clientEventId == clientEventId },
            )
            assertTrue(offlineRow.uploadStatus in setOf("PENDING", "RETRYING"))

            if (emulator) setAirplaneModeEnabled(false)
            setWifiEnabled(true)
            setMobileDataEnabled(true)
            waitUntil(TimeUnit.MINUTES.toMillis(1)) {
                if (emulator) networkRouteAvailable() else wifiConnected()
            }
            if (emulator) {
                WorkManager.getInstance(app)
                    .cancelUniqueWork(SyncScheduler.WORK_NAME)
                    .result
                    .get(10, TimeUnit.SECONDS)
                val worker = TestListenableWorkerBuilder
                    .from(app, PaymentSyncWorker::class.java)
                    .build()
                assertTrue(worker.doWork() is ListenableWorker.Result.Success)
            } else {
                app.syncScheduler.enqueueNow()
            }
            waitUntil(TimeUnit.MINUTES.toMillis(2)) {
                app.database.paymentEventDao().getAll()
                    .firstOrNull { it.clientEventId == clientEventId }
                    ?.uploadStatus == "UPLOADED"
            }
            println("OFFLINE_RECOVERY_CLIENT_EVENT_ID=$clientEventId")
        } finally {
            if (emulator) setAirplaneModeEnabled(false)
            setWifiEnabled(wifiWasEnabled)
            setMobileDataEnabled(mobileDataWasEnabled)
            if (emulator) setAirplaneModeEnabled(airplaneModeWasEnabled)
        }
    }

    @Test
    fun heartbeatMarksRepairRequiredAfterServerRevocation() = runBlocking {
        assertEquals(PairingState.PAIRED, app.deviceStateStore.snapshot().pairingState)
        val failure = runCatching { app.deviceRepository.heartbeat() }.exceptionOrNull()
        val clientFailure = failure as? ClientApiException
        assertNotNull(clientFailure)
        assertTrue(
            clientFailure?.code in setOf("DEVICE_DISABLED", "CREDENTIAL_REVOKED"),
        )
        assertEquals(
            PairingState.REPAIR_REQUIRED,
            app.deviceStateStore.snapshot().pairingState,
        )
        println("REPAIR_REQUIRED_ERROR=${clientFailure?.code}")
    }

    @Test
    fun repairAfterServerReenabledIncrementsCredentialVersion() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val serverUrl = requireNotNull(arguments.getString("serverUrl"))
        val pairingCode = requireNotNull(arguments.getString("pairingCode"))
        val before = requireNotNull(app.deviceStateStore.snapshot().credentials)
        assertEquals(PairingState.REPAIR_REQUIRED, app.deviceStateStore.snapshot().pairingState)

        val repaired = app.deviceRepository.pair(serverUrl, pairingCode)

        assertEquals(before.deviceId, repaired.deviceId)
        assertTrue(repaired.credentialVersion > before.credentialVersion)
        assertEquals(PairingState.PAIRED, app.deviceStateStore.snapshot().pairingState)
        println("REPAIRED_DEVICE_ID=${repaired.deviceId}")
        println("REPAIRED_CREDENTIAL_VERSION=${repaired.credentialVersion}")
    }

    @Test
    fun migrateToNewServerAddressKeepsDeviceIdentity() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val serverUrl = requireNotNull(arguments.getString("serverUrl"))
        val pairingCode = requireNotNull(arguments.getString("pairingCode"))
        val before = requireNotNull(app.deviceStateStore.snapshot().credentials)

        app.deviceStateStore.markRepairRequired(
            code = "SERVER_ADDRESS_CHANGED",
            message = "服务地址已更新，需要重新配对",
        )
        val repaired = app.deviceRepository.pair(serverUrl, pairingCode)
        val after = requireNotNull(app.deviceStateStore.snapshot().credentials)

        assertEquals(before.deviceId, repaired.deviceId)
        assertTrue(repaired.credentialVersion > before.credentialVersion)
        assertEquals(ServerUrlValidator.normalize(serverUrl), after.serverUrl)
        assertEquals(PairingState.PAIRED, app.deviceStateStore.snapshot().pairingState)
        app.deviceRepository.heartbeat()
        println("MIGRATED_DEVICE_ID=${repaired.deviceId}")
        println("MIGRATED_SERVER_URL=${after.serverUrl}")
        println("MIGRATED_CREDENTIAL_VERSION=${repaired.credentialVersion}")
    }

    @Test
    fun foregroundMonitorServiceStartsAndStops() = runBlocking {
        app.monitoringPreferences.setEnabled(true)
        withContext(Dispatchers.Main.immediate) {
            MonitorForegroundService.start(app)
        }
        waitUntil(TimeUnit.SECONDS.toMillis(15)) {
            MonitorRuntimeState.foregroundRunning.value
        }
        assertTrue(app.monitoringPreferences.isEnabled())

        app.monitoringPreferences.setEnabled(false)
        withContext(Dispatchers.Main.immediate) {
            MonitorForegroundService.stop(app)
        }
        waitUntil(TimeUnit.SECONDS.toMillis(15)) {
            !MonitorRuntimeState.foregroundRunning.value
        }
        assertFalse(app.monitoringPreferences.isEnabled())
    }

    private fun setWifiEnabled(enabled: Boolean) {
        executeShellCommand("svc wifi ${if (enabled) "enable" else "disable"}")
    }

    private fun setMobileDataEnabled(enabled: Boolean) {
        executeShellCommand("svc data ${if (enabled) "enable" else "disable"}")
    }

    private fun setAirplaneModeEnabled(enabled: Boolean) {
        executeShellCommand(
            "cmd connectivity airplane-mode ${if (enabled) "enable" else "disable"}",
        )
    }

    private fun readGlobalSetting(name: String): String =
        executeShellCommand("settings get global $name")

    private fun executeShellCommand(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { reader ->
                reader.readText().trim()
            }
        }

    private fun forceScheduledSync() {
        val jobIds = Regex(
            """#u\d+a\d+/(\d+)\s+com\.example\.paymentmonitor/androidx\.work\.impl\.background\.systemjob\.SystemJobService""",
        ).findAll(executeShellCommand("dumpsys jobscheduler com.example.paymentmonitor"))
            .map { match -> match.groupValues[1] }
            .distinct()
            .toList()
        jobIds.forEach { jobId ->
            executeShellCommand(
                "cmd jobscheduler run -f com.example.paymentmonitor $jobId",
            )
        }
    }

    private fun internetConnected(): Boolean {
        val connectivity = app.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun networkRouteAvailable(): Boolean {
        val connectivity = app.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun wifiConnected(): Boolean {
        val connectivity = app.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private suspend fun waitUntil(timeoutMillis: Long, condition: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            delay(1_000)
        }
        error("Condition was not met within ${timeoutMillis}ms")
    }
}
