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
import com.example.viewmodel.BluetoothTrackerViewModel
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

@Composable
fun MapScreen(viewModel: BluetoothTrackerViewModel) {
    val devices by viewModel.devices.collectAsState()
    val trackingDevice by viewModel.trackingDevice.collectAsState()
    val history by viewModel.deviceHistory.collectAsState()
    
    var boxWidth by remember { mutableStateOf(0f) }
    var boxHeight by remember { mutableStateOf(0f) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Radar Map: Standard BLE cannot determine physical direction without special hardware. Devices are placed circularly for visual separation only. Distance (radius) is real.",
                    style = MaterialTheme.typography.bodySmall,
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
                },
            contentAlignment = Alignment.Center
        ) {
            val padding = 32.dp.value * 2.5f // rough density conversion
            val maxRadius = minOf(boxWidth, boxHeight) / 2f - padding
            val maxDisplayDistance = 30.0

            Canvas(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                
                // Draw radar rings
                for (i in 1..4) {
                    drawCircle(
                        color = Color.Green.copy(alpha = 0.3f),
                        radius = (size.width / 2) * (i / 4f),
                        center = center,
                        style = Stroke(width = 2f)
                    )
                }
                
                // Draw crosshairs
                drawLine(Color.Green.copy(alpha = 0.3f), Offset(center.x, 0f), Offset(center.x, size.height))
                drawLine(Color.Green.copy(alpha = 0.3f), Offset(0f, center.y), Offset(size.width, center.y))
                
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
                    val color = if (isTracked) Color.Yellow else Color.Red
                    
                    drawCircle(
                        color = color,
                        radius = if (isTracked) 20f else 16f,
                        center = Offset(x, y)
                    )
                }
                
                // Draw user
                drawCircle(
                    color = Color.Blue,
                    radius = 24f,
                    center = center
                )
            }
            
            // Draw HTML-like overlay for labels
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
                    
                    Box(modifier = Modifier
                        .offset { IntOffset(x.roundToInt() + 24, y.roundToInt() - 24) }
                    ) {
                        Column {
                            Text(
                                text = device.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isTracked) Color.Yellow else Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f)).padding(2.dp)
                            )
                            Text(
                                text = "${device.rssi} dBm",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f)).padding(2.dp)
                            )
                        }
                    }
                }
            }
            
            Text(
                "You",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color.Blue, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .align(Alignment.Center)
            )
        }
    }
}
