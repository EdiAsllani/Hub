package com.edi.hub.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edi.hub.data.model.PantryItem
import com.edi.hub.data.model.PantryLocation
import com.edi.hub.data.model.PantryUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/**
 * Guards the two things most likely to break silently: the LocalDate/Instant converters,
 * and the fact that a consumed item is soft-deleted rather than removed.
 */
@RunWith(AndroidJUnit4::class)
class HubDatabaseTest {

    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        HubDatabase::class.java,
    ).build()

    @After fun close() = db.close()

    @Test
    fun roundTripsDatesAndSoftDeletes() = runBlocking {
        val expiry = LocalDate.of(2026, 10, 1)
        val id = db.pantryDao().insert(
            PantryItem(
                name = "Yogurt",
                quantity = 1.0,
                unit = PantryUnit.PCS,
                location = PantryLocation.FRIDGE,
                addedAt = Instant.ofEpochMilli(1_700_000_000_000),
                expiresOn = expiry,
            ),
        )

        val stored = db.pantryDao().observe(id).first()
        assertEquals(expiry, stored?.expiresOn)
        assertEquals(PantryLocation.FRIDGE, stored?.location)

        db.pantryDao().markConsumed(id, Instant.now().toEpochMilli())
        assertEquals(0, db.pantryDao().observeActive().first().size)
        // Soft delete: the row is still there.
        assertEquals("Yogurt", db.pantryDao().observe(id).first()?.name)
    }
}
