package com.adsbdroid

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adsbdroid.ui.AppNav
import com.adsbdroid.ui.SkyViewModel
import com.adsbdroid.ui.settings.SettingsViewModel
import com.adsbdroid.ui.theme.AdsbDroidTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // osmdroid needs a user agent and a cache location before any MapView loads.
        val prefs = getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        Configuration.getInstance().load(applicationContext, prefs)
        Configuration.getInstance().userAgentValue = packageName

        enableEdgeToEdge()
        setContent {
            AdsbDroidTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PlaneSpotterApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PlaneSpotterApp() {
    val viewModel: SkyViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    val permissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
    )

    LaunchedEffect(Unit) {
        permissions.launchMultiplePermissionRequest()
    }

    val cameraGranted = permissions.permissions
        .firstOrNull { it.permission == Manifest.permission.CAMERA }
        ?.status?.isGranted == true
    val locationGranted = permissions.permissions
        .firstOrNull { it.permission == Manifest.permission.ACCESS_FINE_LOCATION }
        ?.status?.isGranted == true

    LaunchedEffect(locationGranted) {
        if (locationGranted) viewModel.startLocationUpdates()
    }

    AppNav(
        viewModel = viewModel,
        settingsViewModel = settingsViewModel,
        hasCameraPermission = cameraGranted,
    )
}
