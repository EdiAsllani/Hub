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
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/**
 * The SQL is the thing under test, so nothing here is faked. These assertions are what stop the
 * count model from quietly breaking — `docs/plan.md` §7.
 */
@RunWith(AndroidJUnit4::class)
class PantryGroupingTest {

    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        HubDatabase::class.java,
    ).build()

    private val dao = db.pantryDao()

    @After fun close() = db.close()

    private suspend fun add(
        name: String,
        barcode: String? = null,
        location: PantryLocation = PantryLocation.FRIDGE,
        expiresOn: LocalDate? = null,
    ) = dao.insert(
        PantryItem(
            barcode = barcode,
            name = name,
            location = location,
            addedAt = Instant.ofEpochMilli(1_700_000_000_000),
            expiresOn = expiresOn,
        ),
    )

    @Test fun twoRowsSharingABarcodeBecomeOneCard() = runBlocking {
        add("Eggs", barcode = "111", expiresOn = LocalDate.of(2026, 10, 12))
        add("Eggs", barcode = "111", expiresOn = LocalDate.of(2026, 10, 5))

        val cards = dao.observeCards(PantryLocation.FRIDGE).first()
        assertEquals(1, cards.size)
        assertEquals(2, cards.single().entryCount)
        // The card carries the soonest date, which is the one the next swipe will resolve.
        assertEquals(LocalDate.of(2026, 10, 5), cards.single().expiresOn)
    }

    @Test fun rowsWithNoBarcodeStayApart() = runBlocking {
        add("Tomatoes", expiresOn = LocalDate.of(2026, 10, 3))
        add("Baby spinach", expiresOn = LocalDate.of(2026, 10, 1))

        val cards = dao.observeCards(PantryLocation.FRIDGE).first()
        assertEquals(2, cards.size)
        assertEquals(listOf(1, 1), cards.map { it.entryCount })
        // Two hand-typed things are not reliably the same thing, so they never merge.
        assertEquals(listOf("Baby spinach", "Tomatoes"), cards.map { it.name })
    }

    @Test fun theSameBarcodeInTwoLocationsIsTwoCards() = runBlocking {
        add("Peas", barcode = "222", location = PantryLocation.FREEZER, expiresOn = LocalDate.of(2027, 1, 1))
        add("Peas", barcode = "222", location = PantryLocation.PANTRY, expiresOn = LocalDate.of(2027, 2, 1))

        assertEquals(1, dao.observeCards(PantryLocation.FREEZER).first().size)
        assertEquals(1, dao.observeCards(PantryLocation.PANTRY).first().size)
        assertEquals(0, dao.observeCards(PantryLocation.FRIDGE).first().size)
    }

    @Test fun resolvedRowsLeaveTheCardAndTheDateMovesOn() = runBlocking {
        add("Eggs", barcode = "111", expiresOn = LocalDate.of(2026, 10, 12))
        add("Eggs", barcode = "111", expiresOn = LocalDate.of(2026, 10, 5))

        val resolved = dao.resolveNext(PantryLocation.FRIDGE, "111", Disposition.CONSUMED)
        assertEquals(LocalDate.of(2026, 10, 5), dao.observe(resolved!!).first()?.expiresOn)

        val card = dao.observeCards(PantryLocation.FRIDGE).first().single()
        assertEquals(1, card.entryCount)
        assertEquals(LocalDate.of(2026, 10, 12), card.expiresOn)
    }

    @Test fun aDatelessBoxIsResolvedLastAndNeverAheadOfADatedOne() = runBlocking {
        add("Rice", barcode = "333", expiresOn = null)
        add("Rice", barcode = "333", expiresOn = LocalDate.of(2026, 12, 1))

        // MIN() skips NULLs, so the card shows the dated box; a plain ASC would resolve the other one
        // and contradict it.
        val card = dao.observeCards(PantryLocation.FRIDGE).first().single()
        assertEquals(LocalDate.of(2026, 12, 1), card.expiresOn)
        assertEquals(2, card.entryCount)

        val first = dao.resolveNext(PantryLocation.FRIDGE, "333", Disposition.CONSUMED)
        assertEquals(LocalDate.of(2026, 12, 1), dao.observe(first!!).first()?.expiresOn)

        val second = dao.resolveNext(PantryLocation.FRIDGE, "333", Disposition.CONSUMED)
        assertNull(dao.observe(second!!).first()?.expiresOn)
        assertEquals(0, dao.observeCards(PantryLocation.FRIDGE).first().size)
    }

    @Test fun tabCountsAreCardsNotBoxes() = runBlocking {
        add("Eggs", barcode = "111", expiresOn = LocalDate.of(2026, 10, 5))
        add("Eggs", barcode = "111", expiresOn = LocalDate.of(2026, 10, 12))
        add("Tomatoes")
        add("Peas", barcode = "222", location = PantryLocation.FREEZER)

        val counts = dao.observeLocationCounts().first().associate { it.location to it.cards }
        assertEquals(2, counts[PantryLocation.FRIDGE])
        assertEquals(1, counts[PantryLocation.FREEZER])
        assertNull(counts[PantryLocation.PANTRY])
    }

    @Test fun undoPutsTheEntryBack() = runBlocking {
        add("Eggs", barcode = "111", expiresOn = LocalDate.of(2026, 10, 5))
        val id = dao.resolveNext(PantryLocation.FRIDGE, "111", Disposition.DISCARDED)!!
        assertEquals(0, dao.observeCards(PantryLocation.FRIDGE).first().size)

        dao.unresolve(id)
        val back = dao.observe(id).first()
        assertNull(back?.consumedAt)
        assertNull(back?.disposition)
        assertEquals(1, dao.observeCards(PantryLocation.FRIDGE).first().size)
    }
}
