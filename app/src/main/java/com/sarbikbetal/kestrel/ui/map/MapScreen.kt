package com.sarbikbetal.kestrel.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.BottomSheetDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.viewinterop.AndroidView
import com.sarbikbetal.kestrel.data.FlightDetails
import com.sarbikbetal.kestrel.data.TrackSample
import com.sarbikbetal.kestrel.location.UserLocation
import com.sarbikbetal.kestrel.settings.AppTheme
import com.sarbikbetal.kestrel.settings.MapType
import com.sarbikbetal.kestrel.settings.UserPrefs
import com.sarbikbetal.kestrel.ui.TrackedPlane
import com.sarbikbetal.kestrel.ui.sky.PlaneDetailContent
import com.sarbikbetal.kestrel.ui.theme.ASTRO_RED_MAP_FILTER
import com.sarbikbetal.kestrel.ui.theme.LocalAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.advancedpolyline.ColorMapping
import org.osmdroid.views.overlay.advancedpolyline.PolychromaticPaintList

private const val MAP_REFRESH_MS = 100L
private val PEEK_HEIGHT = 268.dp
private val EXPANDED_FRACTION = 0.68f

// adsb.lol traces are a rolling ~24h history of the airframe covering several
// flights. A long gap alone isn't a leg boundary: real traces show oceanic
// coverage holes of hours between two cruise-altitude fixes. A gap only marks a
// new flight when the aircraft was at/near the ground around it (parked between
// flights) — verified against live traces, where every turnaround has a ground
// fix beside the gap while coverage holes sit at cruise altitude on both sides.
private const val LEG_GAP_MS = 30 * 60 * 1000L
private const val GROUND_ALT_M = 500.0

// Altitude→colour ramp (tar1090/adsb.lol style): low = orange, high = violet.
private const val MAX_ALT_M = 12_000.0 // ~39,000 ft, top of the ramp
private const val HUE_LOW = 30f // orange
private const val HUE_HIGH = 280f // violet

/** Positioned fixes of the most recent contiguous flight leg from a full trace. */
private fun currentLeg(samples: List<TrackSample>): List<TrackSample> {
    val leg = ArrayList<TrackSample>()
    var prev: TrackSample? = null
    for (s in samples) {
        val p = prev
        if (p != null && s.timeMs - p.timeMs > LEG_GAP_MS && touchedGround(p, s)) leg.clear()
        prev = s
        if (s.lat != null && s.lon != null) leg.add(s)
    }
    return leg
}

/** True if either fix bracketing a gap is at/near the ground (so the plane landed). */
private fun touchedGround(a: TrackSample, b: TrackSample): Boolean {
    val aa = a.altitudeMeters
    val bb = b.altitudeMeters
    return (aa != null && aa <= GROUND_ALT_M) || (bb != null && bb <= GROUND_ALT_M)
}

/** Orange (low) → violet (high) for an altitude in metres. */
private fun altitudeColor(altMeters: Double?): Int {
    val t = ((altMeters ?: 0.0) / MAX_ALT_M).coerceIn(0.0, 1.0).toFloat()
    val hue = HUE_LOW + t * (HUE_HIGH - HUE_LOW)
    return android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
}

