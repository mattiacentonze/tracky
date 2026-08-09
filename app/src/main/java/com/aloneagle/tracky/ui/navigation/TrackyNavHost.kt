package com.aloneagle.tracky.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aloneagle.tracky.ui.feature.debug.DiagnosticsScreen
import com.aloneagle.tracky.ui.feature.debug.DiagnosticsViewModel
import com.aloneagle.tracky.ui.feature.automation.AutomationsScreen
import com.aloneagle.tracky.ui.feature.automation.AutomationsViewModel
import com.aloneagle.tracky.ui.feature.detail.TrackerDetailScreen
import com.aloneagle.tracky.ui.feature.detail.TrackerDetailViewModel
import com.aloneagle.tracky.ui.feature.scan.ScanScreen
import com.aloneagle.tracky.ui.feature.scan.ScanViewModel
import com.aloneagle.tracky.ui.feature.search.SearchScreen
import com.aloneagle.tracky.ui.feature.search.SearchViewModel
import com.aloneagle.tracky.ui.feature.settings.SettingsScreen
import com.aloneagle.tracky.ui.feature.settings.SettingsViewModel
import com.aloneagle.tracky.ui.feature.trackers.TrackersScreen
import com.aloneagle.tracky.ui.feature.trackers.TrackersViewModel

@Composable
fun TrackyNavHost() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TrackyDestination.bottomBarItems.any { destination ->
        currentDestination?.hierarchy?.any { it.route?.startsWith(destination.route.substringBefore('?')) == true } == true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TrackyDestination.bottomBarItems.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route?.startsWith(destination.route.substringBefore('?')) == true
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route.substringBefore('?')) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                destination.icon?.let { icon ->
                                    Icon(icon, contentDescription = destination.title)
                                }
                            },
                            label = { Text(destination.title) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TrackyDestination.Scan.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TrackyDestination.Trackers.route) {
                val viewModel = hiltViewModel<TrackersViewModel>()
                TrackersScreen(
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState,
                    onOpenScan = { navController.navigate(TrackyDestination.Scan.route) },
                    onOpenDetail = { trackerId -> navController.navigate(TrackyDestination.detailRoute(trackerId)) },
                    onOpenDiagnostics = { navController.navigate(TrackyDestination.Diagnostics.route) },
                    onOpenSettings = { navController.navigate(TrackyDestination.Settings.route) },
                )
            }
            composable(TrackyDestination.Scan.route) {
                val viewModel = hiltViewModel<ScanViewModel>()
                ScanScreen(
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState,
                    onOpenTracker = { trackerId ->
                        navController.navigate(TrackyDestination.detailRoute(trackerId)) {
                            launchSingleTop = true
                        }
                    },
                    onFindTracker = { trackerId -> navController.navigate(TrackyDestination.searchRoute(trackerId)) },
                )
            }
            composable(TrackyDestination.Automations.route) {
                val viewModel = hiltViewModel<AutomationsViewModel>()
                AutomationsScreen(viewModel)
            }
            composable(
                route = TrackyDestination.Detail.route,
                arguments = listOf(navArgument("trackerId") {}),
            ) {
                val viewModel = hiltViewModel<TrackerDetailViewModel>()
                TrackerDetailScreen(
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState,
                    onBack = { navController.popBackStack() },
                    onSearch = { trackerId -> navController.navigate(TrackyDestination.searchRoute(trackerId)) },
                    onOpenDiagnostics = { trackerId ->
                        navController.navigate(TrackyDestination.diagnosticsRoute(trackerId))
                    },
                )
            }
            composable(
                route = TrackyDestination.Search.route,
                arguments = listOf(navArgument("trackerId") {}),
            ) {
                val viewModel = hiltViewModel<SearchViewModel>()
                SearchScreen(
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = TrackyDestination.Diagnostics.route,
                arguments = listOf(
                    navArgument("trackerId") {
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                val viewModel = hiltViewModel<DiagnosticsViewModel>()
                DiagnosticsScreen(
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(TrackyDestination.Settings.route) {
                val viewModel = hiltViewModel<SettingsViewModel>()
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
