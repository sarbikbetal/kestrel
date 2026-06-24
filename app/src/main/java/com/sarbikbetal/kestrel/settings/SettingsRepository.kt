package com.sarbikbetal.kestrel.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sarbikbetal.kestrel.location.UserLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val ds = context.applicationContext.dataStore

    val prefs: Flow<UserPrefs> = ds.data.map { p ->
        UserPrefs(
            speedUnit = p[KEY_SPEED]?.let { runCatching { SpeedUnit.valueOf(it) }.getOrNull() }
                ?: SpeedUnit.KNOTS,
            altitudeUnit = p[KEY_ALT]?.let { runCatching { AltitudeUnit.valueOf(it) }.getOrNull() }
                ?: AltitudeUnit.FEET,
            timeFormat = p[KEY_TIME]?.let { runCatching { TimeFormat.valueOf(it) }.getOrNull() }
                ?: TimeFormat.H24,
            mapType = p[KEY_MAP_TYPE]?.let { runCatching { MapType.valueOf(it) }.getOrNull() }
                ?: MapType.STANDARD,
            showAircraft = p[KEY_SHOW_AIRCRAFT] ?: true,
            showHeadingCone = p[KEY_SHOW_CONE] ?: true,
            positionSource = p[KEY_POSITION_SOURCE]?.let { runCatching { PositionSource.valueOf(it) }.getOrNull() }
                ?: PositionSource.ADSB_LOL,
            routeSource = p[KEY_ROUTE_SOURCE]?.let { runCatching { RouteSource.valueOf(it) }.getOrNull() }
                ?: RouteSource.HEXDB,
        )
    }

    suspend fun setSpeedUnit(unit: SpeedUnit) {
        ds.edit { it[KEY_SPEED] = unit.name }
    }

    suspend fun setAltitudeUnit(unit: AltitudeUnit) {
        ds.edit { it[KEY_ALT] = unit.name }
    }

    suspend fun setTimeFormat(format: TimeFormat) {
        ds.edit { it[KEY_TIME] = format.name }
    }

    suspend fun setMapType(type: MapType) {
        ds.edit { it[KEY_MAP_TYPE] = type.name }
    }

    suspend fun setShowAircraft(show: Boolean) {
        ds.edit { it[KEY_SHOW_AIRCRAFT] = show }
    }

    suspend fun setShowHeadingCone(show: Boolean) {
        ds.edit { it[KEY_SHOW_CONE] = show }
    }

    suspend fun setPositionSource(source: PositionSource) {
        ds.edit { it[KEY_POSITION_SOURCE] = source.name }
    }

    suspend fun setRouteSource(source: RouteSource) {
        ds.edit { it[KEY_ROUTE_SOURCE] = source.name }
    }

    /** Persists the most recent GPS fix so it survives process restarts. */
    suspend fun saveLastLocation(loc: UserLocation) {
        ds.edit { p ->
            p[KEY_LOC_LAT] = loc.lat
            p[KEY_LOC_LON] = loc.lon
            p[KEY_LOC_ALT] = loc.altitudeMeters
            p[KEY_LOC_ACC] = loc.accuracyMeters
        }
    }

    /** Returns the last persisted location, or null if none was ever saved. */
    suspend fun loadLastLocation(): UserLocation? {
        val p = ds.data.firstOrNull() ?: return null
        val lat = p[KEY_LOC_LAT] ?: return null
        val lon = p[KEY_LOC_LON] ?: return null
        return UserLocation(
            lat = lat,
            lon = lon,
            altitudeMeters = p[KEY_LOC_ALT] ?: 0.0,
            accuracyMeters = p[KEY_LOC_ACC] ?: Float.NaN,
        )
    }

    private companion object {
        val KEY_SPEED = stringPreferencesKey("speed_unit")
        val KEY_ALT = stringPreferencesKey("altitude_unit")
        val KEY_TIME = stringPreferencesKey("time_format")
        val KEY_MAP_TYPE = stringPreferencesKey("map_type")
        val KEY_SHOW_AIRCRAFT = booleanPreferencesKey("show_aircraft")
        val KEY_SHOW_CONE = booleanPreferencesKey("show_heading_cone")
        val KEY_POSITION_SOURCE = stringPreferencesKey("position_source")
        val KEY_ROUTE_SOURCE = stringPreferencesKey("route_source")
        // Last known GPS fix
        val KEY_LOC_LAT = doublePreferencesKey("loc_lat")
        val KEY_LOC_LON = doublePreferencesKey("loc_lon")
        val KEY_LOC_ALT = doublePreferencesKey("loc_alt")
        val KEY_LOC_ACC = floatPreferencesKey("loc_acc")
    }
}
