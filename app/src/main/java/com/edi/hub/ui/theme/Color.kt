package com.edi.hub.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The fixed scheme, seeded from the deep teal in `design/spec.md` §1. Teal because the urgency
 * ramp owns red, deep orange and amber, and a brand accent inside that arc makes "expired" read
 * as decoration. The tertiary is the muted plum, and it has exactly one job: the throw-away swipe.
 *
 * `primary` and `tertiary` are the spec's hex values verbatim; every other role is the Material 3
 * tonal palette built from them, so containers and surfaces stay consistent with the two that were
 * drawn.
 */
internal val LightScheme = lightColorScheme(
    primary = Color(0xFF0C6B66),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA2F1EA),
    onPrimaryContainer = Color(0xFF00201E),
    inversePrimary = Color(0xFF81D5CF),
    secondary = Color(0xFF4A6361),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E5),
    onSecondaryContainer = Color(0xFF051F1E),
    tertiary = Color(0xFF6A4E71),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFAD7FF),
    onTertiaryContainer = Color(0xFF291231),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7FAF8),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFF7FAF8),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFDAE5E3),
    onSurfaceVariant = Color(0xFF3F4947),
    surfaceTint = Color(0xFF0C6B66),
    inverseSurface = Color(0xFF2D3130),
    inverseOnSurface = Color(0xFFEFF1F0),
    outline = Color(0xFF6F7978),
    outlineVariant = Color(0xFFBEC9C7),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF7FAF8),
    surfaceDim = Color(0xFFD8DBD9),
    surfaceContainer = Color(0xFFECEEED),
    surfaceContainerHigh = Color(0xFFE6E9E7),
    surfaceContainerHighest = Color(0xFFE0E3E2),
    surfaceContainerLow = Color(0xFFF2F4F3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

internal val DarkScheme = darkColorScheme(
    primary = Color(0xFF81D5CF),
    onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF00504C),
    onPrimaryContainer = Color(0xFFA2F1EA),
    inversePrimary = Color(0xFF0C6B66),
    secondary = Color(0xFFB0CCC9),
    onSecondary = Color(0xFF1C3533),
    secondaryContainer = Color(0xFF324B49),
    onSecondaryContainer = Color(0xFFCCE8E5),
    tertiary = Color(0xFFD8BCE0),
    onTertiary = Color(0xFF402747),
    tertiaryContainer = Color(0xFF583D5F),
    onTertiaryContainer = Color(0xFFFAD7FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101414),
    onBackground = Color(0xFFE0E3E2),
    surface = Color(0xFF101414),
    onSurface = Color(0xFFE0E3E2),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C7),
    surfaceTint = Color(0xFF81D5CF),
    inverseSurface = Color(0xFFE0E3E2),
    inverseOnSurface = Color(0xFF2D3130),
    outline = Color(0xFF889391),
    outlineVariant = Color(0xFF3F4947),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF363A39),
    surfaceDim = Color(0xFF101414),
    surfaceContainer = Color(0xFF1D2020),
    surfaceContainerHigh = Color(0xFF272B2A),
    surfaceContainerHighest = Color(0xFF323535),
    surfaceContainerLow = Color(0xFF191C1C),
    surfaceContainerLowest = Color(0xFF0B0F0E),
)
