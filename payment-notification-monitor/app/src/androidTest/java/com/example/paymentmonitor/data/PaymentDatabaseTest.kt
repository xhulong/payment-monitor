package com.example.paymentmonitor.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.paymentmonitor.model.ParseStatus
import com.example.paymentmonitor.model.PaymentDirection
import com.example.paymentmonitor.model.PaymentPlatform
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentDatabaseTest {
    private lateinit var database: PaymentDatabase
    private lateinit var dao: PaymentEventDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PaymentDatabase::class.java,
        ).build()
        dao = database.paymentEventDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun duplicateFingerprintIsIgnored() {
        runBlocking {
            assertTrue(dao.insertAndTrim(entity(1), 500))
            assertFalse(dao.insertAndTrim(entity(1), 500))
            assertEquals(1, dao.getAll().size)
        }
    }

    @Test
    fun notificationUpdateWithSameIdentityIsDetected() = runBlocking {
        val original = entity(10_000).copy(
            fingerprint = "identity-original",
            clientEventId = "identity-original",
            receivedAt = 1,
            notificationKey = "same-notification-key",
            postTime = 1_700_000_000_123,
            direction = "INCOME",
            amount = "8.88",
        )
        dao.insertAndTrim(original, 500)

        assertTrue(
            dao.existsNotificationIdentity(
                sourcePackage = original.sourcePackage,
                notificationKey = "same-notification-key",
                postTime = 1_700_000_000_123,
                direction = "INCOME",
                amount = "8.88",
            ),
        )
        assertFalse(
            dao.existsNotificationIdentity(
                sourcePackage = original.sourcePackage,
                notificationKey = "same-notification-key",
                postTime = 1_700_000_000_124,
                direction = "INCOME",
                amount = "8.88",
            ),
        )
        assertFalse(
            dao.existsNotificationIdentity(
                sourcePackage = original.sourcePackage,
                notificationKey = "same-notification-key",
                postTime = 1_700_000_000_123,
                direction = "INCOME",
                amount = "8.89",
            ),
        )
    }

    @Test
    fun historyIsTrimmedToFiveHundredNewestRows() {
        runBlocking {
            repeat(505) { index ->
                dao.insertAndTrim(entity(index.toLong()), 500)
            }

            val rows = dao.getAll()
            assertEquals(500, rows.size)
            assertEquals("fingerprint-504", rows.first().fingerprint)
            assertEquals("fingerprint-5", rows.last().fingerprint)
        }
    }

    @Test
    fun rowsPersistAfterDatabaseReopenAndCanBeCleared() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val databaseName = "payment-persistence-test.db"
            context.deleteDatabase(databaseName)
            val first = Room.databaseBuilder(context, PaymentDatabase::class.java, databaseName).build()
            first.paymentEventDao().insertAndTrim(entity(99), 500)
            first.close()

            val reopened = Room.databaseBuilder(context, PaymentDatabase::class.java, databaseName).build()
            assertEquals(1, reopened.paymentEventDao().getAll().size)
            reopened.paymentEventDao().clearTerminalHistory()
            assertTrue(reopened.paymentEventDao().getAll().isEmpty())
            reopened.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun pendingAndRetryingRowsAreNeverTrimmedOrClearedAsHistory() {
        runBlocking {
            repeat(1_005) { index ->
                dao.insertAndTrim(entity(index.toLong(), uploadStatus = "PENDING"), 500)
            }
            dao.insertAndTrim(entity(2_000, uploadStatus = "RETRYING"), 500)
            repeat(510) { index ->
                dao.insertAndTrim(entity(3_000L + index, uploadStatus = "UPLOADED"), 500)
            }

            var rows = dao.getAll()
            assertEquals(1_506, rows.size)
            assertEquals(1_005, rows.count { it.uploadStatus == "PENDING" })
            assertEquals(1, rows.count { it.uploadStatus == "RETRYING" })
            assertEquals(500, rows.count { it.uploadStatus == "UPLOADED" })

            dao.clearTerminalHistory()
            rows = dao.getAll()
            assertEquals(1_006, rows.size)
            assertEquals(1_005, rows.count { it.uploadStatus == "PENDING" })
            assertEquals(1, rows.count { it.uploadStatus == "RETRYING" })
        }
    }

    @Test
    fun deviceSequenceIsMonotonicAndSurvivesDatabaseReopen() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val databaseName = "payment-sequence-test.db"
            context.deleteDatabase(databaseName)
            val first = Room.databaseBuilder(context, PaymentDatabase::class.java, databaseName)
                .addMigrations(PaymentDatabase.MIGRATION_1_2, PaymentDatabase.MIGRATION_2_3)
                .build()
            first.paymentEventDao().insertAndTrim(entity(10_001, "PENDING"), 500)
            first.paymentEventDao().insertAndTrim(entity(10_002, "PENDING"), 500)
            val beforeRestart = first.paymentEventDao().getAll()
                .sortedBy { it.deviceSequence }
                .mapNotNull { it.deviceSequence }
            first.close()

            val reopened = Room.databaseBuilder(context, PaymentDatabase::class.java, databaseName)
                .addMigrations(PaymentDatabase.MIGRATION_1_2, PaymentDatabase.MIGRATION_2_3)
                .build()
            reopened.paymentEventDao().insertAndTrim(entity(10_003, "PENDING"), 500)
            val afterRestart = reopened.paymentEventDao().getAll()
                .sortedBy { it.deviceSequence }
                .mapNotNull { it.deviceSequence }

            assertEquals(2, beforeRestart.size)
            assertEquals(3, afterRestart.size)
            assertTrue(beforeRestart[0] < beforeRestart[1])
            assertTrue(afterRestart[1] < afterRestart[2])
            assertEquals(beforeRestart, afterRestart.take(2))
            reopened.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun entity(
        index: Long,
        uploadStatus: String = "UPLOADED",
    ): PaymentEventEntity = PaymentEventEntity(
        fingerprint = "fingerprint-$index",
        clientEventId = "client-$index",
        parserVersion = "1",
        notificationKeyHash = null,
        rawHash = "raw-$index",
        uploadStatus = uploadStatus,
        attemptCount = 0,
        nextAttemptAt = null,
        lastAttemptAt = null,
        uploadedAt = index,
        lastErrorCode = null,
        lastErrorMessage = null,
        platform = PaymentPlatform.WECHAT.name,
        direction = PaymentDirection.INCOME.name,
        parseStatus = ParseStatus.PARSED.name,
        amount = "1.00",
        currency = "CNY",
        matchedRule = "test",
        receivedAt = index,
        sourcePackage = "com.tencent.mm",
        notificationId = index.toInt(),
        notificationTag = null,
        notificationKey = "key-$index",
        postTime = index,
        title = "微信收款助手",
        text = "收款1.00元",
        bigText = null,
        textLines = "[]",
        ticker = null,
        subText = null,
        infoText = null,
        summaryText = null,
        extrasText = "{}",
    )
}
