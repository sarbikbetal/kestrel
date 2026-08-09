package com.sarbikbetal.kestrel.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sarbikbetal.kestrel.settings.AltitudeUnit
import com.sarbikbetal.kestrel.settings.AppTheme
import com.sarbikbetal.kestrel.settings.PositionSource
import com.sarbikbetal.kestrel.settings.RouteSource
import com.sarbikbetal.kestrel.settings.SpeedUnit
import com.sarbikbetal.kestrel.settings.TimeFormat
import com.sarbikbetal.kestrel.settings.UserPrefs

private const val GITHUB_URL = "https://github.com/sarbikbetal/kestrel"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: UserPrefs,
    compassAccuracy: Int,
    onSpeedUnit: (SpeedUnit) -> Unit,
    onAltitudeUnit: (AltitudeUnit) -> Unit,
    onTimeFormat: (TimeFormat) -> Unit,
    onPositionSource: (PositionSource) -> Unit,
    onRouteSource: (RouteSource) -> Unit,
    onAppTheme: (AppTheme) -> Unit,
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // ── Appearance ────────────────────────────────────────────────
            SectionHeader("Appearance")
            SettingsGroup {
                ListPreferenceItem(
                    title = "Theme",
                    selected = prefs.appTheme,
                    options = AppTheme.entries,
                    labelOf = { it.label },
                    onSelect = onAppTheme,
                )
            }

            // ── Units ─────────────────────────────────────────────────────
            SectionHeader("Units")
            SettingsGroup {
                ListPreferenceItem(
                    title = "Speed",
                    selected = prefs.speedUnit,
                    options = SpeedUnit.entries,
                    labelOf = { it.label },
                    onSelect = onSpeedUnit,
                )
                GroupDivider()
                ListPreferenceItem(
                    title = "Altitude",
                    selected = prefs.altitudeUnit,
                    options = AltitudeUnit.entries,
                    labelOf = { it.label },
                    onSelect = onAltitudeUnit,
                )
                GroupDivider()
                ListPreferenceItem(
                    title = "Time format",
                    selected = prefs.timeFormat,
                    options = TimeFormat.entries,
                    labelOf = { it.label },
                    onSelect = onTimeFormat,
                )
            }

            // ── Data sources ──────────────────────────────────────────────
            SectionHeader("Data sources")
            SettingsGroup {
                ListPreferenceItem(
                    title = "Aircraft positions",
                    selected = prefs.positionSource,
                    options = PositionSource.entries,
                    labelOf = { it.label },
                    onSelect = onPositionSource,
                )
                GroupDivider()
                ListPreferenceItem(
                    title = "Route data",
                    selected = prefs.routeSource,
                    options = RouteSource.entries,
                    labelOf = { it.label },
                    onSelect = onRouteSource,
                )
            }

            // ── Sensors ───────────────────────────────────────────────────
            SectionHeader("Sensors")
            CompassGroup(compassAccuracy = compassAccuracy)

            // ── About ─────────────────────────────────────────────────────
            SectionHeader("About")
            AboutGroup()

            Spacer(Modifier.padding(bottom = 16.dp))
        }
    }
}

// ── Section label ─────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 20.dp, bottom = 6.dp),
    )
}

// ── Card group wrapper ────────────────────────────────────────────────────────

@Composable
private fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
private fun GroupDivider() =
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))

// ── Generic list preference row ───────────────────────────────────────────────

@Composable
private fun <T> ListPreferenceItem(
    title: String,
    selected: T,
    options: List<T>,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                labelOf(selected),
                color = MaterialTheme.colorScheme.primary,
            )
        },
        modifier = modifier.clickable { showDialog = true },
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
                                .clickable {
                                    onSelect(option)
                                    showDialog = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = option == selected,
                                onClick = {
                                    onSelect(option)
                                    showDialog = false
                                },
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(labelOf(option))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// ── Compass & Gyro group ──────────────────────────────────────────────────────

@Composable
private fun CompassGroup(compassAccuracy: Int, modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }
    val (label, color) = accuracyLabelAndColor(compassAccuracy)

    SettingsGroup(modifier = modifier) {
        ListItem(
            headlineContent = { Text("Sync accuracy") },
            supportingContent = { Text(label, color = color) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        GroupDivider()
        ListItem(
            headlineContent = { Text("Calibrate compass") },
            supportingContent = { Text("Trace a figure-8 to improve accuracy") },
            modifier = Modifier.clickable { showDialog = true },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
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

/** Human-readable label and a theme color for the given accuracy level. */
@Composable
private fun accuracyLabelAndColor(accuracy: Int): Pair<String, Color> = when (accuracy) {
    3    -> "High"        to MaterialTheme.colorScheme.primary
    2    -> "Medium"      to MaterialTheme.colorScheme.tertiary
    1    -> "Low"         to MaterialTheme.colorScheme.error
    0    -> "Unreliable"  to MaterialTheme.colorScheme.error
    else -> "Waiting…"    to MaterialTheme.colorScheme.onSurfaceVariant
}

// ── About ─────────────────────────────────────────────────────────────────────

@Composable
private fun AboutGroup(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifEmpty { "—" }
    }
    val openUrl: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    SettingsGroup(modifier = modifier) {
        ListItem(
            headlineContent = { Text("Version") },
            supportingContent = { Text(version) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        GroupDivider()
        ListItem(
            headlineContent = { Text("Source code") },
            supportingContent = {
                Text(
                    "github.com/sarbikbetal/kestrel",
                    color = MaterialTheme.colorScheme.primary,
                )
            },
            modifier = Modifier.clickable { openUrl(GITHUB_URL) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        GroupDivider()
        CreditLink("adsb.lol", "Aircraft positions & traces", "https://adsb.lol", openUrl)
        GroupDivider()
        CreditLink("airplanes.live", "Aircraft positions", "https://airplanes.live", openUrl)
        GroupDivider()
        CreditLink("hexdb.io", "Route data", "https://hexdb.io", openUrl)
        GroupDivider()
        CreditLink("adsbdb.com", "Route data", "https://www.adsbdb.com", openUrl)
        GroupDivider()
        CreditLink("OpenStreetMap", "Map tiles", "https://www.openstreetmap.org/copyright", openUrl)
    }
}

@Composable
private fun CreditLink(
    label: String,
    role: String,
    url: String,
    openUrl: (String) -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(role, color = MaterialTheme.colorScheme.primary)
        },
        modifier = Modifier.clickable { openUrl(url) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
