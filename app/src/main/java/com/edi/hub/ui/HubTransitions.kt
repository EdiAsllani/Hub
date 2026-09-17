package com.edi.hub.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf

/**
 * Threading an `AnimatedVisibilityScope` from every `NavHost` destination down to the one composable
 * that needs it is the awkward part of shared elements with nav-compose, and `docs/plan.md` §3 says
 * so. Both scopes travel as a composition local instead: the endpoints of a shared element are
 * always inside the same `SharedTransitionLayout`, so there is nothing for a parameter to make
 * clearer that this does not.
 *
 * Null wherever no transition is running, which is what previews get.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
class HubTransitions(
    val shared: SharedTransitionScope,
    val visibility: AnimatedVisibilityScope,
)

val LocalHubTransitions = compositionLocalOf<HubTransitions?> { null }

/** The two elements that travel between the row and the detail screen. */
fun nameKey(groupKey: String) = "name-$groupKey"

fun chipKey(groupKey: String) = "chip-$groupKey"
