package com.edi.hub.data

import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The handful of values that are settings rather than data: they never belong in the database,
 * because a restore must not drag another device's backup folder along with it.
 *
 * Each one is Compose state as well as a stored value, so a settings switch and the theme above it
 * stay in step without a listener.
 */
@Singleton
class HubPrefs @Inject constructor(private val prefs: SharedPreferences) {

    private val dynamicColorState = mutableStateOf(prefs.getBoolean(KEY_DYNAMIC_COLOR, false))

    /** Off by default: the fixed scheme is the design, and the wallpaper's is the alternative. */
    var dynamicColor: Boolean
        get() = dynamicColorState.value
        set(value) {
            dynamicColorState.value = value
            prefs.edit { putBoolean(KEY_DYNAMIC_COLOR, value) }
        }

    private val backupFolderState = mutableStateOf(prefs.getString(KEY_BACKUP_FOLDER, null))

    /** The SAF tree the user picked once. Null until they have. */
    var backupFolder: Uri?
        get() = backupFolderState.value?.let(Uri::parse)
        set(value) {
            backupFolderState.value = value?.toString()
            prefs.edit { putString(KEY_BACKUP_FOLDER, value?.toString()) }
        }

    private val lastBackupAtState = mutableStateOf(prefs.getLong(KEY_LAST_BACKUP_AT, 0L))

    var lastBackupAt: Instant?
        get() = lastBackupAtState.value.takeIf { it > 0L }?.let(Instant::ofEpochMilli)
        set(value) {
            val millis = value?.toEpochMilli() ?: 0L
            lastBackupAtState.value = millis
            prefs.edit { putLong(KEY_LAST_BACKUP_AT, millis) }
        }

    private companion object {
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_BACKUP_FOLDER = "backup_folder"
        const val KEY_LAST_BACKUP_AT = "last_backup_at"
    }
}
