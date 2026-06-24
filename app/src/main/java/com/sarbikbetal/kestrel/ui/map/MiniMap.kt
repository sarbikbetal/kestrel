package com.sarbikbetal.kestrel.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sarbikbetal.kestrel.location.UserLocation
import com.sarbikbetal.kestrel.settings.UserPrefs
import com.sarbikbetal.kestrel.ui.TrackedPlane
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

private const val MINIMAP_PLANE_CAP = 40
private const val MINIMAP_REFRESH_MS = 100L

/** Small, non-interactive map in the corner. Tapping it opens the full map. */
@Composable
fun MiniMap(
    location: UserLocation?,
    azimuthDeg: Float,
    prefs: UserPrefs,
    planes: List<TrackedPlane>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false)
            controller.setZoom(8.0)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
        }
    }
    val beam = remember(mapView) { headingMarker(mapView, GeoPoint(0.0, 0.0), 0f) }
    val userDot = remember(mapView) { userMarker(mapView, GeoPoint(0.0, 0.0)) }
    val planeLayer = remember(mapView) { PlaneMarkerLayer(mapView, iconScale = 0.75f) }

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
    LaunchedEffect(Unit) {
        while (true) {
            val loc = curLocation
            if (loc != null) {
                val pt = GeoPoint(loc.lat, loc.lon)
                mapView.controller.setCenter(pt)

                if (curPrefs.showHeadingCone) {
                    if (beam !in mapView.overlays) mapView.overlays.add(0, beam)
                    beam.position = pt
                    beam.rotation = -curAzimuth
                } else {
                    mapView.overlays.remove(beam)
                }

                if (userDot !in mapView.overlays) mapView.overlays.add(userDot)
                userDot.position = pt

                if (curPrefs.showAircraft) {
                    planeLayer.sync(curPlanes, curPrefs, cap = MINIMAP_PLANE_CAP)
                } else {
                    planeLayer.clear()
                }
                mapView.invalidate()
            }
            delay(MINIMAP_REFRESH_MS)
        }
    }

    Card(
        modifier = modifier.size(128.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
            Box(modifier = Modifier.fillMaxSize().clickable(onClick = onClick))
            Icon(
                imageVector = Icons.Filled.OpenInFull,
                contentDescription = "Open map",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(20.dp),
            )
        }
    }
}
