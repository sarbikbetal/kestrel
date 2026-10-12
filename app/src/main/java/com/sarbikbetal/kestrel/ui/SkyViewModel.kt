package com.sarbikbetal.kestrel.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sarbikbetal.kestrel.data.Aircraft
import com.sarbikbetal.kestrel.data.AircraftRepository
import com.sarbikbetal.kestrel.data.AircraftResult
import com.sarbikbetal.kestrel.data.FlightDetails
import com.sarbikbetal.kestrel.data.RouteRepository
import com.sarbikbetal.kestrel.data.TraceRepository
import com.sarbikbetal.kestrel.data.TrackSample
import com.sarbikbetal.kestrel.domain.CountryFlags
import com.sarbikbetal.kestrel.domain.CountryInfo
import com.sarbikbetal.kestrel.domain.SkyMath
import com.sarbikbetal.kestrel.domain.SkyPosition
import com.sarbikbetal.kestrel.location.LocationProvider
import com.sarbikbetal.kestrel.location.UserLocation
import com.sarbikbetal.kestrel.sensors.DeviceOrientation
import com.sarbikbetal.kestrel.sensors.OrientationProvider
import com.sarbikbetal.kestrel.settings.SettingsRepository
import com.sarbikbetal.kestrel.settings.UserPrefs
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** An aircraft plus its precomputed position in the observer's sky. */
data class TrackedPlane(
    val aircraft: Aircraft,
    /** Smoothed display position (eased toward the dead-reckoned target). */
    val lat: Double,
    val lon: Double,
    val sky: SkyPosition,
    /** Sky position a short step further along the plane's heading; null if unknown.
     *  The overlay projects this to orient the icon along on-screen motion. */
    val skyAhead: SkyPosition? = null,
    val country: CountryInfo? = null,
)

/** Status of the API polling cycle, used to drive the update-timer indicator. */
data class PollStatus(
    /** Monotonic time (elapsedRealtime) the last cycle completed. */
    val updatedAtMs: Long = 0L,
    /** Planned wait until the next call, i.e. the duration of the current cycle. */
    val periodMs: Long = 5_000L,
    /** Whether the last cycle fetched successfully. */
    val ok: Boolean = true,
)

data class SkyUiState(
    val location: UserLocation? = null,
    /**
     * True when [location] is sourced from the last persisted GPS fix rather than
     * a live hardware update. The UI uses this to nudge the user to enable GPS.
     */
    val locationIsStale: Boolean = false,
    val orientation: DeviceOrientation = DeviceOrientation(0f, 0f, 0f),
    val planes: List<TrackedPlane> = emptyList(),
    val sensorAvailable: Boolean = true,
    val error: String? = null,
    val prefs: UserPrefs = UserPrefs(),
    val poll: PollStatus = PollStatus(),
    /** Hex of the plane the user tapped, or null. */
    val selectedHex: String? = null,
    /** Flight trace (speed/altitude over time) for the selected plane. */
    val history: List<TrackSample> = emptyList(),
    val historyLoading: Boolean = false,
    val historyError: String? = null,
    /** Enriched airline + route details for the selected plane. */
    val details: FlightDetails? = null,
    val detailsLoading: Boolean = false,
    val detailsError: String? = null,
)

class SkyViewModel(app: Application) : AndroidViewModel(app) {

    private val locationProvider = LocationProvider(app)
    private val orientationProvider = OrientationProvider(app)
    private val repository = AircraftRepository()
    private val traceRepository = TraceRepository()
    private val routeRepository = RouteRepository()
    private val settingsRepository = SettingsRepository(app)

