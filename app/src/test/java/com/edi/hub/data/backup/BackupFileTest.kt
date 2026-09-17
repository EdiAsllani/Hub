package com.edi.hub.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The header check is the first thing a restore does and the only one that needs no SQLite, so it
 * is a plain unit test. The quick_check half of `docs/plan.md` §7 lives in `androidTest` instead,
 * where a real SQLite exists.
 */
class BackupFileTest {

    @get:Rule val temp = TemporaryFolder()

    private fun database(userVersion: Int, size: Int = 4096): File {
        val bytes = ByteArray(size)
        "SQLite format 3".toByteArray(Charsets.US_ASCII).copyInto(bytes)
        bytes[15] = 0
        bytes[60] = (userVersion ushr 24).toByte()
        bytes[61] = (userVersion ushr 16).toByte()
        bytes[62] = (userVersion ushr 8).toByte()
        bytes[63] = userVersion.toByte()
        return temp.newFile().apply { writeBytes(bytes) }
    }

    @Test fun acceptsASqliteHeader() {
        assertTrue(hasSqliteHeader(database(userVersion = 1)))
    }

    @Test fun rejectsSomethingElseEntirely() {
        val notADatabase = temp.newFile().apply { writeBytes(ByteArray(4096) { 'x'.code.toByte() }) }
        assertFalse(hasSqliteHeader(notADatabase))
    }

    @Test fun rejectsAFileTooShortToHoldAHeader() {
        val stub = temp.newFile().apply { writeBytes("SQLite format 3".toByteArray()) }
        assertFalse(hasSqliteHeader(stub))
        assertNull(readUserVersion(stub))
    }

    @Test fun rejectsAnEmptyFile() {
        assertFalse(hasSqliteHeader(temp.newFile()))
    }

    @Test fun readsTheSchemaVersionFromTheHeader() {
        assertEquals(1, readUserVersion(database(userVersion = 1)))
        // A backup from a build that has run a migration this APK does not know about.
        assertEquals(7, readUserVersion(database(userVersion = 7)))
    }
}
