package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.model.DeviceCategory
import com.example.model.SignalType
import com.example.model.TrackedDevice
import com.example.ui.theme.*
import com.example.viewmodel.BluetoothTrackerViewModel

@Composable
fun DeviceListScreen(
    viewModel: BluetoothTrackerViewModel,
    navController: NavController,
    onNavigateToCompass: () -> Unit
) {
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val trackingDevice by viewModel.trackingDevice.collectAsState()

    var showAliasDialog by remember { mutableStateOf<TrackedDevice?>(null) }

    if (showAliasDialog != null) {
        val device = showAliasDialog!!
        var aliasInput by remember { mutableStateOf(device.customAlias ?: device.name) }

        AlertDialog(
            onDismissRequest = { showAliasDialog = null },
            containerColor = OniDarkSurface,
            title = {
                Text(
                    "ASSIGN TARGET ALIAS",
                    color = OniNeonBlue,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = aliasInput,
                    onValueChange = { aliasInput = it },
                    label = { Text("Node Identifier", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OniNeonBlue,
                        unfocusedBorderColor = OniDarkBorder,
                        cursorColor = OniNeonBlue
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setAlias(device.macAddress, aliasInput)
                        showAliasDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OniNeonBlue)
                ) {
                    Text("SAVE", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAliasDialog = null }) {
                    Text("CANCEL", color = Color.LightGray, fontFamily = FontFamily.Monospace)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // TOP HUD BAR
        Surface(
            color = OniDarkSurface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, OniDarkBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(if (isScanning) OniNeonGreen else OniAmber, shape = RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DISCOVERED NODES",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = OniNeonBlue,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = if (isScanning) "ACTIVE SONAR & RF HARVESTING..." else "SCAN IDLE",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Surface(
                    color = Color.Black,
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OniNeonGreen)
                ) {
                    Text(
                        text = "${devices.size} ACTIVE",
                        color = OniNeonGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        if (devices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = OniNeonBlue, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "SCANNING LOCAL SPECTRUM (BLE / WI-FI / EMF)...",
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(devices, key = { it.macAddress }) { device ->
                    val isTracked = trackingDevice?.macAddress == device.macAddress
                    DeviceCard(
                        device = device,
                        isTracked = isTracked,
                        onEditAlias = { showAliasDialog = device },
                        onCyberDeck = {
                            viewModel.selectCyberTarget(device)
                            navController.navigate("cyberdeck") { launchSingleTop = true }
                        },
                        onTrack = {
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
fun DeviceCard(
    device: TrackedDevice,
    isTracked: Boolean,
    onEditAlias: () -> Unit,
    onCyberDeck: () -> Unit,
    onTrack: () -> Unit
) {
    val icon = when (device.signalType) {
        SignalType.WIFI -> Icons.Default.Wifi
        SignalType.EMF -> Icons.Default.Bolt
        SignalType.BLUETOOTH -> when (device.deviceCategory) {
            DeviceCategory.PHONE -> Icons.Default.Smartphone
            DeviceCategory.COMPUTER -> Icons.Default.Computer
            DeviceCategory.AUDIO_VIDEO -> Icons.Default.Headset
            DeviceCategory.WEARABLE -> Icons.Default.Watch
            DeviceCategory.CAMERA -> Icons.Default.CameraAlt
            DeviceCategory.HEALTH -> Icons.Default.Favorite
            DeviceCategory.PERIPHERAL -> Icons.Default.Keyboard
            DeviceCategory.WIFI_ROUTER -> Icons.Default.Wifi
            else -> Icons.Default.Bluetooth
        }
    }

    val iconColor = when (device.signalType) {
        SignalType.WIFI -> OniNeonGreen
        SignalType.EMF -> Color(0xFFE040FB)
        else -> OniNeonBlue
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isTracked) OniDarkRed else OniDarkBorder,
                RoundedCornerShape(8.dp)
            )
            .testTag("device_card_${device.macAddress}"),
        colors = CardDefaults.cardColors(containerColor = OniSurfaceVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.Black, RoundedCornerShape(6.dp))
                            .border(1.dp, iconColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = device.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                            IconButton(onClick = onEditAlias, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(14.dp))
                            }
                        }

                        Text(
                            text = "${device.macAddress} • ${device.vendor}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OniNeonBlueVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${device.rssi} dBm",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (device.rssi > -65) OniNeonGreen else OniAmber,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "~${String.format("%.1f", device.distanceMeters)}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCyberDeck,
                    colors = ButtonDefaults.buttonColors(containerColor = OniNeonBlue),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f).height(34.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("HACK / CYBER", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }

                Button(
                    onClick = onTrack,
                    colors = ButtonDefaults.buttonColors(containerColor = if (isTracked) OniDarkRed else OniDarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isTracked) OniDarkRed else OniDarkBorder),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f).height(34.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Explore, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isTracked) "LOCKED" else "TRACK LOCK", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
