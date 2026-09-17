package com.edi.hub.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.edi.hub.data.model.Product

@Dao
interface ProductDao {
    @Query("SELECT * FROM product WHERE barcode = :barcode")
    suspend fun find(barcode: String): Product?

    @Upsert
    suspend fun upsert(product: Product)

    /**
     * Learning. Only a correction gets here — accepting a pre-filled value teaches nothing, so the
     * caller compares before it writes. A null argument leaves that column as it was, which is what
     * lets one statement carry both the date and the description.
     */
    @Query(
        "UPDATE product SET " +
            "defaultShelfLifeDays = COALESCE(:days, defaultShelfLifeDays), " +
            "defaultDescription = COALESCE(:description, defaultDescription), " +
            "updatedAt = :updatedAt WHERE barcode = :barcode",
    )
    suspend fun learn(barcode: String, days: Int?, description: String?, updatedAt: Long)

    /** "Got it" on a run-out card. Older than the product's latest resolution means it fires again. */
    @Query("UPDATE product SET runOutDismissedAt = :dismissedAt WHERE barcode = :barcode")
    suspend fun dismissRunOut(barcode: String, dismissedAt: Long)
}
