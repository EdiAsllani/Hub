package com.edi.hub.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.edi.hub.data.model.PantryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PantryDao {
    /** Flow, so filtering and list animations follow writes without manual refreshes. */
    @Query("SELECT * FROM pantry_item WHERE consumedAt IS NULL ORDER BY expiresOn IS NULL, expiresOn ASC")
    fun observeActive(): Flow<List<PantryItem>>

    @Query("SELECT * FROM pantry_item WHERE id = :id")
    fun observe(id: Long): Flow<PantryItem?>

    /** Backs the `expiringSoon` dashboard rule. */
    @Query(
        "SELECT * FROM pantry_item WHERE consumedAt IS NULL AND expiresOn IS NOT NULL " +
            "AND expiresOn <= :throughEpochDay ORDER BY expiresOn ASC",
    )
    fun observeExpiringThrough(throughEpochDay: Long): Flow<List<PantryItem>>

    @Insert
    suspend fun insert(item: PantryItem): Long

    @Update
    suspend fun update(item: PantryItem)

    /** Soft delete: the row stays so consumption history survives. */
    @Query("UPDATE pantry_item SET consumedAt = :consumedAt WHERE id = :id")
    suspend fun markConsumed(id: Long, consumedAt: Long)
}
