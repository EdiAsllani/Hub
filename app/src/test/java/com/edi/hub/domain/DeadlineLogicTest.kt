package com.edi.hub.domain

import com.edi.hub.data.model.Deadline
import com.edi.hub.data.model.DeadlineKind
import com.edi.hub.ui.theme.Urgency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DeadlineLogicTest {
    private val today = LocalDate.of(2026, 9, 23)

    @Test fun recurrenceKeepsFutureDate() {
        assertEquals(today.plusDays(3), nextOccurrenceAfter(today.plusDays(3), 7, today))
    }

    @Test fun recurrenceAdvancesPastMissedIntervals() {
        assertEquals(today.plusDays(5), nextOccurrenceAfter(today.minusDays(9), 7, today))
    }

    @Test fun recurrenceDueTodayMovesOneInterval() {
        assertEquals(today.plusDays(30), nextOccurrenceAfter(today, 30, today))
    }

    @Test(expected = IllegalArgumentException::class)
    fun recurrenceRejectsZeroDays() { nextOccurrenceAfter(today, 0, today) }

    @Test fun urgencyUsesKindSpecificBoundaries() {
        assertEquals(Urgency.CRITICAL, deadlineUrgency(DeadlineKind.WARRANTY, today.plusDays(7), today))
        assertEquals(Urgency.SOON, deadlineUrgency(DeadlineKind.WARRANTY, today.plusDays(30), today))
        assertEquals(Urgency.CRITICAL, deadlineUrgency(DeadlineKind.DOCUMENT, today.plusDays(30), today))
        assertEquals(Urgency.SOON, deadlineUrgency(DeadlineKind.DOCUMENT, today.plusDays(90), today))
        assertEquals(Urgency.CRITICAL, deadlineUrgency(DeadlineKind.BILL, today.plusDays(2), today))
        assertEquals(Urgency.SOON, deadlineUrgency(DeadlineKind.BILL, today.plusDays(7), today))
        assertEquals(Urgency.EXPIRED, deadlineUrgency(DeadlineKind.VEHICLE, today.minusDays(1), today))
    }

    @Test fun currencyParsingNeverUsesFloatingPoint() {
        assertEquals(1234L, parseMinorUnits("12.34", "EUR"))
        assertEquals(1234L, parseMinorUnits("12,34", "EUR"))
        assertNull(parseMinorUnits("12.345", "EUR"))
        assertEquals("12.34", formatMinorUnits(1234L, "EUR"))
    }

    @Test fun validationRejectsBlankNameAndInvalidRepeat() {
        assertEquals("Name is required", Deadline(kind = DeadlineKind.BILL, name = " ", dueOn = today).validationError())
        assertTrue(Deadline(kind = DeadlineKind.BILL, name = "Power", dueOn = today, repeatDays = 0).validationError() != null)
    }
}
