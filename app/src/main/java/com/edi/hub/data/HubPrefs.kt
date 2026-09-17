package com.edi.hub.data

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
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

    private companion object {
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
    }
}
