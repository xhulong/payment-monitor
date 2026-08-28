package com.example.paymentmonitor.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PaymentEventEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class PaymentDatabase : RoomDatabase() {
    abstract fun paymentEventDao(): PaymentEventDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS payment_events_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        fingerprint TEXT NOT NULL,
                        clientEventId TEXT NOT NULL,
                        parserVersion TEXT NOT NULL,
                        notificationKeyHash TEXT,
                        rawHash TEXT,
                        uploadStatus TEXT NOT NULL,
                        attemptCount INTEGER NOT NULL,
                        nextAttemptAt INTEGER,
                        lastAttemptAt INTEGER,
                        uploadedAt INTEGER,
                        lastErrorCode TEXT,
                        lastErrorMessage TEXT,
                        platform TEXT NOT NULL,
                        direction TEXT NOT NULL,
                        parseStatus TEXT NOT NULL,
                        amount TEXT,
                        currency TEXT NOT NULL,
                        matchedRule TEXT NOT NULL,
                        receivedAt INTEGER NOT NULL,
                        sourcePackage TEXT NOT NULL,
                        notificationId INTEGER NOT NULL,
                        notificationTag TEXT,
                        notificationKey TEXT,
                        postTime INTEGER NOT NULL,
                        title TEXT,
                        text TEXT,
                        bigText TEXT,
                        textLines TEXT NOT NULL,
                        ticker TEXT,
                        subText TEXT,
                        infoText TEXT,
                        summaryText TEXT,
                        extrasText TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT INTO payment_events_new (
                        id, fingerprint, clientEventId, parserVersion,
                        notificationKeyHash, rawHash, uploadStatus, attemptCount,
                        nextAttemptAt, lastAttemptAt, uploadedAt, lastErrorCode,
                        lastErrorMessage, platform, direction, parseStatus, amount,
                        currency, matchedRule, receivedAt, sourcePackage,
                        notificationId, notificationTag, notificationKey, postTime,
                        title, text, bigText, textLines, ticker, subText, infoText,
                        summaryText, extrasText
                    )
                    SELECT
                        id, fingerprint, fingerprint, 'legacy-v1',
                        NULL, NULL, 'PENDING', 0,
                        NULL, NULL, NULL, NULL,
                        NULL, platform, direction, parseStatus, amount,
                        currency, matchedRule, receivedAt, sourcePackage,
                        notificationId, notificationTag, notificationKey, postTime,
                        title, text, bigText, textLines, ticker, subText, infoText,
                        summaryText, extrasText
                    FROM payment_events
                    """.trimIndent(),
                )
                database.execSQL("DROP TABLE payment_events")
                database.execSQL("ALTER TABLE payment_events_new RENAME TO payment_events")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_payment_events_fingerprint ON payment_events (fingerprint)",
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_payment_events_clientEventId ON payment_events (clientEventId)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_payment_events_receivedAt ON payment_events (receivedAt)",
                )
                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_payment_events_uploadStatus_nextAttemptAt_receivedAt
                    ON payment_events (uploadStatus, nextAttemptAt, receivedAt)
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE payment_events ADD COLUMN deviceSequence INTEGER")
                database.execSQL(
                    """
                    UPDATE payment_events
                    SET deviceSequence = id
                    WHERE deviceSequence IS NULL
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_payment_events_deviceSequence
                    ON payment_events (deviceSequence)
                    """.trimIndent(),
                )
            }
        }

        fun create(context: Context): PaymentDatabase = Room.databaseBuilder(
            context.applicationContext,
            PaymentDatabase::class.java,
            "payment-events.db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }
}
