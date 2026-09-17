package com.edi.hub.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edi.hub.data.HubDatabase
import com.edi.hub.data.HubPrefs
import com.edi.hub.data.model.PantryItem
import com.edi.hub.data.model.PantryLocation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import java.time.LocalDate

/**
 * The acceptance criterion for the backup phase: insert rows, back up, wipe, restore, and find the
 * rows again. Everything else in the app writes data, and none of it may ship until this passes.
 *
 * The SAF half — streaming the vacuumed file into the user's folder — is a plain stream copy and is
 * exercised by hand instead; it needs a document provider and a person to pick a folder.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: HubDatabase
    private lateinit var backups: BackupRepository
    private lateinit var scratch: File

    @Before fun open() {
        context.deleteDatabase(HubDatabase.NAME)
        db = Room.databaseBuilder(context, HubDatabase::class.java, HubDatabase.NAME).build()
        backups = BackupRepository(context, db, HubPrefs(context.getSharedPreferences("hub-test", Context.MODE_PRIVATE)))
        scratch = File(context.cacheDir, "round-trip.db").also { it.delete() }
    }

    @After fun close() {
        db.close()
        scratch.delete()
        context.deleteDatabase(HubDatabase.NAME)
    }

    private fun milk(name: String) = PantryItem(
        barcode = "5449000000996",
        name = name,
        brand = "Vita",
        description = "1 L",
        location = PantryLocation.FRIDGE,
        addedAt = Instant.ofEpochMilli(1_700_000_000_000),
        expiresOn = LocalDate.of(2026, 10, 1),
    )

    /** Everything after `restore` reads through a fresh instance, exactly as the app does after its restart. */
    private fun reopen(): HubDatabase =
        Room.databaseBuilder(context, HubDatabase::class.java, HubDatabase.NAME).build()

    @Test fun survivesTheRoundTrip() = runBlocking {
        db.pantryDao().insert(milk("Milk"))
        db.pantryDao().insert(milk("More milk"))
        backups.vacuumInto(scratch)
        assertTrue(scratch.length() > 0)

        // Wipe: not just the rows, the file, the way reinstalling the app would.
        db.close()
        context.deleteDatabase(HubDatabase.NAME)
        db = reopen()
        assertEquals(0, db.pantryDao().observeActive().first().size)

        backups = BackupRepository(context, db, HubPrefs(context.getSharedPreferences("hub-test", Context.MODE_PRIVATE)))
        assertEquals(RestoreOutcome.Restored, backups.restore(Uri.fromFile(scratch)))

        db = reopen()
        val restored = db.pantryDao().observeActive().first()
        assertEquals(2, restored.size)
        assertEquals(setOf("Milk", "More milk"), restored.map { it.name }.toSet())
        assertEquals("1 L", restored.first().description)
        assertEquals(LocalDate.of(2026, 10, 1), restored.first().expiresOn)
    }

    @Test fun refusesADamagedBackupAndLeavesTheDataAlone() = runBlocking {
        db.pantryDao().insert(milk("Milk"))
        backups.vacuumInto(scratch)

        // Keep the header, lose the pages behind it: the header check passes and quick_check catches it.
        RandomAccessFile(scratch, "rw").use { it.setLength(scratch.length() / 2) }

        val outcome = backups.restore(Uri.fromFile(scratch))
        assertTrue("expected a refusal, got $outcome", outcome is RestoreOutcome.Refused)
        assertEquals(1, db.pantryDao().observeActive().first().size)
    }

    @Test fun refusesABackupFromANewerBuildAndLeavesTheDataAlone() = runBlocking {
        db.pantryDao().insert(milk("Milk"))
        backups.vacuumInto(scratch)

        // Byte 60 of the header is the schema version. Pretend this file came from a later Hub.
        RandomAccessFile(scratch, "rw").use { file ->
            file.seek(60)
            file.writeInt(HubDatabase.VERSION + 1)
        }

        val outcome = backups.restore(Uri.fromFile(scratch))
        assertEquals(RestoreOutcome.Refused(BackupRepository.NEWER_SCHEMA), outcome)
        assertEquals(1, db.pantryDao().observeActive().first().size)
    }

    @Test fun refusesSomethingThatIsNotADatabaseAtAll() = runBlocking {
        db.pantryDao().insert(milk("Milk"))
        scratch.writeBytes(ByteArray(8192) { 'x'.code.toByte() })

        val outcome = backups.restore(Uri.fromFile(scratch))
        assertTrue("expected a refusal, got $outcome", outcome is RestoreOutcome.Refused)
        assertEquals(1, db.pantryDao().observeActive().first().size)
    }
}
