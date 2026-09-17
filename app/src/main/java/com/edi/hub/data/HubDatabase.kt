package com.edi.hub.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.edi.hub.data.dao.PantryDao
import com.edi.hub.data.dao.ProductDao
import com.edi.hub.data.dao.TripDao
import com.edi.hub.data.model.PantryItem
import com.edi.hub.data.model.Product
import com.edi.hub.data.model.Trip

/**
 * Phase 1 holds the pantry tables only. Deadline, MoneyEvent, BacklogItem, Secret,
 * DeadlinePhoto and PriceObservation arrive in later phases as migrations.
 *
 * `fallbackToDestructiveMigration` is never used here — it silently wipes real data on a bump.
 */
@Database(
    entities = [Product::class, PantryItem::class, Trip::class],
    version = HubDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class HubDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao

    abstract fun pantryDao(): PantryDao

    abstract fun tripDao(): TripDao

    companion object {
        const val NAME = "hub.db"

        /** Also what SQLite reports as `PRAGMA user_version`, which is how a restore spots a backup from a newer build. */
        const val VERSION = 1
    }
}
