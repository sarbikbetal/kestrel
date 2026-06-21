package com.adsbdroid.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.adsbdroid.data.FlightDetails
import com.adsbdroid.data.TrackSample
import com.adsbdroid.location.UserLocation
import com.adsbdroid.settings.MapType
import com.adsbdroid.settings.UserPrefs
import com.adsbdroid.ui.TrackedPlane
import com.adsbdroid.ui.sky.PlaneGraphsPanel
import com.adsbdroid.ui.theme.HudAmber
import com.adsbdroid.ui.theme.HudBorder
import com.adsbdroid.ui.theme.HudCyan
import com.adsbdroid.ui.theme.HudSurface
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

private const val MAP_REFRESH_MS = 100L

@Composable
fun MapScreen(
    location: UserLocation?,
    planes: List<TrackedPlane>,
    prefs: UserPrefs,
    azimuthDeg: Float,
    selectedHex: String?,
    history: List<TrackSample>,
    details: FlightDetails?,
    detailsLoading: Boolean,
    historyLoading: Boolean,
    historyError: String?,
    onSelectPlane: (String?) -> Unit,
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

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // Back FAB
        HudFab(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = HudCyan)
        }

        // Layers FAB + panel (hidden while a plane is selected)
        if (selectedHex == null) {
            HudFab(
                onClick = {
                    location?.let { mapView.controller.animateTo(GeoPoint(it.lat, it.lon)) }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Recenter", tint = HudCyan)
            }

            var showLayers by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End,
            ) {
                HudFab(onClick = { showLayers = !showLayers }) {
                    Icon(Icons.Filled.Layers, contentDescription = "Layers", tint = if (showLayers) HudAmber else HudCyan)
                }
                if (showLayers) {
                    LayersPanel(
                        prefs = prefs,
                        onSetMapType = onSetMapType,
                        onToggleAircraft = onToggleAircraft,
                        onToggleHeadingCone = onToggleHeadingCone,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        // Details panel when a plane is selected
        if (selectedHex != null) {
            val selected = planes.firstOrNull { it.aircraft.hex == selectedHex }

            PlaneGraphsPanel(
                selected = selected,
                fallbackTitle = selectedHex,
                details = details,
                detailsLoading = detailsLoading,
                samples = history,
                prefs = prefs,
                loading = historyLoading,
                error = historyError,
                onClose = { onSelectPlane(null) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp),
            )
        }
    }
}

/** A FAB styled as a translucent HUD panel with a cyan accent border. */
@Composable
private fun HudFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = HudSurface,
        contentColor = HudCyan,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, HudBorder, RoundedCornerShape(12.dp)),
    ) {
        content()
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
    Box(
        modifier = modifier
            .width(240.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, HudBorder, RoundedCornerShape(12.dp))
            .background(HudSurface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "MAP TYPE",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = HudCyan,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MapType.entries.forEach { type ->
                    FilterChip(
                        selected = prefs.mapType == type,
                        onClick = { onSetMapType(type) },
                        label = {
                            Text(
                                type.label.uppercase(),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = HudCyan.copy(alpha = 0.18f),
                            selectedLabelColor = HudCyan,
                            labelColor = Color.White.copy(alpha = 0.7f),
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = prefs.mapType == type,
                            borderColor = HudBorder,
                            selectedBorderColor = HudCyan.copy(alpha = 0.5f),
                        ),
                    )
                }
            }
            Text(
                "LAYERS",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = HudCyan,
            )
            ToggleRow("AIRCRAFT", prefs.showAircraft, onToggleAircraft)
            ToggleRow("HEADING CONE", prefs.showHeadingCone, onToggleHeadingCone)
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
        Text(
            label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.White.copy(alpha = 0.8f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = HudCyan,
                checkedTrackColor = HudCyan.copy(alpha = 0.3f),
                uncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
            ),
        )
    }
}
