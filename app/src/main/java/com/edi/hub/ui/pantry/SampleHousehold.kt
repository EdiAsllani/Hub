package com.edi.hub.ui.pantry

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.edi.hub.data.dao.PantryCard
import com.edi.hub.data.model.PantryLocation
import com.edi.hub.ui.theme.HubTheme
import java.time.LocalDate

/**
 * One believable household, the same rows on every board — `design/spec.md` §8. Every size is free
 * text, exactly as it would be typed off the pack. The freezer is empty, which is what makes it the
 * board for the empty-location state.
 */
private val TODAY: LocalDate = LocalDate.of(2026, 9, 17)

private fun card(
    name: String,
    brand: String? = null,
    description: String? = null,
    location: PantryLocation = PantryLocation.FRIDGE,
    expiresOn: LocalDate? = null,
    entries: Int = 1,
    barcode: String? = "sample-$name",
) = PantryCard(
    groupKey = barcode ?: "id:$name",
    barcode = barcode,
    name = name,
    brand = brand,
    description = description,
    location = location,
    expiresOn = expiresOn,
    entryCount = entries,
)

val SampleFridge = listOf(
    card("Baby spinach", description = "200 g", expiresOn = TODAY.minusDays(4), barcode = null),
    card("Greek yogurt", brand = "Rugove", description = "400 g", expiresOn = TODAY.minusDays(2)),
    card("Milk", brand = "Vita", description = "1 L", expiresOn = TODAY.plusDays(1)),
    card("Tomatoes", description = "500 g", expiresOn = TODAY.plusDays(3), barcode = null),
    card("Eggs", brand = "Rugove", description = "24 cope", expiresOn = TODAY.plusDays(5), entries = 2),
)

val SamplePantryShelf = listOf(
    card("Sourdough loaf", description = "1 pcs", location = PantryLocation.PANTRY, expiresOn = TODAY, barcode = null),
    card("Ajvar, mild", description = "330 g", location = PantryLocation.PANTRY, expiresOn = TODAY.plusMonths(8)),
    card("Rice, long grain", description = "1 kg", location = PantryLocation.PANTRY, expiresOn = null),
)

private val SampleCounts = mapOf(
    PantryLocation.FRIDGE to SampleFridge.size,
    PantryLocation.FREEZER to 0,
    PantryLocation.PANTRY to SamplePantryShelf.size,
)

private fun sampleState(
    location: PantryLocation = PantryLocation.FRIDGE,
    cards: List<PantryCard> = SampleFridge,
    filter: PantryFilter = PantryFilter.ALL,
    emptiness: PantryEmptiness = PantryEmptiness.NONE,
    counts: Map<PantryLocation, Int> = SampleCounts,
) = PantryUiState(
    location = location,
    counts = counts,
    cards = cards,
    filter = filter,
    emptiness = emptiness,
    today = TODAY,
)

@Composable
private fun Board(state: PantryUiState) = HubTheme {
    Surface { PantryContent(state, {}, {}, {}) }
}

@Preview(name = "Pantry, fridge, light", showBackground = true, heightDp = 720)
@Preview(name = "Pantry, fridge, dark", showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PantryPopulatedPreview() = Board(sampleState())

@Preview(name = "Pantry, pantry shelf", showBackground = true, heightDp = 720)
@Composable
private fun PantryShelfPreview() =
    Board(sampleState(location = PantryLocation.PANTRY, cards = SamplePantryShelf))

@Preview(name = "Pantry, freezer empty, dark", showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun EmptyLocationPreview() = Board(
    sampleState(
        location = PantryLocation.FREEZER,
        cards = emptyList(),
        emptiness = PantryEmptiness.EMPTY_LOCATION,
    ),
)

@Preview(name = "Pantry, filter matches nothing", showBackground = true, heightDp = 720)
@Composable
private fun NothingMatchesPreview() = Board(
    sampleState(
        cards = emptyList(),
        filter = PantryFilter.EXPIRED,
        emptiness = PantryEmptiness.FILTER_MATCHES_NOTHING,
    ),
)

@Preview(name = "Pantry, first launch", showBackground = true, heightDp = 720)
@Composable
private fun FirstLaunchPreview() = Board(
    sampleState(
        cards = emptyList(),
        emptiness = PantryEmptiness.FIRST_LAUNCH,
        counts = PantryLocation.entries.associateWith { 0 },
    ),
)
