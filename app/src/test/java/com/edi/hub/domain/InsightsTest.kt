package com.edi.hub.domain

import com.edi.hub.data.dao.RunOutCandidate
import com.edi.hub.data.model.Deadline
import com.edi.hub.data.model.DeadlineKind
import com.edi.hub.data.model.PantryItem
import com.edi.hub.data.model.PantryLocation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * The rules are plain functions over a narrow facade, so they are plain unit tests. The join behind
 * `ranOutSince` is SQL rather than rule logic, and it is tested against a real database in
 * `PantryGroupingTest` instead of being faked here.
 */
class InsightsTest {

    private val today = LocalDate.of(2026, 9, 17)
    private val now: Instant = Instant.ofEpochMilli(1_800_000_000_000)

    private fun item(name: String, expiresOn: LocalDate?, snoozedUntil: LocalDate? = null) = PantryItem(
        id = name.hashCode().toLong(),
        name = name,
        location = PantryLocation.FRIDGE,
        addedAt = now,
        expiresOn = expiresOn,
        snoozedUntil = snoozedUntil,
    )

    private class Fake(
        val expiring: List<PantryItem> = emptyList(),
        val runOuts: List<RunOutCandidate> = emptyList(),
        val deadlines: List<Deadline> = emptyList(),
    ) : Queries {
        var askedThrough: LocalDate? = null
        var askedSince: Instant? = null

        override suspend fun expiringThrough(through: LocalDate): List<PantryItem> {
            askedThrough = through
            return expiring.filter { it.expiresOn?.isAfter(through) == false }
        }

        override suspend fun ranOutSince(since: Instant): List<RunOutCandidate> {
            askedSince = since
            return runOuts.filter { !it.lastResolvedAt.isBefore(since) }
        }

        override suspend fun deadlinesThrough(through: LocalDate): List<Deadline> =
            deadlines.filter { it.dueOn <= through && it.completedAt == null }
    }

    @Test fun expiringSoonAsksForTheWeekAhead() = runTest {
        val fake = Fake(expiring = listOf(item("Milk", today.plusDays(1)), item("Ajvar", today.plusMonths(8))))
        val insights = expiringSoon(fake, today)

        assertEquals(today.plusDays(EXPIRY_HORIZON_DAYS), fake.askedThrough)
        assertEquals(listOf("Milk"), insights.map { (it as Insight.ExpiringSoon).item.name })
    }

    @Test fun expiringSoonIncludesWhatIsAlreadyGoneOff() = runTest {
        val fake = Fake(expiring = listOf(item("Spinach", today.minusDays(4))))
        assertEquals(1, expiringSoon(fake, today).size)
    }

    @Test fun somethingWithNoDateIsNeverAnInsight() = runTest {
        val fake = Fake(expiring = listOf(item("Rice", null)))
        assertTrue(expiringSoon(fake, today).isEmpty())
    }

    @Test fun ranOutHoldsForTheWindowAndThenStops() = runTest {
        val justFinished = RunOutCandidate("111", "Eggs", "Rugove", now.minusSeconds(2 * DAY))
        val longGone = RunOutCandidate("222", "Salt", null, now.minusSeconds(30 * DAY))
        val fake = Fake(runOuts = listOf(justFinished, longGone))

        val insights = ranOut(fake, now)

        assertEquals(now.minusSeconds(RUN_OUT_HOLD_DAYS * DAY), fake.askedSince)
        assertEquals(listOf("Eggs"), insights.map { (it as Insight.RanOut).candidate.name })
    }

    @Test fun bothRulesRunAndTheirCardsAreDistinguishable() = runTest {
        val fake = Fake(
            expiring = listOf(item("Milk", today.plusDays(1))),
            runOuts = listOf(RunOutCandidate("111", "Eggs", null, now)),
        )
        val insights = insightRules.flatMap { rule -> rule(fake) }

        assertEquals(2, insights.size)
        assertEquals(1, insights.filterIsInstance<Insight.ExpiringSoon>().size)
        assertEquals(1, insights.filterIsInstance<Insight.RanOut>().size)
        // Ids have to be stable and unique, since the queue is keyed on them.
        assertEquals(2, insights.map { it.id }.toSet().size)
    }

    @Test fun aSnoozedCardStaysDownUntilTheDayItIsDueBack() = runTest {
        val fake = Fake(expiring = listOf(item("Milk", today.plusDays(1), snoozedUntil = today.plusDays(1))))
        val insight = expiringSoon(fake, today).single()

        assertTrue("snoozed until tomorrow means not today", insight.isSnoozed(today))
        // The date arriving is the whole mechanism: nothing clears a snooze, it simply runs out.
        assertFalse("tomorrow it is due back", insight.isSnoozed(today.plusDays(1)))
        assertFalse(insight.isSnoozed(today.plusDays(2)))
    }

    @Test fun somethingNeverSnoozedIsNeverSnoozed() = runTest {
        val fake = Fake(expiring = listOf(item("Milk", today.plusDays(1))))
        assertFalse(expiringSoon(fake, today).single().isSnoozed(today))
    }

    @Test fun aRunOutCardSnoozesTheSameWay() = runTest {
        val fake = Fake(
            runOuts = listOf(
                RunOutCandidate("111", "Eggs", null, now, snoozedUntil = today.plusDays(1)),
                RunOutCandidate("222", "Flour", null, now),
            ),
        )
        val (snoozed, due) = ranOut(fake, now).partition { it.isSnoozed(today) }

        assertEquals(listOf("Eggs"), snoozed.map { (it as Insight.RanOut).candidate.name })
        assertEquals(listOf("Flour"), due.map { (it as Insight.RanOut).candidate.name })
    }

    @Test fun deadlineRulesUseKindHorizonsAndOverdueReturnSemantics() = runTest {
        val fake = Fake(deadlines = listOf(
            Deadline(id = 1, kind = DeadlineKind.WARRANTY, name = "Laptop", dueOn = today.plusDays(30)),
            Deadline(id = 2, kind = DeadlineKind.DOCUMENT, name = "Passport", dueOn = today.plusDays(90)),
            Deadline(id = 3, kind = DeadlineKind.BILL, name = "Power", dueOn = today.plusDays(7)),
            Deadline(id = 4, kind = DeadlineKind.LENDING, name = "Drill", dueOn = today.minusDays(1), counterparty = "Ardit"),
            Deadline(id = 5, kind = DeadlineKind.LENDING, name = "Book", dueOn = today.minusDays(1)),
        ))

        assertEquals(
            listOf("Laptop", "Passport", "Power", "Drill"),
            deadlineInsights(fake, today).map { (it as Insight.DeadlineDue).deadline.name },
        )
    }

    @Test fun rankingCapsTheQueueAtEightAndPutsOverdueFirst() {
        val cards = (1L..10L).map { id ->
            Insight.DeadlineDue(
                Deadline(id = id, kind = DeadlineKind.BILL, name = "Bill $id", dueOn = today.plusDays(id)),
            )
        } + Insight.DeadlineDue(
            Deadline(id = 99, kind = DeadlineKind.BILL, name = "Late", dueOn = today.minusDays(1)),
        )
        val ranked = rankAndCap(cards, today)
        assertEquals(8, ranked.size)
        assertEquals("Late", (ranked.first() as Insight.DeadlineDue).deadline.name)
    }

    private companion object {
        const val DAY = 24L * 60 * 60
    }
}
