package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Alignment
import com.example.model.DeviceCategory
import com.example.model.TrackedDevice
import com.example.viewmodel.BluetoothTrackerViewModel

@Composable
fun DeviceListScreen(viewModel: BluetoothTrackerViewModel, onNavigateToCompass: () -> Unit) {
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    
    var showAliasDialog by remember { mutableStateOf<TrackedDevice?>(null) }
    
    if (showAliasDialog != null) {
        val device = showAliasDialog!!
        var aliasInput by remember { mutableStateOf(device.customAlias ?: device.name) }
        
        AlertDialog(
            onDismissRequest = { showAliasDialog = null },
            title = { Text("Set Device Alias") },
            text = {
                OutlinedTextField(
                    value = aliasInput,
                    onValueChange = { aliasInput = it },
                    label = { Text("Alias Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setAlias(device.macAddress, aliasInput)
                    showAliasDialog = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAliasDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Nearby Devices",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = if (isScanning) "Scanning..." else "Scan Paused",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
        
        if (devices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No devices found yet.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(devices, key = { it.macAddress }) { device ->
                    DeviceCard(
                        device = device,
                        onEditAlias = { showAliasDialog = device },
                        onClick = {
                            viewModel.trackDevice(device)
                            onNavigateToCompass()
                        }
                    )
                }
            }
        }
    }
}
@Composable
fun DeviceCard(device: TrackedDevice, onEditAlias: () -> Unit, onClick: () -> Unit) {
    val icon = when (device.deviceCategory) {
        DeviceCategory.PHONE -> Icons.Default.Smartphone
        DeviceCategory.COMPUTER -> Icons.Default.Computer
        DeviceCategory.AUDIO_VIDEO -> Icons.Default.Headset
        DeviceCategory.WEARABLE -> Icons.Default.Watch
        DeviceCategory.CAMERA -> Icons.Default.CameraAlt
        DeviceCategory.HEALTH -> Icons.Default.Favorite
        DeviceCategory.PERIPHERAL -> Icons.Default.Keyboard
        else -> Icons.Default.Bluetooth
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("device_card_${device.macAddress}"),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Device Type",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp).padding(end = 8.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onEditAlias, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Name", modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    text = device.macAddress + if (device.isConnectable) " (Connectable)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text(
                    text = "${device.rssi} dBm",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (device.rssi > -60) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = String.format("%.1f m", device.distanceMeters),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
