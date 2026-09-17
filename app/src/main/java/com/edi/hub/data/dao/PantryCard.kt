package com.edi.hub.data.dao

import com.edi.hub.data.model.PantryLocation
import java.time.LocalDate

/**
 * One row of the pantry list, which is not one row of the table: two boxes of the same thing are
 * two rows with two honest dates, collapsed into one card at read time. There is no count column
 * and adding one later would be the wrong fix — see `design/spec.md` §7.3.
 *
 * [expiresOn] is the soonest date in the group, which is also the date the next swipe will resolve.
 * The whole count model rests on those two being the same date.
 */
data class PantryCard(
    val groupKey: String,
    val barcode: String?,
    val name: String,
    val brand: String?,
    val description: String?,
    val location: PantryLocation,
    val expiresOn: LocalDate?,
    val entryCount: Int,
)

/**
 * A product whose last open box has been resolved. Barcoded only: a null-barcode row has no
 * `Product` to hold the dismissal, and two hand-typed "tomatoes" are not reliably the same thing.
 */
data class RunOutCandidate(
    val barcode: String,
    val name: String,
    val brand: String?,
    val lastResolvedAt: java.time.Instant,
)

/** Cards, not boxes: eggs `×2` counts once, which is what the tab is telling you. */
data class LocationCount(
    val location: PantryLocation,
    val cards: Int,
)
