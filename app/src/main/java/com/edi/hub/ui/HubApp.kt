package com.edi.hub.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.edi.hub.ui.pantry.PantryScreen
import com.edi.hub.ui.today.TodayScreen
import kotlinx.coroutines.launch

@Composable
fun HubApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    val route = destination.route
                    val selected = route != null &&
                        backStackEntry?.destination?.hasRoute(route::class) == true

                    // NavigationBar has no native disabled state, so an unbuilt destination is
                    // dimmed, marked disabled for accessibility services, and answers with a
                    // snackbar instead of navigating.
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (route == null) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("${destination.label} is coming soon")
                                }
                            } else if (!selected) {
                                navController.navigate(route) {
                                    popUpTo(TodayRoute) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                        modifier = if (route == null) Modifier.semantics { disabled() } else Modifier,
                        colors = if (route == null) {
                            NavigationBarItemDefaults.colors(
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            )
                        } else {
                            NavigationBarItemDefaults.colors()
                        },
                    )
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
        }
    }
}
