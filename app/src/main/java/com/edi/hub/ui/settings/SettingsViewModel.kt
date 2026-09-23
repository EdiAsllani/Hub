package com.edi.hub.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edi.hub.data.HubPrefs
import com.edi.hub.data.backup.BackupOutcome
import com.edi.hub.data.backup.BackupRepository
import com.edi.hub.data.backup.RestoreOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.edi.hub.work.cancelNudges
import com.edi.hub.work.scheduleNudges
import java.time.Instant
import javax.inject.Inject

/** What the user is about to throw away, and what they are about to put in its place. */
data class PendingRestore(
    val uri: Uri,
    val fileName: String,
    val modifiedAt: Instant?,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val backups: BackupRepository,
    private val prefs: HubPrefs,
) : ViewModel() {

    val folder: Uri? get() = backups.folder
    val lastBackupAt: Instant? get() = backups.lastBackupAt
    val dynamicColor: Boolean get() = prefs.dynamicColor
    var currencyCode by mutableStateOf(prefs.currencyCode)
        private set

    var busy by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var pending by mutableStateOf<PendingRestore?>(null)
        private set

    /** Set once the swap has happened. Nothing may read the database after this. */
    var restarting by mutableStateOf(false)
        private set

    fun setDynamicColor(enabled: Boolean) {
        prefs.dynamicColor = enabled
    }

    fun onCurrencyCodeChanged(value: String) {
        currencyCode = value.filter(Char::isLetter).take(3).uppercase()
        if (currencyCode.length == 3) prefs.currencyCode = currencyCode
    }

    val dailyReminder: Boolean get() = prefs.dailyReminder

    /**
     * Only reached once the permission is settled. Without it both summaries are dropped silently,
     * so the switch stays off rather than pretending to be on.
     */
    fun setDailyReminder(enabled: Boolean) {
        prefs.dailyReminder = enabled
        if (enabled) scheduleNudges(context) else cancelNudges(context)
        if (!enabled) message = "Notifications off."
    }

    fun reminderDenied() {
        prefs.dailyReminder = false
        message = "Hub needs permission to post notifications before it can send them."
    }

    fun rememberFolder(tree: Uri) {
        runCatching { backups.rememberFolder(tree) }
            .onFailure { message = "Hub could not keep access to that folder. Try another one." }
    }

    fun backUpNow() {
        if (busy) return
        busy = true
        viewModelScope.launch {
            message = when (val outcome = backups.backUp()) {
                BackupOutcome.Complete -> "Backed up."
                is BackupOutcome.Failed -> outcome.message
            }
            busy = false
        }
    }

    /** Reads the file's name and date so the confirmation can say which backup this is. */
    fun proposeRestore(uri: Uri) {
        viewModelScope.launch {
            pending = withContext(Dispatchers.IO) { describe(uri) }
        }
    }

    fun cancelRestore() {
        pending = null
    }

    fun confirmRestore() {
        val target = pending ?: return
        pending = null
        busy = true
        viewModelScope.launch {
            when (val outcome = backups.restore(target.uri)) {
                is RestoreOutcome.Restored -> restarting = true
                is RestoreOutcome.Refused -> message = outcome.message
                is RestoreOutcome.Failed -> message = outcome.message
            }
            busy = false
        }
    }

    fun clearMessage() {
        message = null
    }

    private fun describe(uri: Uri): PendingRestore {
        var name = "the file you picked"
        var modified: Instant? = null
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, DocumentsContract.Document.COLUMN_LAST_MODIFIED),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)?.let { name = it }
                if (!cursor.isNull(1)) modified = Instant.ofEpochMilli(cursor.getLong(1))
            }
        }
        return PendingRestore(uri, name, modified)
    }
}
