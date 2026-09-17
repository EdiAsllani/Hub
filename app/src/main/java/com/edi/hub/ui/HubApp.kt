package com.edi.hub.ui

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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.edi.hub.ui.components.GhostedNavItem
import com.edi.hub.ui.pantry.PantryScreen
import com.edi.hub.ui.settings.SettingsScreen
import com.edi.hub.ui.today.TodayScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The dashed indicator holds for 200 ms — long enough to read as a response, short enough not to promise one. */
private const val ARMED_MILLIS = 200L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var armed by remember { mutableStateOf<Destination?>(null) }

    val current = backStackEntry?.destination
    val onSettings = current?.hasRoute(SettingsRoute::class) == true

    LaunchedEffect(armed) {
        if (armed != null) {
            delay(ARMED_MILLIS)
            armed = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (onSettings) {
                            "Settings"
                        } else {
                            Destination.entries.firstOrNull {
                                it.route != null && current?.hasRoute(it.route::class) == true
                            }?.label.orEmpty()
                        },
                    )
                },
                navigationIcon = {
                    if (onSettings) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (!onSettings) {
                        IconButton(onClick = { navController.navigate(SettingsRoute) }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!onSettings) {
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
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable<TodayRoute> { TodayScreen() }
            composable<PantryRoute> { PantryScreen() }
            composable<SettingsRoute> { SettingsScreen(snackbarHostState) }
        }
    }
}

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
