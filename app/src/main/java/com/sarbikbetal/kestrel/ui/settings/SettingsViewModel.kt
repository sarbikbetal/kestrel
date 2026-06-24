package com.sarbikbetal.kestrel.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sarbikbetal.kestrel.settings.AltitudeUnit
import com.sarbikbetal.kestrel.settings.MapType
import com.sarbikbetal.kestrel.settings.PositionSource
import com.sarbikbetal.kestrel.settings.RouteSource
import com.sarbikbetal.kestrel.settings.SettingsRepository
import com.sarbikbetal.kestrel.settings.SkyViewMode
import com.sarbikbetal.kestrel.settings.SpeedUnit
import com.sarbikbetal.kestrel.settings.TimeFormat
import com.sarbikbetal.kestrel.settings.UserPrefs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SettingsRepository(app)

    val prefs: StateFlow<UserPrefs> =
        repository.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, UserPrefs())

    fun setSpeedUnit(unit: SpeedUnit) = viewModelScope.launch { repository.setSpeedUnit(unit) }
    fun setAltitudeUnit(unit: AltitudeUnit) = viewModelScope.launch { repository.setAltitudeUnit(unit) }
    fun setTimeFormat(format: TimeFormat) = viewModelScope.launch { repository.setTimeFormat(format) }
    fun setMapType(type: MapType) = viewModelScope.launch { repository.setMapType(type) }
    fun setShowAircraft(show: Boolean) = viewModelScope.launch { repository.setShowAircraft(show) }
    fun setShowHeadingCone(show: Boolean) = viewModelScope.launch { repository.setShowHeadingCone(show) }
    fun setPositionSource(source: PositionSource) = viewModelScope.launch { repository.setPositionSource(source) }
    fun setRouteSource(source: RouteSource) = viewModelScope.launch { repository.setRouteSource(source) }
    fun setSkyViewMode(mode: SkyViewMode) = viewModelScope.launch { repository.setSkyViewMode(mode) }
}
