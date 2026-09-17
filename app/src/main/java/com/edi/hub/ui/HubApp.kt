package com.edi.hub.ui

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.edi.hub.ui.capture.CaptureDateScreen
import com.edi.hub.ui.capture.CaptureIdentifyScreen
import com.edi.hub.ui.capture.CaptureNameScreen
import com.edi.hub.ui.capture.CaptureViewModel
import com.edi.hub.ui.capture.RecentlyAddedViewModel
import com.edi.hub.ui.components.CaptureFabMenu
import com.edi.hub.ui.components.GhostedNavItem
import com.edi.hub.ui.detail.ItemDetailScreen
import com.edi.hub.ui.pantry.PantryScreen
import com.edi.hub.ui.settings.SettingsScreen
import com.edi.hub.ui.today.TodayScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The dashed indicator holds for 200 ms — long enough to read as a response, short enough not to promise one. */
private const val ARMED_MILLIS = 200L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HubApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var armed by remember { mutableStateOf<Destination?>(null) }
    var fabExpanded by remember { mutableStateOf(false) }
    val recentlyAdded: RecentlyAddedViewModel = hiltViewModel()

    val current = backStackEntry?.destination
    val onSettings = current?.hasRoute(SettingsRoute::class) == true
    val onDetail = current?.hasRoute(ItemDetailRoute::class) == true
    // Capture is full screen: the bars would only offer ways out of a sequence that has a back button.
    val capturing = current?.hierarchy?.any { it.hasRoute(CaptureGraph::class) } == true
    val chromeless = onSettings || capturing || onDetail

    LaunchedEffect(armed) {
        if (armed != null) {
            delay(ARMED_MILLIS)
            armed = null
        }
    }
    LaunchedEffect(capturing) { if (capturing) fabExpanded = false }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!capturing) {
                TopAppBar(
                    title = { Text(if (onSettings) "Settings" else if (onDetail) "" else current.destinationLabel()) },
                    navigationIcon = {
                        if (onSettings || onDetail) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (!onSettings && !onDetail) {
                            IconButton(onClick = { navController.navigate(SettingsRoute) }) {
                                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!chromeless) {
                CaptureFabMenu(
                    expanded = fabExpanded,
                    onExpandedChange = { fabExpanded = it },
                    onScan = { navController.navigate(CaptureGraph) },
                    onReserved = { label ->
                        scope.launch { snackbarHostState.showSnackbar("$label is not built yet") }
                    },
                )
            }
        },
        bottomBar = {
            if (!chromeless) {
                NavigationBar {
                    Destination.entries.forEach { destination ->
                        val route = destination.route
                        if (route == null) {
                            GhostedSlot(destination, armed == destination) {
                                armed = destination
                                scope.launch {
                                    snackbarHostState.showSnackbar("${destination.label} is not built yet")
                                }
                            }
                        } else {
                            val selected = current?.hasRoute(route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(route) {
                                            popUpTo(TodayRoute) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        if (selected) destination.icon else destination.outlinedIcon,
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable<TodayRoute> { TodayScreen() }
            composable<PantryRoute> {
                WithTransitions(this) {
                    PantryScreen(
                        snackbarHostState = snackbarHostState,
                        onOpen = { card ->
                            navController.navigate(ItemDetailRoute(card.location.name, card.groupKey))
                        },
                    )
                }
            }
            // 500 ms emphasized on the name and the chip; predictive back scrubs the same transition.
            composable<ItemDetailRoute> {
                WithTransitions(this) { ItemDetailScreen(onGone = { navController.popBackStack() }) }
            }
            composable<SettingsRoute> { SettingsScreen(snackbarHostState) }

            // Each step is its own destination on the shared x axis, so system back reverses one
            // step and predictive back shows the step it is about to reverse to.
            navigation<CaptureGraph>(
                startDestination = CaptureIdentifyRoute,
                enterTransition = { slideIntoContainer(SlideDirection.Start, tween(300)) + fadeIn(tween(300)) },
                exitTransition = { slideOutOfContainer(SlideDirection.Start, tween(300)) + fadeOut(tween(300)) },
                popEnterTransition = { slideIntoContainer(SlideDirection.End, tween(300)) + fadeIn(tween(300)) },
                popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(300)) + fadeOut(tween(300)) },
            ) {
                composable<CaptureIdentifyRoute> { entry ->
                    CaptureIdentifyScreen(
                        viewModel = entry.captureViewModel(navController),
                        // Step 1 advances on its own on both of these paths, so it was never a
                        // decision the user could go back to. Leaving it on the stack would put
                        // back into a loop: return to step 1, the effect fires again, forward again.
                        onNameIt = {
                            navController.navigate(CaptureNameRoute) {
                                popUpTo(CaptureIdentifyRoute) { inclusive = true }
                            }
                        },
                        onAbandon = { navController.popBackStack(CaptureGraph, inclusive = true) },
                        onFinish = { saved ->
                            recentlyAdded.record(saved)
                            navController.popBackStack(CaptureGraph, inclusive = true)
                        },
                    )
                }
                composable<CaptureNameRoute> { entry ->
                    CaptureNameScreen(
                        viewModel = entry.captureViewModel(navController),
                        onNext = { navController.navigate(CaptureDateRoute) },
                    )
                }
                composable<CaptureDateRoute> { entry ->
                    CaptureDateScreen(
                        viewModel = entry.captureViewModel(navController),
                        onSaved = { saved ->
                            recentlyAdded.record(saved)
                            navController.popBackStack(CaptureGraph, inclusive = true)
                        },
                    )
                }
            }
        }
        }
    }

    // The saved item's undo outlives the sequence it was created in, so it is raised from here.
    recentlyAdded.saved?.let { saved ->
        LaunchedEffect(saved.id) {
            val result = snackbarHostState.showSnackbar(
                message = if (saved.restocked) "${saved.name}, one more" else "${saved.name} added",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) recentlyAdded.undo(saved) else recentlyAdded.clear()
        }
    }
}

/**
 * Publishes both halves of the shared-element scope to everything below, so the pantry row and the
 * detail screen can name the same element without either knowing about the NavHost.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.WithTransitions(
    visible: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalHubTransitions provides remember(this, visible) { HubTransitions(this, visible) },
        content = content,
    )
}

/** One ViewModel for the whole sequence, scoped to the graph rather than to any one step. */
@Composable
private fun NavBackStackEntry.captureViewModel(navController: NavController): CaptureViewModel {
    val parent = remember(this) { navController.getBackStackEntry(CaptureGraph) }
    return hiltViewModel(parent)
}

private fun androidx.navigation.NavDestination?.destinationLabel(): String =
    Destination.entries.firstOrNull { it.route != null && this?.hasRoute(it.route::class) == true }
        ?.label
        .orEmpty()

/** Takes the same width as a live slot, so the bar does not redistribute as tabs go live. */
@Composable
private fun RowScope.GhostedSlot(destination: Destination, armed: Boolean, onTap: () -> Unit) {
    GhostedNavItem(
        label = destination.label,
        icon = destination.outlinedIcon,
        onTap = onTap,
        armed = armed,
        modifier = Modifier.weight(1f),
    )
}
