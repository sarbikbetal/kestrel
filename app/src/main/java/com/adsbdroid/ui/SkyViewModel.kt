package com.adsbdroid.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adsbdroid.data.Aircraft
import com.adsbdroid.data.AircraftRepository
import com.adsbdroid.data.AircraftResult
import com.adsbdroid.data.TraceRepository
import com.adsbdroid.data.TrackSample
import com.adsbdroid.domain.CountryFlags
import com.adsbdroid.domain.CountryInfo
import com.adsbdroid.domain.SkyMath
import com.adsbdroid.domain.SkyPosition
import com.adsbdroid.location.LocationProvider
import com.adsbdroid.location.UserLocation
import com.adsbdroid.sensors.DeviceOrientation
import com.adsbdroid.sensors.OrientationProvider
import com.adsbdroid.settings.SettingsRepository
import com.adsbdroid.settings.UserPrefs
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
)

class SkyViewModel(app: Application) : AndroidViewModel(app) {

    private val locationProvider = LocationProvider(app)
    private val orientationProvider = OrientationProvider(app)
    private val repository = AircraftRepository()
    private val traceRepository = TraceRepository()
    private val settingsRepository = SettingsRepository(app)

    private val _location = MutableStateFlow<UserLocation?>(null)
    val location: StateFlow<UserLocation?> = _location.asStateFlow()

    private val _orientation = MutableStateFlow(DeviceOrientation(0f, 0f, 0f))
    private val _snapshot = MutableStateFlow(Snapshot(emptyList(), 0L))
    private val _error = MutableStateFlow<String?>(null)
    private val _pollStatus = MutableStateFlow(PollStatus())
    private val _selectedHex = MutableStateFlow<String?>(null)
    private val _trace = MutableStateFlow(TraceState())

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
                val (targetLat, targetLon) = extrapolate(p, elapsedSec)
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
                    planeAltM = p.altitudeMeters,
                )
                val skyAhead = p.headingDeg?.let { hdg ->
                    val (aLat, aLon) = SkyMath.destinationPoint(lat, lon, hdg, HEADING_STEP_M)
                    SkyMath.skyPosition(
                        obsLat = loc.lat,
                        obsLon = loc.lon,
                        obsAltM = loc.altitudeMeters,
                        planeLat = aLat,
                        planeLon = aLon,
                        planeAltM = p.altitudeMeters,
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

    // Bundles the less-frequent signals so the main combine stays within 5 flows.
    private val misc: Flow<Misc> =
        combine(_error, _pollStatus, _trace) { err, poll, trace ->
            Misc(error = err, poll = poll, trace = trace)
        }

    val uiState: StateFlow<SkyUiState> =
        combine(
            _location,
            _orientation,
            trackedPlanes,
            misc,
            settingsRepository.prefs,
        ) { loc, orient, planes, m, prefs ->
            SkyUiState(
                location = loc,
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
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SkyUiState())

    init {
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

        // Fetch the selected plane's trace, then refresh it while it stays selected.
        viewModelScope.launch {
            _selectedHex.collectLatest { hex ->
                if (hex == null) {
                    _trace.value = TraceState()
                    return@collectLatest
                }
                _trace.value = TraceState(hex = hex, loading = true)
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

    /** Predicted position of [p] after [elapsedSec], or its last known position. */
    private fun extrapolate(p: Aircraft, elapsedSec: Double): Pair<Double, Double> {
        val speed = p.groundSpeedKnots
        val heading = p.headingDeg
        if (elapsedSec <= 0.0 || speed == null || speed <= 0.0 || heading == null) {
            return p.lat to p.lon
        }
        val distanceM = speed * KNOTS_TO_MPS * elapsedSec
        return SkyMath.destinationPoint(p.lat, p.lon, heading, distanceM)
    }

    /** Begin location updates; call only after ACCESS_FINE_LOCATION is granted. */
    fun startLocationUpdates() {
        if (locationJob != null) return
        locationJob = viewModelScope.launch {
            try {
                locationProvider.stream().collect { _location.value = it }
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

    /** Low-frequency signals bundled together to keep the main combine ≤ 5 flows. */
    private data class Misc(
        val error: String?,
        val poll: PollStatus,
        val trace: TraceState,
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
