package com.sarbikbetal.kestrel.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sarbikbetal.kestrel.settings.AltitudeUnit
import com.sarbikbetal.kestrel.settings.PositionSource
import com.sarbikbetal.kestrel.settings.RouteSource
import com.sarbikbetal.kestrel.settings.SpeedUnit
import com.sarbikbetal.kestrel.settings.TimeFormat
import com.sarbikbetal.kestrel.settings.UserPrefs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    prefs: UserPrefs,
    compassAccuracy: Int,
    onSpeedUnit: (SpeedUnit) -> Unit,
    onAltitudeUnit: (AltitudeUnit) -> Unit,
    onTimeFormat: (TimeFormat) -> Unit,
    onPositionSource: (PositionSource) -> Unit,
    onRouteSource: (RouteSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChoiceCard(
                title = "Speed",
                options = SpeedUnit.entries,
                selected = prefs.speedUnit,
                labelOf = { it.label },
                onSelect = onSpeedUnit,
            )
            ChoiceCard(
                title = "Altitude",
                options = AltitudeUnit.entries,
                selected = prefs.altitudeUnit,
                labelOf = { it.label },
                onSelect = onAltitudeUnit,
            )
            ChoiceCard(
                title = "Time format",
                options = TimeFormat.entries,
                selected = prefs.timeFormat,
                labelOf = { it.label },
                onSelect = onTimeFormat,
            )
            ChoiceCard(
                title = "Position source",
                options = PositionSource.entries,
                selected = prefs.positionSource,
                labelOf = { it.label },
                onSelect = onPositionSource,
            )
            ChoiceCard(
                title = "Route data",
                options = RouteSource.entries,
                selected = prefs.routeSource,
                labelOf = { it.label },
                onSelect = onRouteSource,
            )
            CompassCard(compassAccuracy = compassAccuracy)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CompassCard(compassAccuracy: Int, modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }
    val (label, color) = accuracyLabelAndColor(compassAccuracy)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Compass & Gyro",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Sync accuracy",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
            }
            FilledTonalButton(
                onClick = { showDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Calibrate compass")
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Calibrate Compass") },
            text = {
                Text(
                    "For best AR accuracy:\n\n" +
                    "1. Move away from metal surfaces and electronics.\n\n" +
                    "2. Hold your phone and slowly trace a figure-8 pattern in the air " +
                    "several times, rotating through all orientations.\n\n" +
                    "3. Repeat until the accuracy shows High.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Done") }
            },
        )
    }
}

/** Returns a human-readable label and a theme color for the given accuracy level. */
@Composable
private fun accuracyLabelAndColor(accuracy: Int): Pair<String, Color> = when (accuracy) {
    3 -> "High" to MaterialTheme.colorScheme.primary
    2 -> "Medium" to MaterialTheme.colorScheme.tertiary
    1 -> "Low" to MaterialTheme.colorScheme.error
    0 -> "Unreliable" to MaterialTheme.colorScheme.error
    else -> "Waiting…" to MaterialTheme.colorScheme.onSurfaceVariant
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ChoiceCard(
    title: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    ) {
                        Text(labelOf(option))
                    }
                }
            }
        }
    }
}
