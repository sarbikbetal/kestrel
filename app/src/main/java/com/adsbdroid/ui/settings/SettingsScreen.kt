package com.adsbdroid.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adsbdroid.settings.AltitudeUnit
import com.adsbdroid.settings.SpeedUnit
import com.adsbdroid.settings.TimeFormat
import com.adsbdroid.settings.UserPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: UserPrefs,
    onSpeedUnit: (SpeedUnit) -> Unit,
    onAltitudeUnit: (AltitudeUnit) -> Unit,
    onTimeFormat: (TimeFormat) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ChoiceGroup(
                title = "Speed",
                options = SpeedUnit.entries,
                selected = prefs.speedUnit,
                labelOf = { it.label },
                onSelect = onSpeedUnit,
            )
            ChoiceGroup(
                title = "Altitude",
                options = AltitudeUnit.entries,
                selected = prefs.altitudeUnit,
                labelOf = { it.label },
                onSelect = onAltitudeUnit,
            )
            ChoiceGroup(
                title = "Time format",
                options = TimeFormat.entries,
                selected = prefs.timeFormat,
                labelOf = { it.label },
                onSelect = onTimeFormat,
            )
        }
    }
}

@Composable
private fun <T> ChoiceGroup(
    title: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    ElevatedCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    FilterChip(
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        label = { Text(labelOf(option)) },
                    )
                }
            }
        }
    }
}
