package com.edi.hub.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * High-churn, high-volume. Deliberately its own table rather than a kind of Deadline.
 *
 * [expiresOn] is a `LocalDate` stored as an epoch day, never an `Instant` — an expiry is a
 * calendar date, and "expires in 2 days" must not flip across midnight, a timezone change or DST.
 */
@Entity(
    tableName = "pantry_item",
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("expiresOn"),
        Index("barcode"),
        Index("consumedAt"),
        Index("tripId"),
    ],
)
data class PantryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Null for unbarcoded things, such as loose vegetables. */
    val barcode: String? = null,
    val name: String,
    val quantity: Double,
    val unit: PantryUnit,
    val location: PantryLocation,
    val addedAt: Instant,
    val expiresOn: LocalDate? = null,
    val openedAt: Instant? = null,
    /** Soft delete. Consumption history is what makes restock and spending insights possible. */
    val consumedAt: Instant? = null,
    val tripId: Long? = null,
)
