package com.sarbikbetal.kestrel.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Color
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
import org.osmdroid.views.overlay.TilesOverlay

private const val MAP_REFRESH_MS = 100L
private val PEEK_HEIGHT = 268.dp
private val EXPANDED_FRACTION = 0.68f
private val DRAWER_SPRING = spring<Float>(stiffness = 400f, dampingRatio = 1f)

private data class PanelContent(
    val hex: String,
    val selected: TrackedPlane?,
    val details: FlightDetails?,
    val detailsLoading: Boolean,
    val history: List<TrackSample>,
    val historyLoading: Boolean,
    val historyError: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    location: UserLocation?,
    planes: List<TrackedPlane>,
    prefs: UserPrefs,
    azimuthDeg: Float,
    selectedHex: String?,
    onSelectPlane: (String?) -> Unit,
    onSetMapType: (MapType) -> Unit,
    onToggleAircraft: (Boolean) -> Unit,
    onToggleHeadingCone: (Boolean) -> Unit,
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
                planeLayer.sync(curPlanes, curPrefs, selectedHex = curSelectedHex, onSelect = curOnSelect)
            } else {
                planeLayer.clear()
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

    // Close drawer on back press.
    BackHandler(enabled = selectedHex != null) { onSelectPlane(null) }

    var showLayersSheet by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val peekPx = with(density) { PEEK_HEIGHT.toPx() }
        val expandedPx = with(density) { maxHeight.toPx() } * EXPANDED_FRACTION
        val drawerHeightAnim = remember { Animatable(0f) }

        // Slide up on first selection, reset to peek when switching planes, and slide
        // out (rather than vanish) on deselect / back.
        LaunchedEffect(selectedHex) {
            when {
                selectedHex == null -> drawerHeightAnim.animateTo(0f, DRAWER_SPRING)
                drawerHeightAnim.value == 0f -> drawerHeightAnim.animateTo(peekPx, DRAWER_SPRING)
                else -> drawerHeightAnim.snapTo(peekPx)
            }
        }

        // Keep the last selection's content around while the drawer animates out.
        var panel by remember { mutableStateOf<PanelContent?>(null) }
        if (selectedHex != null) {
            val next = PanelContent(
                selectedHex,
                planes.firstOrNull { it.aircraft.hex == selectedHex },
                details, detailsLoading, history, historyLoading, historyError,
            )
            if (next != panel) panel = next
        }
        val drawerVisible by remember { derivedStateOf { drawerHeightAnim.value > 0f } }

        Column(modifier = Modifier.fillMaxSize()) {
            // Map fills remaining space above the drawer.
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    SmallFloatingActionButton(
                        onClick = { showLayersSheet = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = FloatingActionButtonDefaults.smallShape,
                    ) {
                        Icon(Icons.Filled.Layers, contentDescription = "Map layers")
                    }
                    FloatingActionButton(
                        onClick = {
                            location?.let { mapView.controller.animateTo(GeoPoint(it.lat, it.lon)) }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = FloatingActionButtonDefaults.shape,
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = "Recenter")
                    }
                }
            }

            // Resizable plane-detail panel - present while selected or animating out.
            val shown = panel
            if (shown != null && (selectedHex != null || drawerVisible)) {
                PlaneDetailPanel(
                    selected = shown.selected,
                    fallbackTitle = shown.hex,
                    details = shown.details,
                    detailsLoading = shown.detailsLoading,
                    history = shown.history,
                    historyLoading = shown.historyLoading,
                    historyError = shown.historyError,
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
    val heightDp = with(density) { drawerHeightAnim.value.coerceAtLeast(0f).toDp() }

    // Scrolling the content first grows the drawer to its max height, then scrolls the
    // content; pulling down scrolls the content back to top, then shrinks the drawer.
    val nestedScroll = remember(peekPx, expandedPx) {
        fun resize(delta: Float) {
            val newH = (drawerHeightAnim.value - delta).coerceIn(peekPx, expandedPx)
            scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                drawerHeightAnim.snapTo(newH)
            }
        }
        fun settle(velocityY: Float) {
            val h = drawerHeightAnim.value
            val target = when {
                velocityY < 0f -> expandedPx
                velocityY > 0f -> peekPx
                h >= (peekPx + expandedPx) / 2f -> expandedPx
                else -> peekPx
            }
            scope.launch { drawerHeightAnim.animateTo(target, DRAWER_SPRING, -velocityY) }
        }
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val h = drawerHeightAnim.value
                if (source != NestedScrollSource.UserInput || available.y >= 0f || h >= expandedPx) return Offset.Zero
                val used = minOf(-available.y, expandedPx - h)
                resize(-used)
                return Offset(0f, -used)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val h = drawerHeightAnim.value
                if (source != NestedScrollSource.UserInput || available.y <= 0f || h <= peekPx) return Offset.Zero
                val used = minOf(available.y, h - peekPx)
                resize(used)
                return Offset(0f, used)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val h = drawerHeightAnim.value
                if (h <= peekPx || h >= expandedPx) return Velocity.Zero
                settle(available.y)
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y > 0f && drawerHeightAnim.value > peekPx) settle(available.y)
                return Velocity.Zero
            }
        }
    }

    val draggableState = rememberDraggableState { delta ->
        // Drag up (delta < 0) expands; drag down (delta > 0) collapses, min = peek.
        val newH = (drawerHeightAnim.value - delta).coerceIn(peekPx, expandedPx)
        scope.launch { drawerHeightAnim.snapTo(newH) }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp)
            .nestedScroll(nestedScroll),
        shape = MaterialTheme.shapes.extraLarge.copy(
            bottomStart = androidx.compose.foundation.shape.ZeroCornerSize,
            bottomEnd = androidx.compose.foundation.shape.ZeroCornerSize,
        ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
    ) {
        Column {
            // Drag handle - tapping it toggles between peek and expanded.
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

@OptIn(ExperimentalMaterial3Api::class)
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
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Map layers",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )

            SheetSectionHeader("Map type")
            SheetGroup {
                SheetListPreference(
                    title = "Map type",
                    selected = prefs.mapType,
                    options = MapType.entries,
                    labelOf = { it.label },
                    onSelect = onSetMapType,
                )
            }

            SheetSectionHeader("Overlays")
            SheetGroup {
                ToggleRow("Aircraft", prefs.showAircraft, onToggleAircraft)
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                ToggleRow("Heading cone", prefs.showHeadingCone, onToggleHeadingCone)
            }
        }
    }
}

@Composable
private fun SheetSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun SheetGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(content = content)
    }
}

@Composable
private fun <T> SheetListPreference(
    title: String,
    selected: T,
    options: List<T>,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(labelOf(selected)) },
        modifier = Modifier.clickable { showDialog = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(option); showDialog = false }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = option == selected,
                                onClick = { onSelect(option); showDialog = false },
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(labelOf(option))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
