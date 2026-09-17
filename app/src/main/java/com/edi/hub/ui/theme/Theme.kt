package com.edi.hub.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * The fixed teal scheme ships as the default; dynamic colour is offered as a setting, which is what
 * `docs/HANDOVER.md` §4 asks for. The urgency ramp is supplied beside the scheme and never changes
 * with it — expired has to look expired on anyone's wallpaper.
 */
@Composable
fun HubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    CompositionLocalProvider(LocalUrgencyRamp provides if (darkTheme) DarkRamp else LightRamp) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HubTypography,
            shapes = HubShapes,
            content = content,
        )
    }
}
