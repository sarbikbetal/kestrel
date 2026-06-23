package com.sarbikbetal.kestrel.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarbikbetal.kestrel.settings.AltitudeUnit
import com.sarbikbetal.kestrel.settings.SpeedUnit
import com.sarbikbetal.kestrel.settings.TimeFormat
import com.sarbikbetal.kestrel.settings.UserPrefs
import com.sarbikbetal.kestrel.ui.theme.HudAmber
import com.sarbikbetal.kestrel.ui.theme.HudTextDim
import com.sarbikbetal.kestrel.ui.theme.NightBlue
import com.sarbikbetal.kestrel.ui.theme.PanelBg
import com.sarbikbetal.kestrel.ui.theme.PanelBorder
import com.sarbikbetal.kestrel.ui.theme.PanelRow

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
        containerColor = NightBlue,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "SETTINGS",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = HudAmber)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NightBlue,
                    titleContentColor = Color.White,
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ChoiceCard(
                title = "SPEED",
                options = SpeedUnit.entries,
                selected = prefs.speedUnit,
                labelOf = { it.label },
                onSelect = onSpeedUnit,
            )
            ChoiceCard(
                title = "ALTITUDE",
                options = AltitudeUnit.entries,
                selected = prefs.altitudeUnit,
                labelOf = { it.label },
                onSelect = onAltitudeUnit,
            )
            ChoiceCard(
                title = "TIME FORMAT",
                options = TimeFormat.entries,
                selected = prefs.timeFormat,
                labelOf = { it.label },
                onSelect = onTimeFormat,
            )
        }
    }
}

@Composable
private fun <T> ChoiceCard(
    title: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, PanelBorder, RoundedCornerShape(16.dp))
            .background(PanelBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(HudAmber))
            Text(
                title,
                modifier = Modifier.padding(start = 8.dp),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                ChoiceChip(
                    label = labelOf(option),
                    selected = option == selected,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) HudAmber else PanelRow
    val fg = if (selected) NightBlue else HudTextDim
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = fg,
        )
    }
}
