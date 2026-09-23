package com.edi.hub.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The first real migration, and the only kind of test that can fail on a phone holding real data.
 * Version 2 adds three nullable columns, so the proof is that rows written under version 1 are
 * still there afterwards, unchanged, with the new columns reading null.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HubDatabase::class.java,
    )

    @Test
    fun keepsEveryRowFromVersionOne() {
        helper.createDatabase(TEST_DB, 1).use { v1 ->
            v1.execSQL(
                "INSERT INTO product (barcode, name, brand, defaultShelfLifeDays, source, updatedAt) " +
                    "VALUES ('5449000000996', 'Yogurt', 'Rugove', 14, 'USER', 1758000000000)",
            )
            v1.execSQL(
                "INSERT INTO pantry_item (barcode, name, location, addedAt, expiresOn) " +
                    "VALUES ('5449000000996', 'Yogurt', 'FRIDGE', 1758000000000, 20400)",
            )
        }

        // validateDroppedTables, because a migration that quietly leaves a table behind is a
        // migration that passes here and diverges from a fresh install forever.
        helper.runMigrationsAndValidate(TEST_DB, 2, true).use { v2 ->
            v2.query("SELECT name, defaultShelfLifeDays, defaultLocation, snoozedUntil FROM product")
                .use { cursor ->
                    assertTrue("the product row did not survive the migration", cursor.moveToFirst())
                    assertEquals("Yogurt", cursor.getString(0))
                    assertEquals(14, cursor.getInt(1))
                    assertTrue("defaultLocation should start unlearned", cursor.isNull(2))
                    assertTrue("snoozedUntil should start unset", cursor.isNull(3))
                    assertEquals(1, cursor.count)
                }

            v2.query("SELECT name, location, expiresOn, snoozedUntil FROM pantry_item").use { cursor ->
                assertTrue("the pantry row did not survive the migration", cursor.moveToFirst())
                assertEquals("Yogurt", cursor.getString(0))
                assertEquals("FRIDGE", cursor.getString(1))
                assertEquals(20400L, cursor.getLong(2))
                assertTrue("snoozedUntil should start unset", cursor.isNull(3))
                assertEquals(1, cursor.count)
            }
        }
    }

    @Test
    fun addsDeadlineWithoutChangingVersionTwoRows() {
        helper.createDatabase(DEADLINE_TEST_DB, 2).use { v2 ->
            v2.execSQL(
                "INSERT INTO product (barcode, name, source, updatedAt) " +
                    "VALUES ('111', 'Milk', 'USER', 1758000000000)",
            )
        }

        helper.runMigrationsAndValidate(DEADLINE_TEST_DB, 3, true).use { v3 ->
            v3.query("SELECT name FROM product WHERE barcode = '111'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Milk", cursor.getString(0))
            }
            v3.execSQL(
                "INSERT INTO deadline " +
                    "(kind, name, dueOn, repeatDays, counterparty, costMinor, note, completedAt, snoozedUntil) " +
                    "VALUES ('LENDING', 'Drill', 21000, 30, 'Ardit', 1299, 'Return it', NULL, 21001)",
            )
            v3.query("SELECT kind, dueOn, counterparty, costMinor, snoozedUntil FROM deadline").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("LENDING", cursor.getString(0))
                assertEquals(21000L, cursor.getLong(1))
                assertEquals("Ardit", cursor.getString(2))
                assertEquals(1299L, cursor.getLong(3))
                assertEquals(21001L, cursor.getLong(4))
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
        const val DEADLINE_TEST_DB = "deadline-migration-test.db"
    }
}
