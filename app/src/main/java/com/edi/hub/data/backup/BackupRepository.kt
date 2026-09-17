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
    data class Complete(val at: Instant) : BackupOutcome

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

            val target = targetDocument(tree)
                ?: return@withContext BackupOutcome.Failed("Hub could not write to that folder. Choose it again.")
            // "wt" truncates. The default mode can leave the tail of a larger previous backup behind.
            context.contentResolver.openOutputStream(target, "wt").use { out ->
                if (out == null) {
                    return@withContext BackupOutcome.Failed("Hub could not write to that folder. Choose it again.")
                }
                temp.inputStream().use { it.copyTo(out) }
            }

            val at = Instant.now()
            prefs.lastBackupAt = at
            BackupOutcome.Complete(at)
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
     * One fixed filename, overwritten in place — kind to Drive and Syncthing versioning, and it
     * keeps the folder from filling up. `createDocument` on a name that already exists silently
     * produces `hub (1).db`, so the existing document is looked up first.
     */
    private fun targetDocument(tree: Uri): Uri? {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeDocumentId)
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
                if (cursor.getString(1) == HubDatabase.NAME) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                }
            }
        }
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeDocumentId)
        return DocumentsContract.createDocument(
            context.contentResolver,
            parent,
            "application/octet-stream",
            HubDatabase.NAME,
        )
    }

    companion object {
        /**
         * This message has no design — `design/spec.md` §5 lists it as still to be drawn. It is
         * written plainly here so the case is handled rather than crashing.
         */
        const val NEWER_SCHEMA =
            "This backup was made by a newer version of Hub. Update Hub first, then restore it."

        private const val TEMP_BACKUP = "hub-backup.db"
        private const val TEMP_RESTORE = "hub-restore.db"
    }
}
