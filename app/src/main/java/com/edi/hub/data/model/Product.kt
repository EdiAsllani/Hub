package com.edi.hub.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Learned product cache, keyed by barcode. Grows into a private household catalog:
 * a lookup miss is filled in by hand once and then beats any public database for
 * the products this household actually buys.
 */
@Entity(tableName = "product")
data class Product(
    @PrimaryKey val barcode: String,
    val name: String,
    val brand: String? = null,
    val category: String? = null,
    /** Set or corrected by the user; pre-fills the expiry date on every later scan. */
    val defaultShelfLifeDays: Int? = null,
    /** Learned exactly as [defaultShelfLifeDays] is: only a correction writes it. */
    val defaultDescription: String? = null,
    /** Remote URL only, never a BLOB. */
    val imageUrl: String? = null,
    /** "Got it" on a run-out card. Older than the latest resolution means the card fires again. */
    val runOutDismissedAt: Instant? = null,
    val source: ProductSource,
    val updatedAt: Instant,
)
