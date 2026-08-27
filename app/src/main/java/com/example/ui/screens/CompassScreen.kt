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
    val estimatedBearings by viewModel.estimatedBearings.collectAsState()
    val emfStrength by viewModel.emfFieldStrength.collectAsState()
    val inertialSteps by viewModel.inertialSteps.collectAsState()

    
    var showSettingsDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (trackingDevice == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No device selected for tracking.\nGo to the list to select a device.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
            )
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
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Inertial SLAM & Magnetic Sensor",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Pedometer Map", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$inertialSteps Steps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("EMF Detector", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${String.format("%.1f", emfStrength)} µT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (emfStrength > 100f) Color.Red else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (emfStrength > 100f) {
                        Text("⚠️ STRONG METALLIC/MAGNETIC SIGNATURE DETECTED", style = MaterialTheme.typography.labelSmall, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            
            val proximityRatio = (1.0 - (target.distanceMeters / 30.0)).coerceIn(0.0, 1.0).toFloat()
            val primaryColor = MaterialTheme.colorScheme.primary
            val secondaryColor = MaterialTheme.colorScheme.secondary
            
            val smoothPitch by animateFloatAsState(targetValue = userPitch, animationSpec = tween(100), label = "pitch")
            val smoothRoll by animateFloatAsState(targetValue = userRoll, animationSpec = tween(100), label = "roll")
            val smoothAzimuth by animateFloatAsState(targetValue = userAzimuth, animationSpec = tween(100), label = "azimuth")
            
            val estimatedBearing = estimatedBearings[target.macAddress]

            Box(
                modifier = Modifier
                    .size(300.dp)
                    .graphicsLayer {
                        rotationX = -smoothPitch // Pitch up/down
                        rotationY = smoothRoll // Roll left/right
                        // Point the whole canvas if we have a bearing, otherwise just North
                        rotationZ = if (estimatedBearing != null) {
                            estimatedBearing - smoothAzimuth
                        } else {
                            -smoothAzimuth
                        }
                        cameraDistance = 12f * density
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    
                    if (estimatedBearing != null) {
                        // Drawing a large stylized arrow pointing Up (which gets rotated by the Box to target)
                        val arrowPath = Path().apply {
                            moveTo(center.x, center.y - 120f) // Tip
                            lineTo(center.x + 70f, center.y + 90f) // Bottom Right
                            lineTo(center.x, center.y + 50f) // Inner Bottom
                            lineTo(center.x - 70f, center.y + 90f) // Bottom Left
                            close()
                        }
                        
                        // Drop shadow / glow
                        drawPath(
                            path = arrowPath,
                            color = primaryColor.copy(alpha = 0.4f),
                            style = Stroke(width = 20f)
                        )
                        
                        drawPath(
                            path = arrowPath,
                            color = primaryColor
                        )
                    } else {
                        // Indeterminate/Scanning stylized compass
                        val maxRadius = (size.width / 2) * 0.7f
                        val currentRadius = maxRadius * proximityRatio
                        
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.5f),
                            radius = currentRadius,
                            center = center
                        )
                        
                        drawCircle(
                            color = primaryColor,
                            radius = currentRadius,
                            center = center,
                            style = Stroke(width = 4f)
                        )
                        
                        val path = Path().apply {
                            moveTo(center.x, center.y - currentRadius - 30f)
                            lineTo(center.x + 20f, center.y - currentRadius - 10f)
                            lineTo(center.x - 20f, center.y - currentRadius - 10f)
                            close()
                        }
                        
                        drawPath(
                            path = path,
                            color = Color.Red.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(64.dp))
            
            val isEstimated = estimatedBearings.containsKey(target.macAddress)
            val instructionText = if (isEstimated) {
                "Distance stabilized. 3D arrow points in the estimated direction based on your movement.\nPro Tip: Use \"Body Shadowing\" (hold phone to chest and turn around). The arrow glows brighter when your body isn't blocking the signal."
            } else {
                "Distance stabilized with Kalman filter.\nWalk around slowly to calibrate the tracking algorithm.\nPro Tip: Use \"Body Shadowing\" (hold phone to chest and turn around). The compass pulse is strongest when facing the device."
            }
            Text(
                instructionText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
                textAlign = TextAlign.Center
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Inertial SLAM & Magnetic Sensor",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Pedometer Map", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$inertialSteps Steps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("EMF Detector", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${String.format("%.1f", emfStrength)} µT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (emfStrength > 100f) Color.Red else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (emfStrength > 100f) {
                        Text("⚠️ STRONG METALLIC/MAGNETIC SIGNATURE DETECTED", style = MaterialTheme.typography.labelSmall, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
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
                            text = "Sound Alarm & Auto-Track", 
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
