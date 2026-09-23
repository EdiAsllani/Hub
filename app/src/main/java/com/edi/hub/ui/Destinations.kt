package com.edi.hub.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable object PantryRoute

@Serializable object TodayRoute

@Serializable object DeadlinesRoute

@Serializable data class DeadlineDetailRoute(val id: Long)

@Serializable data class DeadlineEditorRoute(val id: Long = 0, val kind: String? = null)

@Serializable object SettingsRoute

/**
 * Capture is a nested graph so the three steps share one ViewModel and so system back reverses one
 * step at a time. Full screen: no navigation bar, no top bar.
 */
@Serializable object CaptureGraph

@Serializable object CaptureIdentifyRoute

@Serializable object CaptureNameRoute

@Serializable object CaptureDateRoute

/**
 * One card's entries. The location travels as a string because the group key alone is ambiguous —
 * the same product on two shelves is two cards.
 */
@Serializable data class ItemDetailRoute(val location: String, val groupKey: String)

/**
 * Bar order is fixed: Pantry at the far left, Today in the centre at the thumb's home position.
 * Deadlines, Money and Backlog are shown but not built yet, so the app is visibly growing into
 * them rather than hiding them. [route] is null while that is the case.
 */
enum class Destination(
    val label: String,
    val icon: ImageVector,
    val outlinedIcon: ImageVector,
    val route: Any?,
) {
    PANTRY("Pantry", Icons.Filled.Kitchen, Icons.Outlined.Kitchen, PantryRoute),
    DEADLINES("Deadlines", Icons.Filled.EventAvailable, Icons.Outlined.EventAvailable, DeadlinesRoute),
    TODAY("Today", Icons.Filled.Today, Icons.Outlined.Today, TodayRoute),
    MONEY("Money", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, null),
    BACKLOG("Backlog", Icons.Filled.Bookmarks, Icons.Outlined.Bookmarks, null),
}
