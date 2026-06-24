package com.sarbikbetal.kestrel.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sarbikbetal.kestrel.location.UserLocation
import com.sarbikbetal.kestrel.settings.AppTheme
import com.sarbikbetal.kestrel.settings.MapType
import com.sarbikbetal.kestrel.settings.UserPrefs
import com.sarbikbetal.kestrel.ui.TrackedPlane
import com.sarbikbetal.kestrel.ui.theme.ASTRO_RED_MAP_FILTER
import com.sarbikbetal.kestrel.ui.theme.LocalAppTheme
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay

private const val MAP_REFRESH_MS = 100L

@Composable
fun MapScreen(
    location: UserLocation?,
    planes: List<TrackedPlane>,
    prefs: UserPrefs,
    azimuthDeg: Float,
    onSelectPlane: (String?) -> Unit,
    onSetMapType: (MapType) -> Unit,
    onToggleAircraft: (Boolean) -> Unit,
    onToggleHeadingCone: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appTheme = LocalAppTheme.current
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

    LaunchedEffect(appTheme) {
        val filter = if (appTheme == AppTheme.ASTRO_RED) ASTRO_RED_MAP_FILTER else null
        mapView.overlayManager.tilesOverlay.setColorFilter(filter)
        mapView.invalidate()
    }

    val curLocation by rememberUpdatedState(location)
    val curAzimuth by rememberUpdatedState(azimuthDeg)
    val curPlanes by rememberUpdatedState(planes)
    val curPrefs by rememberUpdatedState(prefs)
    val curOnSelect by rememberUpdatedState(onSelectPlane)
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
                planeLayer.sync(curPlanes, curPrefs, onSelect = curOnSelect)
            } else {
                planeLayer.clear()
            }
            mapView.invalidate()
            delay(MAP_REFRESH_MS)
        }
    }

    var showLayersSheet by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            FloatingActionButton(
                onClick = { showLayersSheet = true },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = FloatingActionButtonDefaults.shape,
            ) {
                Icon(Icons.Filled.Layers, contentDescription = "Map layers")
            }
        }

        FloatingActionButton(
            onClick = {
                location?.let { mapView.controller.animateTo(GeoPoint(it.lat, it.lon)) }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = FloatingActionButtonDefaults.shape,
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = "Recenter")
        }
    }

    if (showLayersSheet) {
        MapLayersBottomSheet(
            prefs = prefs,
            onSetMapType = onSetMapType,
            onToggleAircraft = onToggleAircraft,
            onToggleHeadingCone = onToggleHeadingCone,
            onDismiss = { showLayersSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MapLayersBottomSheet(
    prefs: UserPrefs,
    onSetMapType: (MapType) -> Unit,
    onToggleAircraft: (Boolean) -> Unit,
    onToggleHeadingCone: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLargeIncreased,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "Map layers",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Map type",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MapType.entries.forEach { type ->
                        FilterChip(
                            selected = prefs.mapType == type,
                            onClick = { onSetMapType(type) },
                            label = { Text(type.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }

            Column {
                Text(
                    "Overlays",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                ToggleRow("Aircraft", prefs.showAircraft, onToggleAircraft)
                ToggleRow("Heading cone", prefs.showHeadingCone, onToggleHeadingCone)
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    )
}