@OptIn(ExperimentalMaterial3Api::class)
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
    selectedHex: String? = null,
    details: FlightDetails? = null,
    detailsLoading: Boolean = false,
    history: List<TrackSample> = emptyList(),
    historyLoading: Boolean = false,
    historyError: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appTheme = LocalAppTheme.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(10.0)
            location?.let { controller.setCenter(GeoPoint(it.lat, it.lon)) }
        }
    }
    val beam = remember(mapView) { headingMarker(mapView, GeoPoint(0.0, 0.0), 0f) }
    val userDot = remember(mapView) { userMarker(mapView, GeoPoint(0.0, 0.0)) }
    val planeLayer = remember(mapView) { PlaneMarkerLayer(mapView) }

    val accent = MaterialTheme.colorScheme.primary.toArgb()
    // Cached current-leg trace points + per-point altitude colours; the live plane
    // position/colour is appended each frame so the path tail stays glued to the
    // (extrapolated) marker.
    val legPoints = remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    val legColors = remember { mutableStateOf(IntArray(0)) }
    val liveColor = remember { mutableStateOf(accent) }
    val pathLine = remember(mapView) {
        Polyline(mapView).apply {
            val gradientPaint = android.graphics.Paint().apply {
                strokeWidth = 6f
                style = android.graphics.Paint.Style.STROKE
                strokeCap = android.graphics.Paint.Cap.ROUND
                isAntiAlias = true
            }
            val mapping = ColorMapping { i ->
                val cols = legColors.value
                if (i < cols.size) cols[i] else liveColor.value
            }
            // Must configure via getOutlinePaintLists() (and never touch outlinePaint
            // after): osmdroid flips an internal flag in whichever getter is called last.
            outlinePaintLists.add(PolychromaticPaintList(gradientPaint, mapping, true))
            setOnClickListener { _, _, _ -> false }
        }
    }
    val highlight = remember(mapView, accent) { selectionMarker(mapView, accent) }

    // Empty-map tap deselects. Plane markers consume their own taps (return true),
    // so only taps on empty space reach this receiver. Pan/zoom never fire singleTapConfirmed.
    val curOnSelect by rememberUpdatedState(onSelectPlane)
    val mapEventsOverlay = remember(mapView) {
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                curOnSelect(null)
                return true
            }
            override fun longPressHelper(p: GeoPoint?) = false
        })
    }

    DisposableEffect(Unit) {
        // Add events overlay first so plane markers (added later) process taps first.
        mapView.overlays.add(0, mapEventsOverlay)
        mapView.onResume()
        onDispose {
            mapView.overlays.remove(mapEventsOverlay)
            mapView.onPause()
        }
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
    val curSelectedHex by rememberUpdatedState(selectedHex)
    LaunchedEffect(Unit) {
        while (true) {
            val loc = curLocation
            if (loc != null) {
                val pt = GeoPoint(loc.lat, loc.lon)
                if (curPrefs.showHeadingCone) {
                    if (beam !in mapView.overlays) mapView.overlays.add(beam)
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
            // Selection halo follows the selected plane's live position.
            val sel = curSelectedHex
            val selPlane = if (sel != null) curPlanes.firstOrNull { it.aircraft.hex == sel } else null
            if (selPlane != null && curPrefs.showAircraft) {
                if (highlight !in mapView.overlays) mapView.overlays.add(highlight)
                val livePos = GeoPoint(selPlane.lat, selPlane.lon)
                highlight.position = livePos
                // Glue the path's tail to the live marker so the final hop isn't broken.
                val base = legPoints.value
                if (pathLine in mapView.overlays && base.isNotEmpty()) {
                    liveColor.value = altitudeColor(selPlane.aircraft.altitudeMeters)
                    val withLive = ArrayList<GeoPoint>(base.size + 1)
                    withLive.addAll(base)
                    withLive.add(livePos)
                    pathLine.setPoints(withLive)
                }
            } else {
                mapView.overlays.remove(highlight)
            }
            mapView.invalidate()
            delay(MAP_REFRESH_MS)
        }
    }

    // Center on the selected plane when selection opens.
    LaunchedEffect(selectedHex) {
        if (selectedHex == null) return@LaunchedEffect
        val plane = planes.firstOrNull { it.aircraft.hex == selectedHex } ?: return@LaunchedEffect
        mapView.controller.animateTo(GeoPoint(plane.lat, plane.lon))
    }

    // Draw the selected plane's flown path from its trace. Kept beneath the plane
    // markers (index 1, just above the tap-handling events overlay).
    LaunchedEffect(history, selectedHex) {
        mapView.overlays.remove(pathLine)
        val leg = if (selectedHex != null) currentLeg(history) else emptyList()
        val pts = leg.map { GeoPoint(it.lat!!, it.lon!!) }
        legPoints.value = pts
        legColors.value = IntArray(leg.size) { altitudeColor(leg[it].altitudeMeters) }
        if (pts.size >= 2) {
            pathLine.setPoints(pts)
            mapView.overlays.add(1, pathLine)
        }
        mapView.invalidate()
    }

    // Close drawer on back press.
    BackHandler(enabled = selectedHex != null) { onSelectPlane(null) }

    var showLayersSheet by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val peekPx = with(density) { PEEK_HEIGHT.toPx() }
        val expandedPx = with(density) { maxHeight.toPx() } * EXPANDED_FRACTION
        val drawerHeightAnim = remember { Animatable(peekPx) }

        // Snap back to peek height whenever a new plane is selected.
        LaunchedEffect(selectedHex) {
            if (selectedHex != null) drawerHeightAnim.snapTo(peekPx)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Map fills remaining space above the drawer.
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(top = 16.dp, end = 16.dp),
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
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = FloatingActionButtonDefaults.shape,
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "Recenter")
                }
            }

            // Resizable plane-detail panel — only present when a plane is selected.
            if (selectedHex != null) {
                val selected = planes.firstOrNull { it.aircraft.hex == selectedHex }
                PlaneDetailPanel(
                    selected = selected,
                    fallbackTitle = selectedHex,
                    details = details,
                    detailsLoading = detailsLoading,
                    history = history,
                    historyLoading = historyLoading,
                    historyError = historyError,
                    prefs = prefs,
                    drawerHeightAnim = drawerHeightAnim,
                    peekPx = peekPx,
                    expandedPx = expandedPx,
                    onDeselect = { onSelectPlane(null) },
                )
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaneDetailPanel(
    selected: TrackedPlane?,
    fallbackTitle: String,
    details: FlightDetails?,
    detailsLoading: Boolean,
    history: List<TrackSample>,
    historyLoading: Boolean,
    historyError: String?,
    prefs: UserPrefs,
    drawerHeightAnim: Animatable<Float, AnimationVector1D>,
    peekPx: Float,
    expandedPx: Float,
    onDeselect: () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val heightDp = with(density) { drawerHeightAnim.value.toDp() }

    val draggableState = rememberDraggableState { delta ->
        // Drag up (delta < 0) expands; drag down (delta > 0) collapses, min = peek.
        val newH = (drawerHeightAnim.value - delta).coerceIn(peekPx, expandedPx)
        scope.launch { drawerHeightAnim.snapTo(newH) }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp),
        shape = MaterialTheme.shapes.extraLarge.copy(
            bottomStart = androidx.compose.foundation.shape.ZeroCornerSize,
            bottomEnd = androidx.compose.foundation.shape.ZeroCornerSize,
        ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
    ) {
        Column {
            // Drag handle — tapping it toggles between peek and expanded.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            val mid = (peekPx + expandedPx) / 2f
                            val target = if (drawerHeightAnim.value >= mid) expandedPx else peekPx
                            scope.launch {
                                drawerHeightAnim.animateTo(
                                    target,
                                    animationSpec = spring(stiffness = 400f, dampingRatio = 0.75f),
                                    initialVelocity = -velocity, // invert: drag up = expand
                                )
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                BottomSheetDefaults.DragHandle()
            }

            PlaneDetailContent(
                selected = selected,
                fallbackTitle = fallbackTitle,
                details = details,
                detailsLoading = detailsLoading,
                samples = history,
                prefs = prefs,
                loading = historyLoading,
                error = historyError,
                onClose = onDeselect,
                showCloseButton = false,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
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
