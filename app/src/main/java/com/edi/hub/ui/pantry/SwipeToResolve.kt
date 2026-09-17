package com.edi.hub.ui.pantry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.edi.hub.data.dao.PantryCard
import com.edi.hub.data.model.Disposition

/** Both gestures arm at 40% of the row and commit on release. Below that the row just snaps back. */
private const val ARM_FRACTION = 0.4f

/**
 * Swipe right is consumed and teal, swipe left is binned and plum. Neither gets a confirmation
 * dialog, precisely because both get four seconds of undo — a dialog on every eaten yogurt would
 * make the gesture cost more than the checkbox it replaced.
 *
 * A swipe on a `×n` card does not remove the row. The entry goes, the badge counts down, the card
 * rewrites itself to the next date, and the box is reset so the row stays where it was. Only the
 * last one collapses. If that difference is not legible the gesture feels broken, which is why the
 * reset happens here rather than letting the box dismiss and the list rebuild the row.
 */
@Composable
fun SwipeToResolve(
    card: PantryCard,
    onResolve: (Disposition) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { width -> width * ARM_FRACTION })

    LaunchedEffect(state.currentValue) {
        val disposition = when (state.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> Disposition.CONSUMED
            SwipeToDismissBoxValue.EndToStart -> Disposition.DISCARDED
            SwipeToDismissBoxValue.Settled -> null
        } ?: return@LaunchedEffect

        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onResolve(disposition)
        // More boxes behind this card: the row stays and rewrites itself in place.
        if (card.entryCount > 1) state.reset()
        // Otherwise the query drops the card and animateItem() collapses the row.
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> ResolvePanel(
                    background = MaterialTheme.colorScheme.primaryContainer,
                    foreground = MaterialTheme.colorScheme.onPrimaryContainer,
                    atStart = true,
                    icon = Icons.Filled.RestaurantMenu,
                    label = "Used one",
                )
                SwipeToDismissBoxValue.EndToStart -> ResolvePanel(
                    background = MaterialTheme.colorScheme.tertiaryContainer,
                    foreground = MaterialTheme.colorScheme.onTertiaryContainer,
                    atStart = false,
                    icon = Icons.Filled.DeleteOutline,
                    label = "Binned",
                )
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        content = { content() },
    )
}

/** The word carries the meaning in greyscale, where teal and plum are two similar greys. */
@Composable
private fun ResolvePanel(
    background: Color,
    foreground: Color,
    atStart: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    Row(
        Modifier
            .fillMaxSize()
            .background(background, MaterialTheme.shapes.medium)
            .padding(horizontal = 20.dp),
        horizontalArrangement = if (atStart) {
            Arrangement.spacedBy(8.dp, Alignment.Start)
        } else {
            Arrangement.spacedBy(8.dp, Alignment.End)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = foreground)
        Text(label, style = MaterialTheme.typography.labelLarge, color = foreground)
    }
}
