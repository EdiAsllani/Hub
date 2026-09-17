package com.edi.hub.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.edi.hub.R

@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, weight: FontWeight) =
    Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

/** Display, headlines, titles — and every numeral in the app. Bundled, never fetched. */
val Gabarito = FontFamily(
    variable(R.font.gabarito, FontWeight.Normal),
    variable(R.font.gabarito, FontWeight.Medium),
    variable(R.font.gabarito, FontWeight.SemiBold),
    variable(R.font.gabarito, FontWeight.Bold),
)

/** Body and labels. */
val Figtree = FontFamily(
    variable(R.font.figtree, FontWeight.Normal),
    variable(R.font.figtree, FontWeight.Medium),
    variable(R.font.figtree, FontWeight.SemiBold),
)

/**
 * Tabular figures for counts and days-left. Proportional digits visibly jitter through the
 * `animateItem()` reordering, which is the most-seen animation in the app. Both bundled fonts
 * expose `tnum`; if either is ever swapped, check that first.
 */
fun TextStyle.numeric(): TextStyle = copy(fontFamily = Gabarito, fontFeatureSettings = "tnum")

private val base = Typography()

val HubTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Gabarito),
    displayMedium = base.displayMedium.copy(fontFamily = Gabarito),
    displaySmall = base.displaySmall.copy(fontFamily = Gabarito),
    headlineLarge = base.headlineLarge.copy(fontFamily = Gabarito),
    headlineMedium = base.headlineMedium.copy(fontFamily = Gabarito),
    headlineSmall = base.headlineSmall.copy(fontFamily = Gabarito),
    titleLarge = base.titleLarge.copy(fontFamily = Gabarito),
    titleMedium = base.titleMedium.copy(fontFamily = Gabarito),
    titleSmall = base.titleSmall.copy(fontFamily = Gabarito),
    bodyLarge = base.bodyLarge.copy(fontFamily = Figtree),
    bodyMedium = base.bodyMedium.copy(fontFamily = Figtree),
    bodySmall = base.bodySmall.copy(fontFamily = Figtree),
    labelLarge = base.labelLarge.copy(fontFamily = Figtree),
    labelMedium = base.labelMedium.copy(fontFamily = Figtree),
    labelSmall = base.labelSmall.copy(fontFamily = Figtree),
)
