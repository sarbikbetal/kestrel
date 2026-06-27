package com.sarbikbetal.kestrel.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sarbikbetal.kestrel.settings.SkyViewMode
import com.sarbikbetal.kestrel.ui.map.MapScreen
import com.sarbikbetal.kestrel.ui.map.MiniMap
import com.sarbikbetal.kestrel.ui.settings.SettingsScreen
import com.sarbikbetal.kestrel.ui.settings.SettingsViewModel
import com.sarbikbetal.kestrel.ui.sky.ArSkyScreen
import com.sarbikbetal.kestrel.ui.sky.PlaneDetailBottomSheet

object Routes {
    const val SKY = "sky"
    const val MAP = "map"
    const val SETTINGS = "settings"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav(
    viewModel: SkyViewModel,
    settingsViewModel: SettingsViewModel,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
) {
    val nav = rememberNavController()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Held as State (not read here) so the high-frequency orientation only invalidates
    // the AR canvas's draw phase, never recomposes this whole nav tree.
    val orientation = viewModel.orientation.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Always present; the plane-detail ModalBottomSheet draws above it (with
            // its scrim) when a plane is selected, so the bar stays put behind the sheet.
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                val navBackStackEntry by nav.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                NavigationBarItem(
                    icon = { Icon(Icons.Filled.CameraAlt, contentDescription = "AR Sky") },
                    label = { Text("AR Sky") },
                    selected = currentDestination?.hierarchy?.any { it.route == Routes.SKY } == true,
                    onClick = {
                        nav.navigate(Routes.SKY) {
                            popUpTo(nav.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Map, contentDescription = "Map") },
                    label = { Text("Map") },
                    selected = currentDestination?.hierarchy?.any { it.route == Routes.MAP } == true,
                    onClick = {
                        nav.navigate(Routes.MAP) {
                            popUpTo(nav.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = currentDestination?.hierarchy?.any { it.route == Routes.SETTINGS } == true,
                    onClick = {
                        nav.navigate(Routes.SETTINGS) {
                            popUpTo(nav.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = nav,
            startDestination = Routes.SKY,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.SKY) {
                val skyPrefs by settingsViewModel.prefs.collectAsStateWithLifecycle()
                ArSkyScreen(
                    state = state,
                    orientation = orientation,
                    hasCameraPermission = hasCameraPermission,
                    skyViewMode = skyPrefs.skyViewMode,
                    onToggleSkyView = {
                        val next = if (skyPrefs.skyViewMode == SkyViewMode.CAMERA) SkyViewMode.SYNTHETIC else SkyViewMode.CAMERA
                        settingsViewModel.setSkyViewMode(next)
                        if (next == SkyViewMode.CAMERA) onRequestCameraPermission()
                    },
                    onSelectPlane = viewModel::selectPlane,
                    miniMap = {
                        MiniMap(
                            location = state.location,
                            azimuthDeg = state.orientation.compassBearingDeg,
                            prefs = state.prefs,
                            planes = state.planes,
                            onClick = {
                                nav.navigate(Routes.MAP) {
                                    popUpTo(nav.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    },
                )
            }
            composable(Routes.MAP) {
                MapScreen(
                    location = state.location,
                    planes = state.planes,
                    prefs = state.prefs,
                    azimuthDeg = state.orientation.compassBearingDeg,
                    onSelectPlane = viewModel::selectPlane,
                    onSetMapType = settingsViewModel::setMapType,
                    onToggleAircraft = settingsViewModel::setShowAircraft,
                    onToggleHeadingCone = settingsViewModel::setShowHeadingCone,
                    selectedHex = state.selectedHex,
                    details = state.details,
                    detailsLoading = state.detailsLoading,
                    history = state.history,
                    historyLoading = state.historyLoading,
                    historyError = state.historyError,
                )
            }
            composable(Routes.SETTINGS) {
                val prefs by settingsViewModel.prefs.collectAsStateWithLifecycle()
                SettingsScreen(
                    prefs = prefs,
                    compassAccuracy = state.orientation.compassAccuracy,
                    onSpeedUnit = settingsViewModel::setSpeedUnit,
                    onAltitudeUnit = settingsViewModel::setAltitudeUnit,
                    onTimeFormat = settingsViewModel::setTimeFormat,
                    onPositionSource = settingsViewModel::setPositionSource,
                    onRouteSource = settingsViewModel::setRouteSource,
                    onAppTheme = settingsViewModel::setAppTheme,
                )
            }
        }

        // AR Sky only: full modal bottom sheet. Map uses its own push-up drawer.
        val navBackStackEntry by nav.currentBackStackEntryAsState()
        if (navBackStackEntry?.destination?.route == Routes.SKY) {
            state.selectedHex?.let { hex ->
                val selected = state.planes.firstOrNull { it.aircraft.hex == hex }
                PlaneDetailBottomSheet(
                    selected = selected,
                    fallbackTitle = hex,
                    details = state.details,
                    detailsLoading = state.detailsLoading,
                    samples = state.history,
                    prefs = state.prefs,
                    loading = state.historyLoading,
                    error = state.historyError,
                    onDismiss = { viewModel.selectPlane(null) },
                )
            }
        }
    }
}
