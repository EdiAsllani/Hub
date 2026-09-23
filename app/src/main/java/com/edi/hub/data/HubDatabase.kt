package com.edi.hub.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.edi.hub.data.dao.DeadlineDao
import com.edi.hub.data.dao.PantryDao
import com.edi.hub.data.dao.ProductDao
import com.edi.hub.data.dao.TripDao
import com.edi.hub.data.model.Deadline
import com.edi.hub.data.model.PantryItem
import com.edi.hub.data.model.Product
import com.edi.hub.data.model.Trip

/**
 * Version 3 adds Deadline only. MoneyEvent remains a later migration.
 *
 * `fallbackToDestructiveMigration` is never used here — it silently wipes real data on a bump.
 *
 * Version 2 adds three nullable columns and nothing else, so Room derives the `ALTER TABLE`s
 * from the exported schemas rather than trusting SQL written by hand. A column added this way
 * cannot lose a row; anything that could would need a migration written out in full.
 */
@Database(
    entities = [Product::class, PantryItem::class, Trip::class, Deadline::class],
    version = HubDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
@TypeConverters(Converters::class)
abstract class HubDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao

    abstract fun pantryDao(): PantryDao

    abstract fun tripDao(): TripDao

    abstract fun deadlineDao(): DeadlineDao

    companion object {
        const val NAME = "hub.db"

        /** Also what SQLite reports as `PRAGMA user_version`, which is how a restore spots a backup from a newer build. */
        const val VERSION = 3
    }
}
