package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.AlertSettings
import com.example.model.AlertType
import com.example.viewmodel.BluetoothTrackerViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CompassScreen(viewModel: BluetoothTrackerViewModel) {
    val trackingDevice by viewModel.trackingDevice.collectAsState()
    val userAzimuth by viewModel.userAzimuth.collectAsState()
    val userPitch by viewModel.userPitch.collectAsState()
    val userRoll by viewModel.userRoll.collectAsState()
    
    var showSettingsDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (trackingDevice == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "No device",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No device selected to track.",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Go to the list and tap a device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val target = trackingDevice!!
            
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Tracking: ${target.displayName}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "Distance: ${String.format("%.1f m", target.distanceMeters)} (${target.rssi} dBm)",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Alert Settings")
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Dynamic 3D Gyroscope Compass
            val proximityRatio = (1.0 - (target.distanceMeters / 30.0)).coerceIn(0.0, 1.0).toFloat()
            val primaryColor = MaterialTheme.colorScheme.primary
            val secondaryColor = MaterialTheme.colorScheme.secondary
            
            // Smooth the orientation changes slightly for better visual effect
            val smoothPitch by animateFloatAsState(targetValue = userPitch, animationSpec = tween(100), label = "pitch")
            val smoothRoll by animateFloatAsState(targetValue = userRoll, animationSpec = tween(100), label = "roll")
            val smoothAzimuth by animateFloatAsState(targetValue = userAzimuth, animationSpec = tween(100), label = "azimuth")

            Box(
                modifier = Modifier
                    .size(300.dp)
                    .graphicsLayer {
                        rotationX = -smoothPitch // Pitch up/down
                        rotationY = smoothRoll // Roll left/right
                        rotationZ = -smoothAzimuth // Yaw rotation (compass direction)
                        cameraDistance = 12f * density
                    },
                contentAlignment = Alignment.Center
            ) {
                // Compass Base / 3D structural rings
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    
                    // Outer structural ring
                    drawCircle(
                        color = Color.DarkGray.copy(alpha = 0.8f),
                        radius = size.width / 2,
                        center = center,
                        style = Stroke(width = 8f)
                    )
                    
                    // Inner structural ring
                    drawCircle(
                        color = Color.Gray.copy(alpha = 0.4f),
                        radius = (size.width / 2) * 0.85f,
                        center = center,
                        style = Stroke(width = 4f)
                    )
                    
                    // Crosshairs
                    drawLine(Color.Gray.copy(alpha = 0.3f), Offset(center.x, 0f), Offset(center.x, size.height), strokeWidth = 2f)
                    drawLine(Color.Gray.copy(alpha = 0.3f), Offset(0f, center.y), Offset(size.width, center.y), strokeWidth = 2f)
                    
                    // Signal strength indicator (pulsing center sphere/disc)
                    val maxRadius = (size.width / 2) * 0.7f
                    val currentRadius = maxRadius * proximityRatio
                    
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.6f),
                        radius = currentRadius,
                        center = center
                    )
                    
                    // Core point
                    drawCircle(
                        color = secondaryColor,
                        radius = 16f,
                        center = center
                    )
                }
                
                // Floating indicators in 3D space
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Float above the base visually by scaling or just letting it render on top
                            scaleX = 1.1f
                            scaleY = 1.1f
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2, size.height / 2)
                        
                        // Compass North pointer
                        val path = Path().apply {
                            moveTo(center.x, center.y - (size.width / 2) * 0.9f)
                            lineTo(center.x + 20f, center.y)
                            lineTo(center.x - 20f, center.y)
                            close()
                        }
                        
                        drawPath(
                            path = path,
                            color = Color.Red.copy(alpha = 0.8f)
                        )
                        
                        // South pointer
                        val southPath = Path().apply {
                            moveTo(center.x, center.y + (size.width / 2) * 0.9f)
                            lineTo(center.x + 20f, center.y)
                            lineTo(center.x - 20f, center.y)
                            close()
                        }
                        
                        drawPath(
                            path = southPath,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(64.dp))
            
            Text(
                "Standard BLE cannot determine physical direction.\nThe 3D compass tracks your phone's gyroscope.\nUse the expanding proximity pulse to find the device (Hot/Cold).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.weight(1f))
        }
    }
    
    if (showSettingsDialog) {
        AlertSettingsDialog(viewModel = viewModel, onDismiss = { showSettingsDialog = false })
    }
}

@Composable
fun AlertSettingsDialog(viewModel: BluetoothTrackerViewModel, onDismiss: () -> Unit) {
    val trackingDevice by viewModel.trackingDevice.collectAsState()
    val deviceAlertSettings by viewModel.deviceAlertSettings.collectAsState()
    val macAddress = trackingDevice?.macAddress ?: return
    
    val currentSettings = deviceAlertSettings[macAddress] ?: AlertSettings()
    
    var enabled by remember { mutableStateOf(currentSettings.enabled) }
    var distance by remember { mutableStateOf(currentSettings.distanceThresholdMeters.toFloat()) }
    var alertType by remember { mutableStateOf(currentSettings.alertType) }
    var isHostageMode by remember { mutableStateOf(currentSettings.isHostageMode) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anti-Loss Security Perimeter") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enabled, onCheckedChange = { enabled = it })
                    Text("Enable Perimeter Monitoring")
                }
                
                if (enabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Distance Threshold: ${String.format("%.1f", distance)}m")
                    Slider(
                        value = distance,
                        onValueChange = { distance = it },
                        valueRange = 1f..30f
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Trigger when:")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = alertType == AlertType.IN_RANGE,
                            onClick = { alertType = AlertType.IN_RANGE }
                        )
                        Text("In Range")
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(
                            selected = alertType == AlertType.OUT_OF_RANGE,
                            onClick = { alertType = AlertType.OUT_OF_RANGE }
                        )
                        Text("Out of Range")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = isHostageMode, onCheckedChange = { isHostageMode = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sound Alarm & Auto-Track (Killswitch)", 
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isHostageMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.updateAlertSettings(macAddress, AlertSettings(enabled, distance.toDouble(), alertType, isHostageMode))
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
