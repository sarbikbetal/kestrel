package com.adsbdroid.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adsbdroid.settings.AltitudeUnit
import com.adsbdroid.settings.MapType
import com.adsbdroid.settings.SettingsRepository
import com.adsbdroid.settings.SpeedUnit
import com.adsbdroid.settings.TimeFormat
import com.adsbdroid.settings.UserPrefs
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
}
