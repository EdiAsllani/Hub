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
 *
 * There is no quantity and no unit. [description] is free text off the pack ("500 ml", "24 cope")
 * that Hub never parses, converts or sums. Two boxes are two rows; the list groups them into one
 * `×2` card at read time. See `design/spec.md` §7.1 and §7.3.
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
    /** Null for unbarcoded things, such as loose vegetables. Null rows never group. */
    val barcode: String? = null,
    val name: String,
    /** Free text on the item, pre-filled from [Product] on a lookup hit. */
    val brand: String? = null,
    /** Free text off the pack. A label for a human, not data. */
    val description: String? = null,
    val location: PantryLocation,
    val addedAt: Instant,
    val expiresOn: LocalDate? = null,
    val openedAt: Instant? = null,
    /** Soft delete, and now *resolved at* — [disposition] says which way it went. */
    val consumedAt: Instant? = null,
    val disposition: Disposition? = null,
    val tripId: Long? = null,
)
