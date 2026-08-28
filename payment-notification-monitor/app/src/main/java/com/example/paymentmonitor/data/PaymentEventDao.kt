package com.example.paymentmonitor.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PaymentEventDao {
    @Query("SELECT * FROM payment_events ORDER BY receivedAt DESC, id DESC")
    abstract fun observeAll(): Flow<List<PaymentEventEntity>>

    @Query("SELECT * FROM payment_events ORDER BY receivedAt DESC, id DESC")
    abstract suspend fun getAll(): List<PaymentEventEntity>

    @Query("SELECT uploadStatus, COUNT(*) AS count FROM payment_events GROUP BY uploadStatus")
    abstract fun observeStatusCounts(): Flow<List<UploadStatusCount>>

    @Query(
        """
        SELECT * FROM payment_events
        WHERE uploadStatus IN ('PENDING', 'RETRYING')
          AND (nextAttemptAt IS NULL OR nextAttemptAt <= :now)
        ORDER BY CASE WHEN direction = 'INCOME' THEN 0 ELSE 1 END,
                 postTime ASC, receivedAt ASC, id ASC
        LIMIT :limit
        """,
    )
    abstract suspend fun getReadyForUpload(now: Long, limit: Int): List<PaymentEventEntity>

    @Query(
        """
        SELECT * FROM payment_events
        WHERE direction = 'INCOME'
          AND uploadStatus IN ('PENDING', 'RETRYING')
          AND (nextAttemptAt IS NULL OR nextAttemptAt <= :now)
        ORDER BY postTime ASC, receivedAt ASC, id ASC
        LIMIT :limit
        """,
    )
    abstract suspend fun getReadyIncomeForUpload(now: Long, limit: Int): List<PaymentEventEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(entity: PaymentEventEntity): Long

    @Query(
        """
        UPDATE payment_events
        SET deviceSequence = :sequence
        WHERE id = :id AND deviceSequence IS NULL
        """,
    )
    protected abstract suspend fun assignDeviceSequence(id: Long, sequence: Long)

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM payment_events
            WHERE sourcePackage = :sourcePackage
              AND notificationKey = :notificationKey
              AND postTime = :postTime
              AND direction = :direction
              AND (
                    amount = :amount
                    OR (amount IS NULL AND :amount IS NULL)
              )
        )
        """,
    )
    abstract suspend fun existsNotificationIdentity(
        sourcePackage: String,
        notificationKey: String,
        postTime: Long,
        direction: String,
        amount: String?,
    ): Boolean

    @Query(
        """
        DELETE FROM payment_events
        WHERE id IN (
            SELECT id FROM payment_events
            WHERE uploadStatus IN ('UPLOADED', 'REJECTED')
            ORDER BY receivedAt DESC, id DESC
            LIMIT -1 OFFSET :maximumCount
        )
        """,
    )
    protected abstract suspend fun trimTerminalHistory(maximumCount: Int)

    @Query(
        """
        UPDATE payment_events
        SET uploadStatus = 'UPLOADING', lastAttemptAt = :now,
            lastErrorCode = NULL, lastErrorMessage = NULL
        WHERE clientEventId IN (:clientEventIds)
        """,
    )
    abstract suspend fun markUploading(clientEventIds: List<String>, now: Long)

    @Query(
        """
        UPDATE payment_events
        SET uploadStatus = 'UPLOADED', uploadedAt = :now, nextAttemptAt = NULL,
            lastErrorCode = NULL, lastErrorMessage = NULL
        WHERE clientEventId IN (:clientEventIds)
        """,
    )
    abstract suspend fun markUploaded(clientEventIds: List<String>, now: Long)

    @Query(
        """
        UPDATE payment_events
        SET uploadStatus = 'REJECTED', nextAttemptAt = NULL,
            lastErrorCode = :code, lastErrorMessage = :message
        WHERE clientEventId = :clientEventId
        """,
    )
    abstract suspend fun markRejected(clientEventId: String, code: String, message: String?)

    @Query(
        """
        UPDATE payment_events
        SET uploadStatus = 'RETRYING', attemptCount = attemptCount + 1,
            nextAttemptAt = :nextAttemptAt, lastErrorCode = :code,
            lastErrorMessage = :message
        WHERE clientEventId IN (:clientEventIds)
        """,
    )
    abstract suspend fun markRetrying(
        clientEventIds: List<String>,
        nextAttemptAt: Long,
        code: String,
        message: String?,
    )

    @Query(
        """
        UPDATE payment_events
        SET uploadStatus = 'RETRYING', nextAttemptAt = :now,
            lastErrorCode = NULL, lastErrorMessage = NULL
        WHERE clientEventId = :clientEventId
        """,
    )
    abstract suspend fun retry(clientEventId: String, now: Long)

    @Query(
        """
        UPDATE payment_events
        SET uploadStatus = 'RETRYING', nextAttemptAt = :now,
            lastErrorCode = 'STALE_UPLOAD', lastErrorMessage = '上传任务异常中断'
        WHERE uploadStatus = 'UPLOADING' AND lastAttemptAt < :cutoff
        """,
    )
    abstract suspend fun recoverStaleUploading(cutoff: Long, now: Long)

    @Query("DELETE FROM payment_events WHERE uploadStatus IN ('UPLOADED', 'REJECTED')")
    abstract suspend fun clearTerminalHistory()

    @Query(
        "SELECT COUNT(*) FROM payment_events WHERE uploadStatus IN ('PENDING', 'UPLOADING', 'RETRYING')",
    )
    abstract suspend fun countPending(): Int

    @Query(
        "SELECT COUNT(*) FROM payment_events WHERE uploadStatus IN ('PENDING', 'UPLOADING')",
    )
    abstract suspend fun countPendingHeartbeat(): Int

    @Query("SELECT COUNT(*) FROM payment_events WHERE uploadStatus = 'RETRYING'")
    abstract suspend fun countRetryingHeartbeat(): Int

    @Query("SELECT COUNT(*) FROM payment_events WHERE uploadStatus = 'REJECTED'")
    abstract suspend fun countRejectedHeartbeat(): Int

    @Query(
        """
        SELECT COUNT(*) FROM payment_events
        WHERE direction = 'INCOME'
          AND uploadStatus IN ('PENDING', 'UPLOADING', 'RETRYING')
        """,
    )
    abstract suspend fun countPendingIncome(): Int

    @Query(
        """
        UPDATE payment_events
        SET nextAttemptAt = :now
        WHERE direction = 'INCOME'
          AND uploadStatus = 'RETRYING'
          AND lastErrorCode = 'NETWORK_ERROR'
        """,
    )
    abstract suspend fun expediteIncomeNetworkRetries(now: Long)

    @Query(
        """
        UPDATE payment_events
        SET notificationKeyHash = :notificationKeyHash, rawHash = :rawHash
        WHERE clientEventId = :clientEventId
        """,
    )
    abstract suspend fun updateHashes(
        clientEventId: String,
        notificationKeyHash: String?,
        rawHash: String,
    )

    @Transaction
    open suspend fun insertAndTrim(entity: PaymentEventEntity, maximumCount: Int): Boolean {
        val insertedId = insert(entity)
        if (insertedId == -1L) return false
        assignDeviceSequence(insertedId, insertedId)
        trimTerminalHistory(maximumCount)
        return true
    }

    @Transaction
    open suspend fun finishUploadAndTrim(
        uploadedIds: List<String>,
        now: Long,
        maximumCount: Int,
    ) {
        if (uploadedIds.isNotEmpty()) markUploaded(uploadedIds, now)
        trimTerminalHistory(maximumCount)
    }
}
