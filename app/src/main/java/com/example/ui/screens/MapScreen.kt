package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Keyboard
import com.example.model.DeviceCategory
import com.example.model.TrackedDevice
import com.example.model.SignalType
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Bolt
import androidx.navigation.NavController
import com.example.viewmodel.BluetoothTrackerViewModel
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

@Composable
fun MapScreen(viewModel: BluetoothTrackerViewModel, navController: NavController) {
    val devices by viewModel.devices.collectAsState()
    val trackingDevice by viewModel.trackingDevice.collectAsState()
    val history by viewModel.deviceHistory.collectAsState()
    
    var boxWidth by remember { mutableStateOf(0f) }
    var boxHeight by remember { mutableStateOf(0f) }
    var zoomScale by remember { mutableStateOf(1f) }
    var selectedDeviceMac by remember { mutableStateOf<String?>(null) }
    
    val selectedDevice = devices.find { it.macAddress == selectedDeviceMac }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Radar Map (Pinch to Zoom)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .onGloballyPositioned { coordinates -> 
                        boxWidth = coordinates.size.width.toFloat()
                        boxHeight = coordinates.size.height.toFloat()
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(0.5f, 5f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val padding = 32.dp.value * 2.5f
                val baseMaxRadius = minOf(boxWidth, boxHeight) / 2f - padding
                val maxRadius = baseMaxRadius * zoomScale
                val maxDisplayDistance = 30.0

                Canvas(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                    val center = Offset(size.width / 2, size.height / 2)
                    
                    // Draw radar rings
                    for (i in 1..4) {
                        drawCircle(
                            color = Color.Green.copy(alpha = 0.3f),
                            radius = maxRadius * (i / 4f),
                            center = center,
                            style = Stroke(width = 2f)
                        )
                    }
                    
                    // Draw crosshairs
                    drawLine(Color.Green.copy(alpha = 0.3f), Offset(center.x, center.y - maxRadius), Offset(center.x, center.y + maxRadius))
                    drawLine(Color.Green.copy(alpha = 0.3f), Offset(center.x - maxRadius, center.y), Offset(center.x + maxRadius, center.y))
                    
                    // History trails
                    if (trackingDevice != null && history.isNotEmpty()) {
                        val path = Path()
                        var first = true
                        history.reversed().forEach { loc ->
                            val distanceRatio = (loc.distanceMeters / maxDisplayDistance).coerceIn(0.0, 1.0)
                            val r = maxRadius * distanceRatio
                            val visualAngle = (loc.macAddress.hashCode() and 0x7FFFFFFF) % 360f
                            val angleRad = Math.toRadians(visualAngle.toDouble()) - Math.PI / 2
                            
                            val x = center.x + (r * cos(angleRad)).toFloat()
                            val y = center.y + (r * sin(angleRad)).toFloat()
                            
                            if (first) {
                                path.moveTo(x, y)
                                first = false
                            } else {
                                path.lineTo(x, y)
                            }
                        }
                        
                        drawPath(
                            path = path,
                            color = Color.Yellow.copy(alpha = 0.6f),
                            style = Stroke(width = 4f)
                        )
                    }
                    
                    // Draw current devices dots
                    devices.forEach { device ->
                        val distanceRatio = (device.distanceMeters / maxDisplayDistance).coerceIn(0.0, 1.0)
                        val r = maxRadius * distanceRatio
                        val visualAngle = (device.macAddress.hashCode() and 0x7FFFFFFF) % 360f
                        val angleRad = Math.toRadians(visualAngle.toDouble()) - Math.PI / 2
                        
                        val x = center.x + (r * cos(angleRad)).toFloat()
                        val y = center.y + (r * sin(angleRad)).toFloat()
                        
                        val isTracked = trackingDevice?.macAddress == device.macAddress
                        val isSelected = selectedDevice?.macAddress == device.macAddress
                        val color = when {
                            isSelected -> Color.Cyan
                            isTracked -> Color.Yellow
                            device.signalType == SignalType.WIFI -> Color.Green
                            device.signalType == SignalType.EMF -> Color(0xFF9C27B0) // Purple
                            else -> Color.Red
                        }
                        
                        drawCircle(
                            color = color,
                            radius = if (isTracked || isSelected) 20f else 16f,
                            center = Offset(x, y)
                        )
                    }
                }

                // Draw labels and click targets
                if (boxWidth > 0 && boxHeight > 0) {
                    val center = Offset(boxWidth / 2, boxHeight / 2)
                    
                    devices.forEach { device ->
                        val distanceRatio = (device.distanceMeters / maxDisplayDistance).coerceIn(0.0, 1.0)
                        val r = maxRadius * distanceRatio
                        val visualAngle = (device.macAddress.hashCode() and 0x7FFFFFFF) % 360f
                        val angleRad = Math.toRadians(visualAngle.toDouble()) - Math.PI / 2
                        
                        val x = center.x + (r * cos(angleRad)).toFloat()
                        val y = center.y + (r * sin(angleRad)).toFloat()
                        
                        val isTracked = trackingDevice?.macAddress == device.macAddress
                        val isSelected = selectedDevice?.macAddress == device.macAddress
                        
                        val labelColor = when {
                            isSelected -> Color.Cyan
                            isTracked -> Color.Yellow
                            device.signalType == SignalType.WIFI -> Color.Green
                            device.signalType == SignalType.EMF -> Color(0xFFE1BEE7) // Light Purple
                            else -> Color.White
                        }
                        
                        // Click target
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .offset { IntOffset(x.roundToInt() - 24, y.roundToInt() - 24) }
                                .clickable { selectedDeviceMac = device.macAddress }
                        )

                        // Label
                        Box(modifier = Modifier
                            .offset { 
                                // Dynamic alignment to prevent clipping
                                val xOff = if (x > boxWidth / 2) -120 else 24
                                val yOff = -24
                                IntOffset(x.roundToInt() + xOff, y.roundToInt() + yOff) 
                            }
                            .width(100.dp)
                        ) {
                            Column(modifier = Modifier.background(Color.Black.copy(alpha = 0.6f)).padding(2.dp)) {
                                Text(
                                    text = device.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = labelColor,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${device.rssi} dBm",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Side Panel (Inspector)
        if (selectedDevice != null) {
            Surface(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Device Inspector",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { selectedDeviceMac = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val device = selectedDevice!!
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

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    InspectorProperty("Name", device.displayName)
                    InspectorProperty("MAC Address", device.macAddress)
                    InspectorProperty("Vendor", device.vendor)
                    InspectorProperty("Distance", String.format("%.2f meters", device.distanceMeters))
                    InspectorProperty("Signal Strength", "${device.rssi} dBm")
                    InspectorProperty("Tx Power", "${device.txPower} dBm")
                    InspectorProperty("Connectable", device.isConnectable.toString())
                    InspectorProperty("Category", device.deviceCategory.name)
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Button(
                        onClick = { 
                            viewModel.trackDevice(device)
                            navController.navigate("compass") {
                                launchSingleTop = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Track this Device")
                    }
                }
            }
        }
    }
}

@Composable
fun InspectorProperty(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Divider(modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}
