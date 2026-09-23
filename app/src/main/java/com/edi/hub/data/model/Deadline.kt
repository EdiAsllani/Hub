package com.edi.hub.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/** One low-churn due-date thing. Every feature in Deadlines is a filter over this row. */
@Entity(
    tableName = "deadline",
    indices = [Index("dueOn"), Index("kind")],
)
data class Deadline(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: DeadlineKind,
    val name: String,
    val dueOn: LocalDate,
    val repeatDays: Int? = null,
    val counterparty: String? = null,
    val costMinor: Long? = null,
    val note: String? = null,
    val completedAt: Instant? = null,
    val snoozedUntil: LocalDate? = null,
)
