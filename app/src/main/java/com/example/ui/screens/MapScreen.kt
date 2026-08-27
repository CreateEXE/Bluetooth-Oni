package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.model.DeviceCategory
import com.example.model.SignalType
import com.example.model.TrackedDevice
import com.example.ui.theme.*
import com.example.viewmodel.BluetoothTrackerViewModel
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun MapScreen(viewModel: BluetoothTrackerViewModel, navController: NavController) {
    val devices by viewModel.devices.collectAsState()
    val trackingDevice by viewModel.trackingDevice.collectAsState()
    val history by viewModel.deviceHistory.collectAsState()
    val generalSettings by viewModel.generalSettings.collectAsState()

    var boxWidth by remember { mutableStateOf(0f) }
    var boxHeight by remember { mutableStateOf(0f) }
    var zoomScale by remember { mutableStateOf(1f) }
    var selectedDeviceMac by remember { mutableStateOf<String?>(null) }

    val selectedDevice = devices.find { it.macAddress == selectedDeviceMac }

    // Sonar Beam Sweep Animation
    val infiniteTransition = rememberInfiniteTransition(label = "radarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    // Sonar Wave Ripple Pulse
    val rippleProgress by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(OniNeonGreen, shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TACTICAL SONAR PPI // 360°",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = OniNeonGreen,
                            letterSpacing = 1.sp
                        )
                    }

                    Text(
                        text = "ZOOM: ${String.format("%.1f", zoomScale)}x | ${devices.size} NODES",
                        color = OniNeonBlueVariant,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // RADAR DISPLAY CANVAS CONTAINER
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .onGloballyPositioned { coordinates ->
                        boxWidth = coordinates.size.width.toFloat()
                        boxHeight = coordinates.size.height.toFloat()
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(0.5f, 4f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val padding = 32.dp.value * 2f
                val baseMaxRadius = minOf(boxWidth, boxHeight) / 2f - padding
                val maxRadius = (baseMaxRadius * zoomScale).coerceAtLeast(100f)
                val maxDisplayDistance = 30.0

                Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    val center = Offset(size.width / 2, size.height / 2)

                    // Concentric Tactical Distance Rings
                    val ringDistances = listOf("7.5m", "15m", "22.5m", "30m")
                    for (i in 1..4) {
                        val r = maxRadius * (i / 4f)
                        drawCircle(
                            color = OniNeonGreen.copy(alpha = 0.2f),
                            radius = r,
                            center = center,
                            style = Stroke(width = 1.5f)
                        )
                    }

                    // Tactical Crosshairs and 45° Diagonals
                    drawLine(
                        color = OniNeonGreen.copy(alpha = 0.25f),
                        start = Offset(center.x, center.y - maxRadius),
                        end = Offset(center.x, center.y + maxRadius),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = OniNeonGreen.copy(alpha = 0.25f),
                        start = Offset(center.x - maxRadius, center.y),
                        end = Offset(center.x + maxRadius, center.y),
                        strokeWidth = 1f
                    )

                    // Expanding Sonar Ping Wave
                    drawCircle(
                        color = OniNeonGreen.copy(alpha = (1f - rippleProgress) * 0.4f),
                        radius = maxRadius * rippleProgress,
                        center = center,
                        style = Stroke(width = 2.5f)
                    )

                    // 360° Rotating Sonar Sweep Beam with Phosphor Fade
                    rotate(degrees = sweepAngle, pivot = center) {
                        val beamPath = Path().apply {
                            moveTo(center.x, center.y)
                            val angleRad = Math.toRadians(40.0)
                            lineTo(
                                center.x + (maxRadius * cos(-angleRad)).toFloat(),
                                center.y + (maxRadius * sin(-angleRad)).toFloat()
                            )
                            arcTo(
                                rect = androidx.compose.ui.geometry.Rect(
                                    center.x - maxRadius,
                                    center.y - maxRadius,
                                    center.x + maxRadius,
                                    center.y + maxRadius
                                ),
                                startAngleDegrees = -40f,
                                sweepAngleDegrees = 40f,
                                forceMoveTo = false
                            )
                            close()
                        }

                        drawPath(
                            path = beamPath,
                            color = OniNeonGreen.copy(alpha = 0.15f)
                        )
                        drawLine(
                            color = OniNeonGreen,
                            start = center,
                            end = Offset(center.x + maxRadius, center.y),
                            strokeWidth = 2.5f
                        )
                    }

                    // Historic Tracking Trail
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
                            color = OniAmber.copy(alpha = 0.6f),
                            style = Stroke(width = 3f)
                        )
                    }

                    // User Epicenter Center Point
                    drawCircle(
                        color = OniNeonGreen,
                        radius = 6f,
                        center = center
                    )
                    drawCircle(
                        color = OniNeonGreen.copy(alpha = 0.5f),
                        radius = 12f,
                        center = center,
                        style = Stroke(width = 1.5f)
                    )

                    // Target Blip Points
                    devices.forEach { device ->
                        val distanceRatio = (device.distanceMeters / maxDisplayDistance).coerceIn(0.0, 1.0)
                        val r = maxRadius * distanceRatio
                        val visualAngle = (device.macAddress.hashCode() and 0x7FFFFFFF) % 360f
                        val angleRad = Math.toRadians(visualAngle.toDouble()) - Math.PI / 2

                        val x = center.x + (r * cos(angleRad)).toFloat()
                        val y = center.y + (r * sin(angleRad)).toFloat()

                        val isTracked = trackingDevice?.macAddress == device.macAddress
                        val isSelected = selectedDevice?.macAddress == device.macAddress

                        val blipColor = when {
                            isSelected -> OniNeonBlue
                            isTracked -> OniDarkRed
                            device.signalType == SignalType.WIFI -> OniNeonGreen
                            device.signalType == SignalType.EMF -> Color(0xFFE040FB)
                            else -> OniNeonBlueVariant
                        }

                        // Outer Glow Ring
                        drawCircle(
                            color = blipColor.copy(alpha = 0.4f),
                            radius = if (isTracked || isSelected) 18f else 12f,
                            center = Offset(x, y),
                            style = Stroke(width = 2f)
                        )
                        // Inner Solid Blip
                        drawCircle(
                            color = blipColor,
                            radius = if (isTracked || isSelected) 8f else 6f,
                            center = Offset(x, y)
                        )
                    }
                }

                // Interactive Click Targets & HUD Labels
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

                        // Click target
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .offset { IntOffset(x.roundToInt() - 24, y.roundToInt() - 24) }
                                .clickable {
                                    selectedDeviceMac = device.macAddress
                                    if (generalSettings.sonarSoundEnabled) {
                                        viewModel.triggerSonarAudioPing()
                                    }
                                }
                        )

                        // Label
                        Box(
                            modifier = Modifier
                                .offset {
                                    val xOff = if (x > boxWidth / 2) -110 else 16
                                    val yOff = -20
                                    IntOffset(x.roundToInt() + xOff, y.roundToInt() + yOff)
                                }
                                .width(105.dp)
                        ) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(3.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) OniNeonBlue else if (isTracked) OniDarkRed else OniDarkBorder
                                )
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                                    Text(
                                        text = device.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) OniNeonBlue else if (isTracked) OniDarkRed else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "${device.rssi}dBm | ${String.format("%.1f", device.distanceMeters)}m",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OniNeonGreen,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // TACTICAL NODE INSPECTOR DRAWER
        AnimatedVisibility(
            visible = selectedDevice != null,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            if (selectedDevice != null) {
                val dev = selectedDevice!!
                Surface(
                    color = OniDarkSurface,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OniNeonBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = dev.displayName.uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "MAC: ${dev.macAddress} | VENDOR: ${dev.vendor}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OniNeonBlueVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            IconButton(onClick = { selectedDeviceMac = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "RSSI: ${dev.rssi} dBm",
                                color = if (dev.rssi > -65) OniNeonGreen else OniAmber,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "EST. DISTANCE: ${String.format("%.1f", dev.distanceMeters)}m",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "CLASS: ${dev.deviceCategory.name}",
                                color = OniNeonBlueVariant,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.selectCyberTarget(dev)
                                    navController.navigate("cyberdeck") { launchSingleTop = true }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = OniNeonBlue),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f).height(40.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("HACK / CYBER", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }

                            Button(
                                onClick = {
                                    viewModel.trackDevice(dev)
                                    navController.navigate("compass") { launchSingleTop = true }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = OniDarkRed),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f).height(40.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("TARGET LOCK", fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }

                            Button(
                                onClick = {
                                    viewModel.triggerSonarAudioPing()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = OniSurfaceVariant),
                                border = androidx.compose.foundation.BorderStroke(1.dp, OniNeonGreen),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f).height(40.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = OniNeonGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PING", color = OniNeonGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    }
}
