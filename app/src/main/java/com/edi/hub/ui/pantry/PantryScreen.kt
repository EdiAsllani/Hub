package com.edi.hub.ui.pantry

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edi.hub.data.dao.PantryCard
import com.edi.hub.data.model.Disposition
import com.edi.hub.data.model.PantryLocation
import com.edi.hub.ui.components.UrgencyChip
import com.edi.hub.ui.theme.numeric
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun PantryScreen(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: PantryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    PantryContent(
        state = state,
        onLocation = viewModel::show,
        onFilter = viewModel::apply,
        onSort = viewModel::apply,
        onResolve = { card, disposition ->
            viewModel.resolve(card, disposition) { resolved ->
                scope.launch {
                    // ponytail: a second swipe inside the four seconds replaces this snackbar and
                    // the first undo is lost. A queue of pending undos is the fix if that bites.
                    val result = snackbarHostState.showSnackbar(
                        message = when (resolved.disposition) {
                            Disposition.CONSUMED -> "${resolved.name} used"
                            Disposition.DISCARDED -> "${resolved.name} binned"
                        },
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo(resolved)
                }
            }
        },
        modifier = modifier,
    )
}

/** Split out from the screen so the previews can render the sample household without Hilt. */
@Composable
fun PantryContent(
    state: PantryUiState,
    onLocation: (PantryLocation) -> Unit,
    onFilter: (PantryFilter) -> Unit,
    onSort: (PantrySort) -> Unit,
    modifier: Modifier = Modifier,
    onResolve: (PantryCard, Disposition) -> Unit = { _, _ -> },
) {
    Column(modifier.fillMaxSize()) {
        // The tabs stay on every state: first launch is where the three shelves are introduced,
        // and filter-matches-nothing has to keep the user's place rather than replace the screen.
        LocationTabs(state.location, state.counts, onLocation)
        // The chips only appear where there is something to filter.
        if (state.emptiness == PantryEmptiness.NONE ||
            state.emptiness == PantryEmptiness.FILTER_MATCHES_NOTHING
        ) {
            FilterRow(state.filter, state.sort, onFilter, onSort)
        }

        when (state.emptiness) {
            PantryEmptiness.FIRST_LAUNCH -> FirstLaunch()
            PantryEmptiness.EMPTY_LOCATION -> EmptyLocation(state.location)
            PantryEmptiness.FILTER_MATCHES_NOTHING -> NothingMatches(state.filter) {
                onFilter(PantryFilter.ALL)
            }
            PantryEmptiness.NONE -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Keyed on the group, not the row id: a ×n card rewrites itself in place when one
                // entry goes, and keying on the row would remount it instead.
                items(state.cards, key = PantryCard::groupKey) { card ->
                    SwipeToResolve(
                        card = card,
                        onResolve = { onResolve(card, it) },
                        modifier = Modifier.animateItem(),
                    ) {
                        PantryCardRow(card, state.today)
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationTabs(
    selected: PantryLocation,
    counts: Map<PantryLocation, Int>,
    onLocation: (PantryLocation) -> Unit,
) {
    TabRow(selectedTabIndex = PantryLocation.entries.indexOf(selected)) {
        PantryLocation.entries.forEach { location ->
            val count = counts[location] ?: 0
            Tab(
                selected = location == selected,
                onClick = { onLocation(location) },
                text = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(location.label)
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.labelMedium.numeric(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun FilterRow(
    filter: PantryFilter,
    sort: PantrySort,
    onFilter: (PantryFilter) -> Unit,
    onSort: (PantrySort) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PantryFilter.entries.forEach { entry ->
            FilterChip(
                selected = entry == filter,
                onClick = { onFilter(entry) },
                label = { Text(entry.label) },
            )
        }
        TextButton(onClick = { onSort(if (sort == PantrySort.BY_DATE) PantrySort.BY_NAME else PantrySort.BY_DATE) }) {
            Text(sort.label)
        }
    }
}

@Composable
private fun PantryCardRow(card: PantryCard, today: LocalDate, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = card.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Beside the name, because that is where the eye already is — and it counts
                    // down in place rather than the row collapsing, which is what tells the user
                    // one box went and another is still here.
                    AnimatedContent(
                        targetState = card.entryCount,
                        transitionSpec = { rewriteInPlace },
                        label = "countBadge",
                    ) { count ->
                        if (count > 1) {
                            Text(
                                text = "×$count",
                                style = MaterialTheme.typography.titleMedium.numeric(),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                val meta = listOfNotNull(card.brand, card.description, card.location.label)
                Text(
                    text = meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedContent(
                targetState = card.expiresOn,
                transitionSpec = { rewriteInPlace },
                label = "cardDate",
            ) { expiresOn ->
                UrgencyChip(expiresOn, today = today)
            }
        }
    }
}

@Composable
private fun FirstLaunch() = EmptyBoard(
    headline = "Nothing in the kitchen yet",
    body = "Hub keeps three shelves — fridge, freezer and pantry. Scan a barcode with the button " +
        "below and Hub looks the product up, asks when it goes off, and remembers the answer. " +
        "The next time you scan the same thing, it fills the date in for you.",
)

@Composable
private fun EmptyLocation(location: PantryLocation) = EmptyBoard(
    headline = "The ${location.label.lowercase()} is empty",
    body = when (location) {
        PantryLocation.FRIDGE -> "Milk, yogurt, things with a few days on them."
        PantryLocation.FREEZER -> "Peas, bread, whatever you bought two for one."
        PantryLocation.PANTRY -> "Rice, tins, the jar of ajvar that outlives everything."
    },
)

@Composable
private fun NothingMatches(filter: PantryFilter, onClear: () -> Unit) = EmptyBoard(
    headline = when (filter) {
        PantryFilter.EXPIRED -> "Nothing here is expired"
        PantryFilter.SOON -> "Nothing goes off this week"
        PantryFilter.NO_DATE -> "Everything here has a date on it"
        PantryFilter.ALL -> "Nothing here"
    },
    body = "That is the good news. The shelf itself is not empty.",
    action = "Show everything",
    onAction = onClear,
)

@Composable
private fun EmptyBoard(
    headline: String,
    body: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(headline, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (action != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}

/** 300 ms standard: the card rewrites itself rather than being replaced. `design/spec.md` §6. */
private val rewriteInPlace: ContentTransform =
    fadeIn(tween(durationMillis = 300)) togetherWith fadeOut(tween(durationMillis = 300))

val PantryLocation.label: String
    get() = when (this) {
        PantryLocation.FRIDGE -> "Fridge"
        PantryLocation.FREEZER -> "Freezer"
        PantryLocation.PANTRY -> "Pantry"
    }
