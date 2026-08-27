package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.viewmodel.BluetoothTrackerViewModel
import com.example.model.FilterSettings
import com.example.model.GeneralSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: BluetoothTrackerViewModel, navController: NavController) {
    val filterSettings by viewModel.filterSettings.collectAsState()
    val generalSettings by viewModel.generalSettings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SectionHeader(title = "Detection Filters", icon = Icons.Default.FilterList)
            
            SettingsSwitch(
                label = "Show Bluetooth Signals",
                checked = filterSettings.showBluetooth,
                onCheckedChange = { viewModel.updateFilterSettings(filterSettings.copy(showBluetooth = it)) }
            )
            SettingsSwitch(
                label = "Show Wi-Fi Signals",
                checked = filterSettings.showWifi,
                onCheckedChange = { viewModel.updateFilterSettings(filterSettings.copy(showWifi = it)) }
            )
            SettingsSwitch(
                label = "Show EMF Anomalies",
                checked = filterSettings.showEmf,
                onCheckedChange = { viewModel.updateFilterSettings(filterSettings.copy(showEmf = it)) }
            )
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            SettingsSwitch(
                label = "Named Devices Only",
                checked = filterSettings.showNamedOnly,
                onCheckedChange = { viewModel.updateFilterSettings(filterSettings.copy(showNamedOnly = it)) }
            )
            SettingsSwitch(
                label = "Show Secure Wi-Fi",
                checked = filterSettings.showLockedWifi,
                onCheckedChange = { viewModel.updateFilterSettings(filterSettings.copy(showLockedWifi = it)) }
            )
            SettingsSwitch(
                label = "Show Open Wi-Fi",
                checked = filterSettings.showOpenWifi,
                onCheckedChange = { viewModel.updateFilterSettings(filterSettings.copy(showOpenWifi = it)) }
            )
            SettingsSwitch(
                label = "New Devices Only (Last 30s)",
                checked = filterSettings.showNewOnly,
                onCheckedChange = { viewModel.updateFilterSettings(filterSettings.copy(showNewOnly = it)) }
            )
            SettingsSwitch(
                label = "Tracked Device Only",
                checked = filterSettings.showTrackedOnly,
                onCheckedChange = { viewModel.updateFilterSettings(filterSettings.copy(showTrackedOnly = it)) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("Minimum Signal Strength: ${filterSettings.minSignalStrength} dBm", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = filterSettings.minSignalStrength.toFloat(),
                onValueChange = { viewModel.updateFilterSettings(filterSettings.copy(minSignalStrength = it.toInt())) },
                valueRange = -100f..-30f,
                steps = 70
            )

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(title = "Sonar & Audio Feedback", icon = Icons.Default.VolumeUp)

            SettingsSwitch(
                label = "Sonar Audio Ping (Tactical 880Hz Chirp)",
                checked = generalSettings.sonarSoundEnabled,
                onCheckedChange = { viewModel.updateGeneralSettings(generalSettings.copy(sonarSoundEnabled = it)) }
            )
            SettingsSwitch(
                label = "Map Sonar Epicenter Auto-Follow",
                checked = generalSettings.sonarEpicenterAutoFollow,
                onCheckedChange = { viewModel.updateGeneralSettings(generalSettings.copy(sonarEpicenterAutoFollow = it)) }
            )
            SettingsSwitch(
                label = "Sonar 360° Sweep Beam Animation",
                checked = generalSettings.sonarSweepAnimation,
                onCheckedChange = { viewModel.updateGeneralSettings(generalSettings.copy(sonarSweepAnimation = it)) }
            )
            SettingsSwitch(
                label = "Proximity Geiger Audio Ticks",
                checked = generalSettings.geigerAudioEnabled,
                onCheckedChange = { viewModel.updateGeneralSettings(generalSettings.copy(geigerAudioEnabled = it)) }
            )

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(title = "Spectre Background Daemon", icon = Icons.Default.Sensors)
            
            val isDaemonRunning by viewModel.isDaemonRunning.collectAsState()
            val daemonStatus by viewModel.daemonStatusText.collectAsState()

            Surface(
                color = if (isDaemonRunning) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isDaemonRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isDaemonRunning) "Foreground Sonar Daemon (ACTIVE)" else "Foreground Sonar Daemon (INACTIVE)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = if (isDaemonRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = daemonStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isDaemonRunning,
                            onCheckedChange = { viewModel.toggleDaemon(it) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(title = "Native Integration & Alerts", icon = Icons.Default.Settings)
            
            SettingsSwitch(
                label = "Haptic Feedback (Vibration)",
                checked = generalSettings.hapticFeedback,
                onCheckedChange = { viewModel.updateGeneralSettings(generalSettings.copy(hapticFeedback = it)) }
            )
            SettingsSwitch(
                label = "Flashlight Strobe (Proximity Alert)",
                checked = generalSettings.flashlightAlert,
                onCheckedChange = { viewModel.updateGeneralSettings(generalSettings.copy(flashlightAlert = it)) }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(title = "Persistent Storage & Memory", icon = Icons.Default.Storage)

            Text(
                "Filter configurations, Sonar audio preferences, node aliases, tracking breadcrumbs, and terminal session logs are automatically stored in the local SQLite Room database.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.clearPersistentLogs() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text("CLEAR PERSISTENT TERMINAL & CYBER LOGS")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "Native functions like the vibrator are used to provide 'hotter/colder' physical feedback, and the camera flash can be used as a visual beacon when pinpointing a device's exact location.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun SettingsSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
