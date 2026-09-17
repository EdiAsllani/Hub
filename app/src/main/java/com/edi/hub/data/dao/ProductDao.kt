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

    /** Called when the user corrects an expiry date, so the next scan pre-fills correctly. */
    @Query("UPDATE product SET defaultShelfLifeDays = :days, updatedAt = :updatedAt WHERE barcode = :barcode")
    suspend fun updateShelfLife(barcode: String, days: Int, updatedAt: Long)
}
