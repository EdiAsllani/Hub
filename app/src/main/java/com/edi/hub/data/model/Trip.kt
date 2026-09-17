package com.edi.hub.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/** One shopping trip: N pantry items and (later) one money event, so prices are entered once. */
@Entity(tableName = "trip")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val store: String? = null,
    val occurredAt: Instant,
)
