package com.edi.hub.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Marks a composable as one end of a shared element, and does nothing at all where no transition is
 * running. Keeping the no-op in one place is what lets the pantry row and the detail screen stay
 * previewable without a NavHost around them.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElement(key: String): Modifier {
    val transitions = LocalHubTransitions.current ?: return this
    with(transitions.shared) {
        return this@sharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = transitions.visibility,
        )
    }
}
