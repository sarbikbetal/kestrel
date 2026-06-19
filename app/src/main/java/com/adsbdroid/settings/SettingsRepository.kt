package com.adsbdroid.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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

    private companion object {
        val KEY_SPEED = stringPreferencesKey("speed_unit")
        val KEY_ALT = stringPreferencesKey("altitude_unit")
        val KEY_TIME = stringPreferencesKey("time_format")
        val KEY_MAP_TYPE = stringPreferencesKey("map_type")
        val KEY_SHOW_AIRCRAFT = booleanPreferencesKey("show_aircraft")
        val KEY_SHOW_CONE = booleanPreferencesKey("show_heading_cone")
    }
}
