package com.edi.hub.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edi.hub.data.model.Deadline
import com.edi.hub.data.model.DeadlineKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DeadlineDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        HubDatabase::class.java,
    ).build()

    @After fun close() = db.close()

    @Test fun activeSnoozeAndCompletionPersist() = runBlocking {
        val today = LocalDate.of(2026, 9, 23)
        val oneShot = db.deadlineDao().insert(
            Deadline(kind = DeadlineKind.BILL, name = "Power", dueOn = today.plusDays(2)),
        )
        val recurring = db.deadlineDao().insert(
            Deadline(kind = DeadlineKind.UPKEEP, name = "Filter", dueOn = today.minusDays(9), repeatDays = 7),
        )

        db.deadlineDao().snooze(oneShot, today.plusDays(1).toEpochDay())
        assertEquals(today.plusDays(1), db.deadlineDao().find(oneShot)?.snoozedUntil)

        db.deadlineDao().complete(oneShot, today, Instant.EPOCH)
        assertEquals(Instant.EPOCH, db.deadlineDao().find(oneShot)?.completedAt)

        val advanced = db.deadlineDao().complete(recurring, today, Instant.EPOCH)
        assertEquals(today.plusDays(5), advanced?.dueOn)
        assertNull(advanced?.completedAt)
        assertEquals(listOf("Filter"), db.deadlineDao().observeActive().first().map { it.name })
    }
}
