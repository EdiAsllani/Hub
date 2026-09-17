package com.edi.hub.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.edi.hub.data.model.Disposition
import com.edi.hub.data.model.PantryItem
import com.edi.hub.data.model.PantryLocation
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * The group key is `COALESCE(barcode, 'id:' || id)`, never the bare barcode column: SQLite treats
 * NULLs as equal in `GROUP BY`, so grouping on `barcode` would collapse every hand-entered item in
 * the pantry into one card reading `×3`.
 */
private const val GROUP_KEY = "COALESCE(barcode, 'id:' || id)"

@Dao
interface PantryDao {
    /** Flow, so filtering and list animations follow writes without manual refreshes. */
    @Query("SELECT * FROM pantry_item WHERE consumedAt IS NULL ORDER BY expiresOn IS NULL, expiresOn ASC")
    fun observeActive(): Flow<List<PantryItem>>

    @Query("SELECT * FROM pantry_item WHERE id = :id")
    fun observe(id: Long): Flow<PantryItem?>

    /**
     * The pantry list. Filters to one location and to open rows *first*, then groups — so the same
     * product in the fridge and in the freezer is two cards, one on each tab.
     *
     * The bare columns beside `MIN(expiresOn)` are not arbitrary: SQLite takes them from the row
     * that matched the minimum, so the card describes the entry that the next swipe will resolve.
     */
    @Query(
        "SELECT $GROUP_KEY AS groupKey, barcode, name, brand, description, location, " +
            "MIN(expiresOn) AS expiresOn, COUNT(*) AS entryCount " +
            "FROM pantry_item WHERE consumedAt IS NULL AND location = :location " +
            "GROUP BY groupKey ORDER BY expiresOn IS NULL, expiresOn ASC",
    )
    fun observeCards(location: PantryLocation): Flow<List<PantryCard>>

    /** Counts are cards rather than boxes, so they match what the tab is about to show. */
    @Query(
        "SELECT location, COUNT(DISTINCT $GROUP_KEY) AS cards FROM pantry_item " +
            "WHERE consumedAt IS NULL GROUP BY location",
    )
    fun observeLocationCounts(): Flow<List<LocationCount>>

    /** Every open entry behind one card, earliest first. The detail screen explains the count by showing it. */
    @Query(
        "SELECT * FROM pantry_item WHERE consumedAt IS NULL AND location = :location " +
            "AND $GROUP_KEY = :groupKey ORDER BY expiresOn IS NULL, expiresOn ASC",
    )
    fun observeGroup(location: PantryLocation, groupKey: String): Flow<List<PantryItem>>

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

    /**
     * Soft delete: the row stays so consumption history survives. `consumedAt` means *resolved at*,
     * whichever way the item went, and [disposition] says which.
     */
    @Query("UPDATE pantry_item SET consumedAt = :resolvedAt, disposition = :disposition WHERE id = :id")
    suspend fun resolve(id: Long, resolvedAt: Long, disposition: Disposition)

    /** Undo. Both columns go back to null on the row that was returned. */
    @Query("UPDATE pantry_item SET consumedAt = NULL, disposition = NULL WHERE id = :id")
    suspend fun unresolve(id: Long)

    /**
     * `expiresOn IS NULL, expiresOn ASC` rather than a plain `ASC`, which sorts NULL first in
     * SQLite: a dateless box would then be consumed ahead of a dated one, and would contradict the
     * card, since `MIN()` skips NULLs.
     */
    @Query(
        "SELECT id FROM pantry_item WHERE consumedAt IS NULL AND location = :location " +
            "AND $GROUP_KEY = :groupKey ORDER BY expiresOn IS NULL, expiresOn ASC LIMIT 1",
    )
    suspend fun nextToResolve(location: PantryLocation, groupKey: String): Long?

    /**
     * Resolves one entry of a card and says which one, because undo needs that id and there is no
     * other way to name it afterwards. `UPDATE ... ORDER BY ... LIMIT` is not compiled into
     * Android's SQLite, so the select and the update are two statements in one transaction.
     */
    @Transaction
    suspend fun resolveNext(
        location: PantryLocation,
        groupKey: String,
        disposition: Disposition,
        resolvedAt: Instant = Instant.now(),
    ): Long? {
        val id = nextToResolve(location, groupKey) ?: return null
        resolve(id, resolvedAt.toEpochMilli(), disposition)
        return id
    }
}
