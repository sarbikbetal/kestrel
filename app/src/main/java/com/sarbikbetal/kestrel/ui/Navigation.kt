package com.sarbikbetal.kestrel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sarbikbetal.kestrel.ui.map.MapScreen
import com.sarbikbetal.kestrel.ui.map.MiniMap
import com.sarbikbetal.kestrel.ui.settings.SettingsScreen
import com.sarbikbetal.kestrel.ui.settings.SettingsViewModel
import com.sarbikbetal.kestrel.ui.sky.ArSkyScreen

object Routes {
    const val SKY = "sky"
    const val MAP = "map"
    const val SETTINGS = "settings"
}

@Composable
fun AppNav(
    viewModel: SkyViewModel,
    settingsViewModel: SettingsViewModel,
    hasCameraPermission: Boolean,
) {
    val nav = rememberNavController()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    NavHost(navController = nav, startDestination = Routes.SKY) {
        composable(Routes.SKY) {
            ArSkyScreen(
                state = state,
                hasCameraPermission = hasCameraPermission,
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onSelectPlane = viewModel::selectPlane,
                miniMap = {
                    MiniMap(
                        location = state.location,
                        azimuthDeg = state.orientation.azimuthDeg,
                        prefs = state.prefs,
                        planes = state.planes,
                        onClick = { nav.navigate(Routes.MAP) },
                    )
                },
            )
        }
        composable(Routes.MAP) {
            MapScreen(
                location = state.location,
                planes = state.planes,
                prefs = state.prefs,
                azimuthDeg = state.orientation.azimuthDeg,
                selectedHex = state.selectedHex,
                history = state.history,
                details = state.details,
                detailsLoading = state.detailsLoading,
                historyLoading = state.historyLoading,
                historyError = state.historyError,
                onSelectPlane = viewModel::selectPlane,
                onSetMapType = settingsViewModel::setMapType,
                onToggleAircraft = settingsViewModel::setShowAircraft,
                onToggleHeadingCone = settingsViewModel::setShowHeadingCone,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            val prefs by settingsViewModel.prefs.collectAsStateWithLifecycle()
            SettingsScreen(
                prefs = prefs,
                onSpeedUnit = settingsViewModel::setSpeedUnit,
                onAltitudeUnit = settingsViewModel::setAltitudeUnit,
                onTimeFormat = settingsViewModel::setTimeFormat,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