    // Eagerly-shared prefs so source-provider lambdas always see the latest value
    // without needing a second subscriber to the settings flow.
    private val currentPrefs = settingsRepository.prefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPrefs())

    private val _location = MutableStateFlow<UserLocation?>(null)
    val location: StateFlow<UserLocation?> = _location.asStateFlow()

    /** Whether device location services are switched on. Seeded from the system,
     *  re-checked on resume, and set true whenever a live fix arrives. Drives the
     *  "enable location" nudge so it only appears when location is actually off. */
    private val _locationServicesEnabled = MutableStateFlow(locationProvider.isLocationEnabled())

    private val _orientation = MutableStateFlow(DeviceOrientation(0f, 0f, 0f))

    /**
     * Raw, high-frequency (~50 Hz) orientation for the AR canvas only. Kept out of
     * [uiState] so a phone rotation drives the canvas *draw* phase, not a recomposition
     * of the whole screen (which would also re-run the osmdroid mini-map at 50 Hz).
     */
    val orientation: StateFlow<DeviceOrientation> = _orientation.asStateFlow()

    // Throttled copy for the rest of the UI (mini-map bearing, compass accuracy):
    // these only need ~10 Hz, so feeding uiState from this avoids 50 Hz state churn.
    @OptIn(FlowPreview::class)
    private val sampledOrientation: Flow<DeviceOrientation> = _orientation.sample(RENDER_INTERVAL_MS)
    private val _snapshot = MutableStateFlow(Snapshot(emptyList(), 0L))
    private val _error = MutableStateFlow<String?>(null)
    private val _pollStatus = MutableStateFlow(PollStatus())
    private val _selectedHex = MutableStateFlow<String?>(null)
    private val _trace = MutableStateFlow(TraceState())
    private val _route = MutableStateFlow(RouteState())

    private var locationJob: Job? = null

    // Smoothed display position per aircraft (hex -> [lat, lon]), eased toward the
    // dead-reckoned target each tick so planes glide to new spots instead of jumping.
    private val displayedPos = HashMap<String, DoubleArray>()
    private var lastTickMs = 0L

    // Drives smooth, frame-by-frame extrapolation independent of the API cadence.
    private val renderTicker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(RENDER_INTERVAL_MS)
        }
    }

    // Sky positions recompute every render tick, dead-reckoning each plane forward
    // from its last reported position and easing the displayed position toward it.
    private val trackedPlanes: StateFlow<List<TrackedPlane>> =
        combine(_snapshot, _location, renderTicker) { snap, loc, _ ->
            if (loc == null) return@combine emptyList()
            val nowMs = SystemClock.elapsedRealtime()
            val dtSec = if (lastTickMs == 0L) 0.1 else ((nowMs - lastTickMs) / 1000.0).coerceIn(0.0, 1.0)
            lastTickMs = nowMs
            // Frame-rate-independent exponential easing factor.
            val alpha = 1.0 - kotlin.math.exp(-dtSec / SMOOTH_TAU_SEC)
            val elapsedSec = if (snap.fetchedAtMs == 0L) 0.0 else {
                ((nowMs - snap.fetchedAtMs) / 1000.0).coerceIn(0.0, MAX_EXTRAPOLATION_SEC)
            }
            val seen = HashSet<String>(snap.planes.size)
            val tracked = snap.planes.map { p ->
                val (targetLat, targetLon, targetAlt) = extrapolate(p, elapsedSec)
                seen += p.hex
                val cur = displayedPos[p.hex]
                val lat: Double
                val lon: Double
                if (cur == null) {
                    lat = targetLat; lon = targetLon
                    displayedPos[p.hex] = doubleArrayOf(lat, lon)
                } else {
                    lat = cur[0] + (targetLat - cur[0]) * alpha
                    lon = cur[1] + (targetLon - cur[1]) * alpha
                    cur[0] = lat; cur[1] = lon
                }
                val sky = SkyMath.skyPosition(
                    obsLat = loc.lat,
                    obsLon = loc.lon,
                    obsAltM = loc.altitudeMeters,
                    planeLat = lat,
                    planeLon = lon,
                    planeAltM = targetAlt,
                )
                val skyAhead = p.headingDeg?.let { hdg ->
                    val (aLat, aLon) = SkyMath.destinationPoint(lat, lon, hdg, HEADING_STEP_M)
                    SkyMath.skyPosition(
                        obsLat = loc.lat,
                        obsLon = loc.lon,
                        obsAltM = loc.altitudeMeters,
                        planeLat = aLat,
                        planeLon = aLon,
                        planeAltM = targetAlt,
                    )
                }
                TrackedPlane(
                    aircraft = p,
                    lat = lat,
                    lon = lon,
                    sky = sky,
                    skyAhead = skyAhead,
                    country = CountryFlags.lookup(p.hex),
                )
            }.sortedBy { it.sky.slantDistanceMeters }
            // Drop planes that are no longer present so the map can't grow unbounded.
            displayedPos.keys.retainAll(seen)
            tracked
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Selection is the source of truth for `selectedHex`: _trace is only reset after the
    // in-flight (non-cancellable) network fetch returns, which used to leave the sheet
    // stuck open - and its close button inert - while data was still loading.
    private val selectedTrace: Flow<TraceState> = combine(_trace, _selectedHex) { t, h ->
        when {
            h == null -> TraceState()
            t.hex == h -> t
            else -> TraceState(hex = h, loading = true)
        }
    }

    // Bundles the less-frequent signals so the main combine stays within 5 flows.
    private val misc: Flow<Misc> =
        combine(_error, _pollStatus, selectedTrace, _route, _locationServicesEnabled) { err, poll, trace, route, enabled ->
            Misc(error = err, poll = poll, trace = trace, route = route, locationServicesEnabled = enabled)
        }

    val uiState: StateFlow<SkyUiState> =
        combine(
            _location,
            sampledOrientation,
            trackedPlanes,
            misc,
            settingsRepository.prefs,
        ) { loc, orient, planes, m, prefs ->
            SkyUiState(
                location = loc,
                locationIsStale = loc != null && !m.locationServicesEnabled,
                orientation = orient,
                planes = planes,
                sensorAvailable = orientationProvider.isAvailable,
                error = m.error,
                prefs = prefs,
                poll = m.poll,
                selectedHex = m.trace.hex,
                history = m.trace.samples,
                historyLoading = m.trace.loading,
                historyError = m.trace.error,
                details = m.route.details,
                detailsLoading = m.route.loading,
                detailsError = m.route.error,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SkyUiState())

    init {
        // Seed location from the last persisted fix so planes are visible immediately,
        // even before the hardware GPS locks on. Cleared to "live" once real fixes arrive.
        viewModelScope.launch {
            val cached = settingsRepository.loadLastLocation()
            if (cached != null && _location.value == null) {
                _location.value = cached
                // If services are off the nudge shows; if on, we silently use the
                // cached fix until the first live update arrives.
            }
        }

        if (orientationProvider.isAvailable) {
            viewModelScope.launch {
                orientationProvider.stream().collect { _orientation.value = it }
            }
        }
        viewModelScope.launch {
            repository.stream(
                intervalMs = 5_000L,
                radiusNm = 100,
                locationProvider = { _location.value?.let { it.lat to it.lon } },
                sourceProvider = { currentPrefs.value.positionSource },
            ).collect { result ->
                val now = SystemClock.elapsedRealtime()
                when (result) {
                    is AircraftResult.Success -> {
                        _snapshot.value = Snapshot(result.aircraft, now)
                        _error.value = null
                        _pollStatus.value = PollStatus(now, result.nextDelayMs, ok = true)
                    }
                    is AircraftResult.Error -> {
                        _error.value = result.message
                        _pollStatus.value = PollStatus(now, result.nextDelayMs, ok = false)
                    }
                }
            }
        }

        // Fetch the selected plane's trace + route, then refresh trace while selected.
        viewModelScope.launch {
            _selectedHex.collectLatest { hex ->
                if (hex == null) {
                    _trace.value = TraceState()
                    _route.value = RouteState()
                    return@collectLatest
                }
                _trace.value = TraceState(hex = hex, loading = true)
                _route.value = RouteState(loading = true)

                // Route is one-shot per callsign (cached with 10-min TTL).
                val plane = _snapshot.value.planes.firstOrNull { it.hex == hex }
                if (plane != null) {
                    routeRepository.fetch(plane.callsign, hex, plane.lat, plane.lon, currentPrefs.value.routeSource).fold(
                        onSuccess = { details -> _route.value = RouteState(details = details) },
                        onFailure = { e -> _route.value = RouteState(error = e.message) },
                    )
                } else {
                    _route.value = RouteState()
                }

                // Trace refreshes every 30 s while the plane stays selected.
                while (true) {
                    traceRepository.fetch(hex).fold(
                        onSuccess = { samples ->
                            _trace.value = TraceState(hex = hex, loading = false, samples = samples)
                        },
                        onFailure = { e ->
                            _trace.value = _trace.value.copy(
                                loading = false,
                                error = e.message ?: "Couldn't load flight history",
                            )
                        },
                    )
                    delay(TRACE_REFRESH_MS)
                }
            }
        }
    }

    /** Select a plane to show its speed/altitude history, or pass null to dismiss. */
    fun selectPlane(hex: String?) {
        _selectedHex.value = hex
    }

    fun clearSelection() {
        _selectedHex.value = null
    }

    /**
     * Predicted position of [p] after [elapsedSec] plus any age already baked into
     * the API snapshot (seen_pos). Returns (lat, lon, altMeters).
     */
    private fun extrapolate(p: Aircraft, elapsedSec: Double): Triple<Double, Double, Double> {
        // seen_pos: position was already this many seconds old when the snapshot arrived.
        val effectiveSec = (elapsedSec + p.seenPosSec).coerceIn(0.0, MAX_EXTRAPOLATION_SEC)
        val predAlt = p.altitudeMeters + (p.verticalRateMps ?: 0.0) * effectiveSec
        val speed = p.groundSpeedKnots
        val heading = p.headingDeg
        if (effectiveSec <= 0.0 || speed == null || speed <= 0.0 || heading == null) {
            return Triple(p.lat, p.lon, predAlt)
        }
        val distanceM = speed * KNOTS_TO_MPS * effectiveSec
        val (lat, lon) = SkyMath.destinationPoint(p.lat, p.lon, heading, distanceM)
        return Triple(lat, lon, predAlt)
    }

    /** Re-check whether location services are on (e.g. after the user returns from
     *  the system location settings) so the "enable location" nudge stays accurate. */
    fun refreshLocationServices() {
        _locationServicesEnabled.value = locationProvider.isLocationEnabled()
    }

    /** Begin location updates; call only after ACCESS_FINE_LOCATION is granted. */
    fun startLocationUpdates() {
        if (locationJob != null) return
        locationJob = viewModelScope.launch {
            try {
                locationProvider.stream().collect { loc ->
                    _location.value = loc
                    // A live fix proves services are on; clear any stale nudge.
                    _locationServicesEnabled.value = true
                    // Persist so the next cold start has a starting point.
                    settingsRepository.saveLastLocation(loc)
                }
            } catch (e: SecurityException) {
                _error.value = "Location permission missing"
            }
        }
    }

    /** Last batch of aircraft plus the monotonic time it was fetched. */
    private data class Snapshot(val planes: List<Aircraft>, val fetchedAtMs: Long)

    /** Trace fetch state for the currently selected plane. */
    private data class TraceState(
        val hex: String? = null,
        val loading: Boolean = false,
        val samples: List<TrackSample> = emptyList(),
        val error: String? = null,
    )

    /** Route/airline fetch state for the currently selected plane. */
    private data class RouteState(
        val loading: Boolean = false,
        val details: FlightDetails? = null,
        val error: String? = null,
    )

    /** Low-frequency signals bundled together to keep the main combine ≤ 5 flows. */
    private data class Misc(
        val error: String?,
        val poll: PollStatus,
        val trace: TraceState,
        val route: RouteState,
        val locationServicesEnabled: Boolean,
    )

    companion object {
        private const val RENDER_INTERVAL_MS = 100L
        private const val KNOTS_TO_MPS = 0.514444
        // Easing time constant for position transitions (~0.6s to mostly settle).
        private const val SMOOTH_TAU_SEC = 0.6
        // Cap how far we trust dead reckoning if fetches stall (e.g. during backoff).
        private const val MAX_EXTRAPOLATION_SEC = 30.0
        // Step ahead along heading used only to derive on-screen icon orientation.
        private const val HEADING_STEP_M = 2_000.0
        // How often to refresh the selected plane's trace while it stays open.
        private const val TRACE_REFRESH_MS = 30_000L
    }
}
