package com.edi.hub.data.backup

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.WorkManager
import com.edi.hub.data.HubDatabase
import com.edi.hub.data.HubPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** A backup either lands or it does not, and the user is told which in plain words. */
sealed interface BackupOutcome {
    data object Complete : BackupOutcome

    data class Failed(val message: String) : BackupOutcome
}

sealed interface RestoreOutcome {
    /** The live database has been replaced. Hub has to restart before anything reads it. */
    data object Restored : RestoreOutcome

    /** Rejected during validation. Nothing was touched, so there is nothing to undo. */
    data class Refused(val message: String) : RestoreOutcome

    /** Failed during the swap and rolled back to the database that was already there. */
    data class Failed(val message: String) : RestoreOutcome
}

/**
 * The one place in Hub where a mistake destroys real data rather than annoying someone. The order
 * of the steps below is `docs/plan.md` §5 and is not free to rearrange.
 */
@Singleton
class BackupRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: HubDatabase,
    private val prefs: HubPrefs,
) {
    val folder: Uri? get() = prefs.backupFolder

    val lastBackupAt: Instant? get() = prefs.lastBackupAt

    /**
     * Keeps the folder across reboots. Without the persisted permission the URI is good for this
     * process only, and the next backup would fail with nothing to show for it.
     */
    fun rememberFolder(tree: Uri) {
        context.contentResolver.takePersistableUriPermission(
            tree,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs.backupFolder = tree
    }

    suspend fun backUp(): BackupOutcome = withContext(Dispatchers.IO) {
        val tree = prefs.backupFolder
            ?: return@withContext BackupOutcome.Failed("Choose a backup folder first.")
        val temp = File(context.cacheDir, TEMP_BACKUP)
        try {
            vacuumInto(temp)
            if (temp.length() == 0L) {
                return@withContext BackupOutcome.Failed("Hub produced an empty backup. Nothing was written.")
            }

            val staging = findDocument(tree, STAGED_NAME) ?: createDocument(tree, STAGED_NAME)
            ?: return@withContext BackupOutcome.Failed("Hub could not write to that folder. Choose it again.")

            // Whether the last step can happen at all is decided before the first one does. A
            // provider that cannot rename would otherwise be found out after the old backup is
            // already gone, which is the failure this whole sequence exists to prevent.
            if (!supportsRename(staging)) return@withContext overwriteInPlace(tree, temp)

            if (!writeInto(staging, temp)) {
                return@withContext BackupOutcome.Failed("Hub could not write to that folder. Choose it again.")
            }

            // Only now, with a complete backup already on disk under another name, is the old one
            // touched. Everything past this point is a metadata operation measured in milliseconds,
            // rather than a copy that can die halfway through.
            findDocument(tree, HubDatabase.NAME)?.let {
                DocumentsContract.deleteDocument(context.contentResolver, it)
            }
            runCatching {
                DocumentsContract.renameDocument(context.contentResolver, staging, HubDatabase.NAME)
            }.getOrNull() ?: return@withContext BackupOutcome.Failed(STRANDED)

            prefs.lastBackupAt = Instant.now()
            BackupOutcome.Complete
        } catch (e: Exception) {
            BackupOutcome.Failed(e.message ?: "The backup did not finish.")
        } finally {
            temp.delete()
        }
    }

    /**
     * The half of a backup that has nothing to do with the user's folder, kept separate so the
     * round-trip test can exercise it without a document provider.
     */
    internal fun vacuumInto(target: File) {
        // VACUUM INTO refuses to overwrite, so the target has to be gone first.
        target.delete()
        // execSQL rather than query(): a query has to be stepped or the statement never runs,
        // and it has to run outside a transaction.
        db.openHelper.writableDatabase.execSQL("VACUUM INTO ?", arrayOf(target.absolutePath))
    }

    /**
     * Validates the incoming file completely before the live database is touched at all. That keeps
     * the window in which Room is closed as short as it can be, and turns the worst case from a
     * corruption recovery into a copy that did not happen.
     */
    suspend fun restore(source: Uri): RestoreOutcome = withContext(Dispatchers.IO) {
        val incoming = File(context.cacheDir, TEMP_RESTORE)
        try {
            incoming.delete()
            val copied = context.contentResolver.openInputStream(source)?.use { input ->
                incoming.outputStream().use { input.copyTo(it) }
            }
            if (copied == null) return@withContext RestoreOutcome.Refused("Hub could not read that file.")

            if (!hasSqliteHeader(incoming)) {
                return@withContext RestoreOutcome.Refused("That file is not a Hub backup.")
            }
            val version = readUserVersion(incoming)
            if (version == null || version > HubDatabase.VERSION) {
                return@withContext RestoreOutcome.Refused(NEWER_SCHEMA)
            }
            if (!passesQuickCheck(incoming)) {
                return@withContext RestoreOutcome.Refused("That backup is damaged, so Hub left your data alone.")
            }

            swapIn(incoming)
        } catch (e: Exception) {
            RestoreOutcome.Refused(e.message ?: "Hub could not read that file.")
        } finally {
            incoming.delete()
        }
    }

    /** Everything past this point touches the live file, and every step of it is reversible until the last. */
    private fun swapIn(incoming: File): RestoreOutcome {
        // The daily job holds a connection. There is only ever one job, so cancelling all of them
        // is the same thing and needs no name shared across packages; KEEP re-enqueues on next launch.
        WorkManager.getInstance(context).cancelAllWork().result.get()
        db.close()

        val live = context.getDatabasePath(HubDatabase.NAME)
        val rolledBack = File(live.parentFile, "${HubDatabase.NAME}.bak")
        rolledBack.delete()
        // Renamed, never deleted: if the copy below fails there is still a database to come back to.
        if (live.exists() && !live.renameTo(rolledBack)) {
            return RestoreOutcome.Failed("Hub could not set your current data aside, so nothing was replaced.")
        }

        return try {
            incoming.copyTo(live, overwrite = true)
            // A stale write-ahead log replayed over a freshly restored file is the classic silent corruption.
            File(live.parentFile, "${HubDatabase.NAME}-wal").delete()
            File(live.parentFile, "${HubDatabase.NAME}-shm").delete()
            RestoreOutcome.Restored
        } catch (e: Exception) {
            live.delete()
            rolledBack.renameTo(live)
            RestoreOutcome.Failed(e.message ?: "The restore did not finish. Your data is unchanged.")
        }
    }

    private fun passesQuickCheck(file: File): Boolean = try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            raw.rawQuery("PRAGMA quick_check", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
            }
        }
    } catch (e: Exception) {
        false
    }

    /**
     * The fallback for a provider that cannot rename: the old behaviour, which truncates the only
     * backup before it writes the new one. Kept because losing the ability to back up at all is
     * worse than a window, and taken only when the check above proves the safe path is unavailable.
     */
    // ponytail: no second safe path for rename-less providers. The device's own storage provider
    // supports rename, which is where a backup folder lives. Revisit if a real one turns up that
    // does not — a timestamped filename per backup would sidestep renaming entirely.
    private fun overwriteInPlace(tree: Uri, temp: File): BackupOutcome {
        val target = findDocument(tree, HubDatabase.NAME)
            ?: createDocument(tree, HubDatabase.NAME)
            ?: return BackupOutcome.Failed("Hub could not write to that folder. Choose it again.")
        if (!writeInto(target, temp)) {
            return BackupOutcome.Failed("Hub could not write to that folder. Choose it again.")
        }
        prefs.lastBackupAt = Instant.now()
        return BackupOutcome.Complete
    }

    /** "wt" truncates. The default mode can leave the tail of a larger previous backup behind. */
    private fun writeInto(target: Uri, temp: File): Boolean {
        val stream = context.contentResolver.openOutputStream(target, "wt") ?: return false
        stream.use { out -> temp.inputStream().use { it.copyTo(out) } }
        return true
    }

    /**
     * One fixed filename — kind to Drive and Syncthing versioning, and it keeps the folder from
     * filling up. `createDocument` on a name that already exists silently produces `hub (1).db`,
     * so an existing document is always looked up before one is made.
     */
    private fun findDocument(tree: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        context.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                }
            }
        }
        return null
    }

    private fun createDocument(tree: Uri, name: String): Uri? = DocumentsContract.createDocument(
        context.contentResolver,
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)),
        "application/octet-stream",
        name,
    )

    private fun supportsRename(document: Uri): Boolean =
        context.contentResolver.query(
            document,
            arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
            null,
            null,
            null,
        )?.use { cursor ->
            cursor.moveToFirst() &&
                cursor.getInt(0) and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0
        } ?: false

    companion object {
        /**
         * This message has no design — `design/spec.md` §5 lists it as still to be drawn. It is
         * written plainly here so the case is handled rather than crashing.
         */
        const val NEWER_SCHEMA =
            "This backup was made by a newer version of Hub. Update Hub first, then restore it."

        /** Where the new backup lands while the previous one is still the file on disk. */
        private const val STAGED_NAME = "${HubDatabase.NAME}.tmp"

        /**
         * Names the file the user has to rename by hand, because it is the only copy left and a
         * message that does not name it is a message they cannot act on.
         */
        const val STRANDED =
            "Hub wrote the backup but could not put it in place. Your folder holds " +
                "$STAGED_NAME — rename it to ${HubDatabase.NAME} to use it."

        private const val TEMP_BACKUP = "hub-backup.db"
        private const val TEMP_RESTORE = "hub-restore.db"
    }
}
