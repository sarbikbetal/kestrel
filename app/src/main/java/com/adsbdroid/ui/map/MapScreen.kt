package com.adsbdroid.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.adsbdroid.location.UserLocation
import com.adsbdroid.settings.MapType
import com.adsbdroid.settings.UserPrefs
import com.adsbdroid.ui.TrackedPlane
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

// Match the ViewModel render cadence so smoothed positions glide instead of stepping.
private const val MAP_REFRESH_MS = 100L

@Composable
fun MapScreen(
    location: UserLocation?,
    planes: List<TrackedPlane>,
    prefs: UserPrefs,
    azimuthDeg: Float,
    onSetMapType: (MapType) -> Unit,
    onToggleAircraft: (Boolean) -> Unit,
    onToggleHeadingCone: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(10.0)
            location?.let { controller.setCenter(GeoPoint(it.lat, it.lon)) }
        }
    }
    val beam = remember(mapView) { headingMarker(mapView, GeoPoint(0.0, 0.0), 0f) }
    val userDot = remember(mapView) { userMarker(mapView, GeoPoint(0.0, 0.0)) }
    val planeLayer = remember(mapView) { PlaneMarkerLayer(mapView) }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    LaunchedEffect(prefs.mapType) {
        mapView.setTileSource(MapTiles.source(prefs.mapType))
        mapView.invalidate()
    }

    // Update marker positions in place at a smooth cadence (the user can pan/zoom,
    // so we never recenter here), reading the latest inputs each tick.
    val curLocation by rememberUpdatedState(location)
    val curAzimuth by rememberUpdatedState(azimuthDeg)
    val curPlanes by rememberUpdatedState(planes)
    val curPrefs by rememberUpdatedState(prefs)
    LaunchedEffect(Unit) {
        while (true) {
            val loc = curLocation
            if (loc != null) {
                val pt = GeoPoint(loc.lat, loc.lon)
                if (curPrefs.showHeadingCone) {
                    if (beam !in mapView.overlays) mapView.overlays.add(0, beam)
                    beam.position = pt
                    beam.rotation = -curAzimuth
                } else {
                    mapView.overlays.remove(beam)
                }
                if (userDot !in mapView.overlays) mapView.overlays.add(userDot)
                userDot.position = pt
            }
            if (curPrefs.showAircraft) {
                planeLayer.sync(curPlanes, curPrefs)
            } else {
                planeLayer.clear()
            }
            mapView.invalidate()
            delay(MAP_REFRESH_MS)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        FloatingActionButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
                .clip(CircleShape),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to sky")
        }

        FloatingActionButton(
            onClick = {
                location?.let { mapView.controller.animateTo(GeoPoint(it.lat, it.lon)) }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
                .clip(CircleShape),
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = "Recenter")
        }

        var showLayers by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            FloatingActionButton(
                onClick = { showLayers = !showLayers },
                modifier = Modifier.clip(CircleShape),
            ) {
                Icon(Icons.Filled.Layers, contentDescription = "Layers")
            }
            if (showLayers) {
                LayersPanel(
                    prefs = prefs,
                    onSetMapType = onSetMapType,
                    onToggleAircraft = onToggleAircraft,
                    onToggleHeadingCone = onToggleHeadingCone,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayersPanel(
    prefs: UserPrefs,
    onSetMapType: (MapType) -> Unit,
    onToggleAircraft: (Boolean) -> Unit,
    onToggleHeadingCone: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.width(260.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Map type", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MapType.entries.forEach { type ->
                    FilterChip(
                        selected = prefs.mapType == type,
                        onClick = { onSetMapType(type) },
                        label = { Text(type.label, fontSize = 12.sp) },
                    )
                }
            }
            Text("Layers", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            ToggleRow("Aircraft", prefs.showAircraft, onToggleAircraft)
            ToggleRow("Heading cone", prefs.showHeadingCone, onToggleHeadingCone)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
