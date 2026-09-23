package com.edi.hub.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.edi.hub.data.model.Deadline
import com.edi.hub.data.model.DeadlineKind
import com.edi.hub.domain.nextOccurrenceAfter
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface DeadlineDao {
    @Query("SELECT * FROM deadline WHERE completedAt IS NULL ORDER BY dueOn, name COLLATE NOCASE")
    fun observeActive(): Flow<List<Deadline>>

    @Query("SELECT * FROM deadline WHERE id = :id")
    fun observe(id: Long): Flow<Deadline?>

    @Query("SELECT * FROM deadline WHERE id = :id")
    suspend fun find(id: Long): Deadline?

    @Query(
        "SELECT * FROM deadline WHERE completedAt IS NULL AND dueOn <= :throughEpochDay " +
            "ORDER BY dueOn, name COLLATE NOCASE",
    )
    suspend fun dueThrough(throughEpochDay: Long): List<Deadline>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(deadline: Deadline): Long

    @Update
    suspend fun update(deadline: Deadline)

    @Query("UPDATE deadline SET snoozedUntil = :untilEpochDay WHERE id = :id")
    suspend fun snooze(id: Long, untilEpochDay: Long?)

    @Transaction
    suspend fun complete(id: Long, today: LocalDate, now: Instant): Deadline? {
        val current = find(id) ?: return null
        val updated = if (current.repeatDays == null) {
            current.copy(completedAt = now, snoozedUntil = null)
        } else {
            current.copy(
                dueOn = nextOccurrenceAfter(current.dueOn, current.repeatDays, today),
                completedAt = null,
                snoozedUntil = null,
            )
        }
        update(updated)
        return updated
    }

    @Query(
        "SELECT COUNT(*) FROM deadline WHERE completedAt IS NULL AND kind = :kind " +
            "AND dueOn < :todayEpochDay AND counterparty IS NOT NULL AND TRIM(counterparty) != ''",
    )
    suspend fun countOverdueLending(kind: DeadlineKind = DeadlineKind.LENDING, todayEpochDay: Long): Int
}
