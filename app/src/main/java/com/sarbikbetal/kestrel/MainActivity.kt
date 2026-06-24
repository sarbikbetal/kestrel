package com.sarbikbetal.kestrel

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sarbikbetal.kestrel.ui.AppNav
import com.sarbikbetal.kestrel.ui.SkyViewModel
import com.sarbikbetal.kestrel.ui.settings.SettingsViewModel
import com.sarbikbetal.kestrel.ui.theme.KestrelTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
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
            KestrelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                ) {
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

    val locationPerm = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val cameraPerm = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        locationPerm.launchPermissionRequest()
    }

    LaunchedEffect(locationPerm.status.isGranted) {
        if (locationPerm.status.isGranted) viewModel.startLocationUpdates()
    }

    AppNav(
        viewModel = viewModel,
        settingsViewModel = settingsViewModel,
        hasCameraPermission = cameraPerm.status.isGranted,
        // Called only when the user explicitly switches to camera view — not on startup.
        onRequestCameraPermission = {
            if (!cameraPerm.status.isGranted) cameraPerm.launchPermissionRequest()
        },
    )
}
