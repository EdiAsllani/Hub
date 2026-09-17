package com.edi.hub.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edi.hub.data.model.Disposition
import com.edi.hub.data.model.PantryItem
import com.edi.hub.ui.chipKey
import com.edi.hub.ui.components.UrgencyChip
import com.edi.hub.ui.nameKey
import com.edi.hub.ui.pantry.label
import com.edi.hub.ui.sharedElement
import com.edi.hub.ui.theme.numeric
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val longDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Composable
fun ItemDetailScreen(
    onGone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ItemDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ItemDetailContent(
        state = state,
        onResolve = { disposition -> viewModel.resolve(disposition, onEmptied = onGone) },
        modifier = modifier,
    )
}

@Composable
fun ItemDetailContent(
    state: ItemDetailUiState,
    onResolve: (Disposition) -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = state.entries.firstOrNull() ?: return
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = first.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.sharedElement(nameKey(state.groupKey)),
            )
            if (state.entries.size > 1) {
                Text("×${state.entries.size}", style = MaterialTheme.typography.headlineSmall.numeric())
            }
        }
        UrgencyChip(
            expiresOn = first.expiresOn,
            today = state.today,
            modifier = Modifier.sharedElement(chipKey(state.groupKey)),
        )

        val meta = listOfNotNull(first.brand, first.description, first.location.label)
        if (meta.isNotEmpty()) {
            Text(
                meta.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The only screen that explains the count model, and it explains it by showing it.
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                state.entries.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    EntryRow(entry, goesFirst = index == 0 && state.entries.size > 1, today = state.today)
                }
            }
        }

        if (state.learnedShelfLifeDays != null) LearnedShelfLife(state.learnedShelfLifeDays)

        // The same two words and the same two colours as the swipes. A gesture and a button that
        // mean the same thing must not be two vocabularies for it.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onResolve(Disposition.CONSUMED) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.RestaurantMenu, contentDescription = null)
                Text("Used one", Modifier.padding(start = 8.dp))
            }
            Button(
                onClick = { onResolve(Disposition.DISCARDED) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                Text("Binned", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun EntryRow(entry: PantryItem, goesFirst: Boolean, today: LocalDate) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = entry.expiresOn?.format(longDate) ?: "No date",
                style = MaterialTheme.typography.bodyLarge.numeric(),
            )
            if (goesFirst) {
                Text(
                    "Goes first",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        UrgencyChip(entry.expiresOn, today = today)
    }
}

/**
 * The one mechanism in Hub that changes behaviour without being asked to, so it is the one worth
 * spelling out. Everything else the user does is exactly what they told it to do.
 */
@Composable
private fun LearnedShelfLife(days: Int) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "You have told Hub this keeps for $days days. The next time you scan it, that is " +
                "the date it will offer — change it there and Hub will learn the new one.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}
