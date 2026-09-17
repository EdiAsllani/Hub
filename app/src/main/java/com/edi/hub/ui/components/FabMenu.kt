package com.edi.hub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * `FloatingActionButtonMenu` is a Material 3 Expressive API and is **not** in material3 1.4.0,
 * which is the version this project can build against until `compileSdk 37` is published. The
 * menu is therefore a plain column, staggered on the way in, with the same morph the component
 * would have given.
 *
 * One live action and six reserved entries, ordered by frequency so the live one sits nearest the
 * thumb. The reserved entries use the ghosted treatment from the navigation bar — one rule, two
 * places, as `design/spec.md` §2 asks.
 */
private data class ReservedAction(val label: String, val icon: ImageVector)

private val RESERVED = listOf(
    ReservedAction("Add a secret", Icons.Outlined.Lock),
    ReservedAction("Add to the backlog", Icons.Outlined.Bookmarks),
    ReservedAction("Record income", Icons.Outlined.Savings),
    ReservedAction("Record a debt", Icons.Outlined.AccountBalanceWallet),
    ReservedAction("Add a deadline", Icons.Outlined.EventAvailable),
    ReservedAction("Record an expense", Icons.Outlined.ReceiptLong),
)

/** ~25 ms between entries, bottom to top, so the column reads as coming out of the button. */
private const val STAGGER_MILLIS = 25

@Composable
fun CaptureFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onScan: () -> Unit,
    onReserved: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RESERVED.forEachIndexed { index, action ->
            // Top of the list is furthest from the thumb, so it is also last in.
            val delay = (RESERVED.size - index) * STAGGER_MILLIS
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(400, delayMillis = delay)) +
                    scaleIn(tween(400, delayMillis = delay), initialScale = 0.9f),
                exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.9f),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    GhostedMenuEntry(
                        label = action.label,
                        icon = action.icon,
                        onTap = { onReserved(action.label) },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.9f),
            exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.9f),
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    onExpandedChange(false)
                    onScan()
                },
                icon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null) },
                text = { Text("Scan pantry item") },
            )
        }

        FloatingActionButton(onClick = { onExpandedChange(!expanded) }) {
            Icon(
                imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Add,
                contentDescription = if (expanded) "Close the menu" else "Add something",
            )
        }
    }
}
