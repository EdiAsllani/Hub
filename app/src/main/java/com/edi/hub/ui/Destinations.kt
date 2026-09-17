package com.edi.hub.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable object PantryRoute

@Serializable object TodayRoute

/**
 * Bar order is fixed: Pantry at the far left, Today in the centre.
 * Deadlines, Money and Backlog are shown but not built yet, so the app is visibly
 * growing into them rather than hiding them. [route] is null while that is the case.
 */
enum class Destination(
    val label: String,
    val icon: ImageVector,
    val route: Any?,
) {
    PANTRY("Pantry", Icons.Filled.Kitchen, PantryRoute),
    DEADLINES("Deadlines", Icons.Filled.EventAvailable, null),
    TODAY("Today", Icons.Filled.Today, TodayRoute),
    MONEY("Money", Icons.Filled.AccountBalanceWallet, null),
    BACKLOG("Backlog", Icons.Filled.Bookmarks, null),
}
