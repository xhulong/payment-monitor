package com.example.paymentmonitor.data

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PaymentDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrateVersionOneToThreePreservesRawFieldsAndBackfillsDeviceSequence() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO payment_events (
                    fingerprint, platform, direction, parseStatus, amount, currency,
                    matchedRule, receivedAt, sourcePackage, notificationId,
                    notificationTag, notificationKey, postTime, title, text, bigText,
                    textLines, ticker, subText, infoText, summaryText, extrasText
                ) VALUES (
                    'legacy-fingerprint', 'WECHAT', 'INCOME', 'PARSED', '8.88', 'CNY',
                    'legacy-rule', 1000, 'com.tencent.mm', 7,
                    'legacy-tag', 'legacy-key', 900, 'Legacy title', 'Legacy text',
                    'Legacy big text', '["line-1","line-2"]', 'Legacy ticker',
                    'Legacy subtext', 'Legacy info', 'Legacy summary', '{"legacy":"value"}'
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            3,
            true,
            PaymentDatabase.MIGRATION_1_2,
            PaymentDatabase.MIGRATION_2_3,
        ).apply {
            query(
                """
                SELECT fingerprint, clientEventId, parserVersion, uploadStatus,
                       attemptCount, rawHash, deviceSequence, id,
                       title, text, bigText, textLines, extrasText
                FROM payment_events
                """.trimIndent(),
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("legacy-fingerprint", cursor.string("fingerprint"))
                assertEquals("legacy-fingerprint", cursor.string("clientEventId"))
                assertEquals("legacy-v1", cursor.string("parserVersion"))
                assertEquals("PENDING", cursor.string("uploadStatus"))
                assertEquals(0, cursor.int("attemptCount"))
                assertNull(cursor.nullableString("rawHash"))
                assertEquals(cursor.long("id"), cursor.long("deviceSequence"))
                assertEquals("Legacy title", cursor.string("title"))
                assertEquals("Legacy text", cursor.string("text"))
                assertEquals("Legacy big text", cursor.string("bigText"))
                assertEquals("[\"line-1\",\"line-2\"]", cursor.string("textLines"))
                assertEquals("{\"legacy\":\"value\"}", cursor.string("extrasText"))
            }
            close()
        }
    }

    @Test
    fun migrateVersionTwoToThreeBackfillsStableSequenceFromLocalRowId() {
        helper.createDatabase(TEST_DATABASE_V2, 2).apply {
            execSQL(
                """
                INSERT INTO payment_events (
                    fingerprint, clientEventId, parserVersion, notificationKeyHash,
                    rawHash, uploadStatus, attemptCount, nextAttemptAt, lastAttemptAt,
                    uploadedAt, lastErrorCode, lastErrorMessage, platform, direction,
                    parseStatus, amount, currency, matchedRule, receivedAt, sourcePackage,
                    notificationId, notificationTag, notificationKey, postTime, title,
                    text, bigText, textLines, ticker, subText, infoText, summaryText,
                    extrasText
                ) VALUES (
                    'v2-fingerprint', 'v2-client-event', '2', NULL,
                    'v2-raw-hash', 'PENDING', 0, NULL, NULL,
                    NULL, NULL, NULL, 'ALIPAY', 'INCOME',
                    'PARSED', '1.23', 'CNY', 'v2-rule', 2000, 'com.eg.android.AlipayGphone',
                    8, NULL, 'v2-key', 1900, '支付宝收款',
                    '成功收款1.23元', NULL, '[]', NULL, NULL, NULL, NULL,
                    '{}'
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_V2,
            3,
            true,
            PaymentDatabase.MIGRATION_2_3,
        ).apply {
            query("SELECT id, deviceSequence FROM payment_events").use { cursor ->
                cursor.moveToFirst()
                assertEquals(cursor.long("id"), cursor.long("deviceSequence"))
            }
            close()
        }
    }

    private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))

    private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

    private fun Cursor.nullableString(column: String): String? =
        getColumnIndexOrThrow(column).let { index ->
            if (isNull(index)) null else getString(index)
        }

    private companion object {
        const val TEST_DATABASE = "payment-migration-test.db"
        const val TEST_DATABASE_V2 = "payment-migration-v2-test.db"
    }
}
