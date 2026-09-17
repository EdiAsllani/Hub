package com.edi.hub.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edi.hub.data.model.Disposition
import com.edi.hub.data.model.PantryItem
import com.edi.hub.data.model.PantryLocation
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
 * and the fact that a resolved item is soft-deleted rather than removed.
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
                brand = "Rugove",
                description = "400 g",
                location = PantryLocation.FRIDGE,
                addedAt = Instant.ofEpochMilli(1_700_000_000_000),
                expiresOn = expiry,
            ),
        )

        val stored = db.pantryDao().observe(id).first()
        assertEquals(expiry, stored?.expiresOn)
        assertEquals(PantryLocation.FRIDGE, stored?.location)
        assertEquals("400 g", stored?.description)

        db.pantryDao().resolve(id, Instant.now().toEpochMilli(), Disposition.DISCARDED)
        assertEquals(0, db.pantryDao().observeActive().first().size)

        // Soft delete: the row is still there, and it remembers which way it went.
        val resolved = db.pantryDao().observe(id).first()
        assertEquals("Yogurt", resolved?.name)
        assertEquals(Disposition.DISCARDED, resolved?.disposition)
    }
}
