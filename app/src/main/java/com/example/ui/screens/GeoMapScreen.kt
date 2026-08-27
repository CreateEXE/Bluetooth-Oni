package com.example.ui.screens

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.example.model.DeviceCategory
import com.example.model.SignalType
import com.example.model.TrackedDevice
import com.example.ui.theme.*
import com.example.viewmodel.BluetoothTrackerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GeoMapScreen(viewModel: BluetoothTrackerViewModel, navController: NavController) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val userAzimuth by viewModel.userAzimuth.collectAsState()
    val generalSettings by viewModel.generalSettings.collectAsState()
    val scope = rememberCoroutineScope()

    var sonarLockEnabled by remember { mutableStateOf(generalSettings.sonarEpicenterAutoFollow) }
    var sonarSweepVisible by remember { mutableStateOf(generalSettings.sonarSweepAnimation) }
    var selectedDevice by remember { mutableStateOf<TrackedDevice?>(null) }
    var lastCenteredLocation by remember { mutableStateOf<GeoPoint?>(null) }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(17.5)

            // Apply high-contrast dark cyberpunk matrix filter to map tiles
            val matrix = ColorMatrix(
                floatArrayOf(
                    -0.85f, 0f, 0f, 0f, 220f,
                    0f, -0.85f, 0f, 0f, 240f,
                    0f, 0f, -0.85f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(matrix))
        }
    }

    DisposableEffect(mapView) {
        onDispose {
            mapView.onDetach()
        }
    }

    // Continuous Real-Time Epicenter Auto-Centering
    LaunchedEffect(currentLocation, sonarLockEnabled) {
        currentLocation?.let { loc ->
            val userGeoPoint = GeoPoint(loc.latitude, loc.longitude)
            if (sonarLockEnabled) {
                mapView.controller.animateTo(userGeoPoint)
                lastCenteredLocation = userGeoPoint
                if (generalSettings.sonarSoundEnabled) {
                    viewModel.triggerSonarAudioPing()
                }
            } else if (lastCenteredLocation == null) {
                mapView.controller.setCenter(userGeoPoint)
                lastCenteredLocation = userGeoPoint
            }
        }
    }

    // Sonar Sweep Line Rotation Animation
    val infiniteTransition = rememberInfiniteTransition(label = "sonarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    // Sonar Wave Ripple Expanding Pulse Animation
    val wavePulseProgress by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePulse"
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // BASE MAP LAYER
        AndroidView(
            factory = {
                mapView.apply {
                    val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this)
                    locationOverlay.enableMyLocation()
                    overlays.add(locationOverlay)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                // Clean non-persistent markers
                val nonMarkerOverlays = view.overlays.filter { it !is Marker && it !is FolderOverlay }
                view.overlays.clear()
                view.overlays.addAll(nonMarkerOverlays)

                // Add tactical device nodes
                devices.forEach { device ->
                    if (device.latitude != null && device.longitude != null) {
                        val marker = Marker(view)
                        marker.position = GeoPoint(device.latitude, device.longitude)
                        marker.title = device.displayName

                        val lastSeenSec = (System.currentTimeMillis() - device.lastSeenTimestamp) / 1000
                        val status = if (lastSeenSec < 10) "LIVE" else "${lastSeenSec}s AGO"
                        marker.snippet = "[$status] RSSI: ${device.rssi} dBm | ~${String.format("%.1f", device.distanceMeters)}m"

                        val iconRes = when (device.signalType) {
                            SignalType.WIFI -> android.R.drawable.presence_online
                            SignalType.EMF -> android.R.drawable.ic_dialog_alert
                            else -> android.R.drawable.presence_invisible
                        }
                        marker.icon = context.getDrawable(iconRes)
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                        marker.setOnMarkerClickListener { m, _ ->
                            selectedDevice = device
                            m.showInfoWindow()
                            true
                        }

                        view.overlays.add(marker)
                    }
                }
                view.invalidate()
            }
        )

        // REAL-TIME TACTICAL SONAR EPICENTER OVERLAY
        if (sonarSweepVisible) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = minOf(size.width, size.height) * 0.44f

                // Concentric Sonar Range Rings
                for (i in 1..4) {
                    val r = maxRadius * (i / 4f)
                    drawCircle(
                        color = OniNeonBlue.copy(alpha = 0.22f),
                        radius = r,
                        center = center,
                        style = Stroke(width = 1.5f)
                    )
                }

                // Crosshair Reticles
                drawLine(
                    color = OniNeonBlue.copy(alpha = 0.25f),
                    start = Offset(center.x, center.y - maxRadius),
                    end = Offset(center.x, center.y + maxRadius),
                    strokeWidth = 1f
                )
                drawLine(
                    color = OniNeonBlue.copy(alpha = 0.25f),
                    start = Offset(center.x - maxRadius, center.y),
                    end = Offset(center.x + maxRadius, center.y),
                    strokeWidth = 1f
                )

                // Expanding Sonar Ripple Wave
                drawCircle(
                    color = OniNeonGreen.copy(alpha = (1.0f - wavePulseProgress) * 0.45f),
                    radius = maxRadius * wavePulseProgress,
                    center = center,
                    style = Stroke(width = 2.5f)
                )

                // Rotating Sonar Beam Sweep
                rotate(degrees = sweepAngle, pivot = center) {
                    val beamPath = Path().apply {
                        moveTo(center.x, center.y)
                        val angleRad = Math.toRadians(35.0)
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
                            startAngleDegrees = -35f,
                            sweepAngleDegrees = 35f,
                            forceMoveTo = false
                        )
                        close()
                    }

                    drawPath(
                        path = beamPath,
                        color = OniNeonGreen.copy(alpha = 0.18f)
                    )
                    drawLine(
                        color = OniNeonGreen.copy(alpha = 0.85f),
                        start = center,
                        end = Offset(center.x + maxRadius, center.y),
                        strokeWidth = 2f
                    )
                }

                // Epicenter Center Node Dot
                drawCircle(
                    color = OniNeonGreen,
                    radius = 6f,
                    center = center
                )
                drawCircle(
                    color = OniNeonGreen.copy(alpha = 0.4f),
                    radius = 12f,
                    center = center,
                    style = Stroke(width = 2f)
                )
            }
        }

        // TOP TACTICAL HUD TELEMETRY BAR
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .align(Alignment.TopCenter),
            color = OniDarkSurface.copy(alpha = 0.92f),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, OniNeonBlue.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Explore, contentDescription = null, tint = OniNeonGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SONAR EPICENTER MAP",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = OniNeonBlue
                        )
                    }

                    Surface(
                        color = if (sonarLockEnabled) OniNeonGreen.copy(alpha = 0.2f) else Color.DarkGray.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (sonarLockEnabled) OniNeonGreen else Color.Gray
                        ),
                        modifier = Modifier.clickable { sonarLockEnabled = !sonarLockEnabled }
                    ) {
                        Text(
                            text = if (sonarLockEnabled) "EPICENTER LOCKED" else "FREE PAN",
                            color = if (sonarLockEnabled) OniNeonGreen else Color.LightGray,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val lat = currentLocation?.latitude?.let { String.format("%.5f", it) } ?: "--.-----"
                    val lon = currentLocation?.longitude?.let { String.format("%.5f", it) } ?: "--.-----"
                    val speed = currentLocation?.speed?.let { String.format("%.1f m/s", it) } ?: "0.0 m/s"

                    Text(
                        text = "POS: $lat, $lon",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "SPD: $speed | HDG: ${userAzimuth.toInt()}°",
                        color = OniAmber,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // FLOATING ACTION CONTROLS
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    sonarLockEnabled = true
                    currentLocation?.let {
                        mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude))
                    }
                    viewModel.triggerSonarAudioPing()
                },
                containerColor = OniSurfaceVariant,
                contentColor = OniNeonGreen,
                shape = CircleShape,
                modifier = Modifier.border(1.dp, OniNeonGreen, CircleShape)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Center on Epicenter")
            }

            FloatingActionButton(
                onClick = {
                    sonarSweepVisible = !sonarSweepVisible
                },
                containerColor = OniSurfaceVariant,
                contentColor = OniNeonBlue,
                shape = CircleShape,
                modifier = Modifier.border(1.dp, OniNeonBlue, CircleShape)
            ) {
                Icon(
                    if (sonarSweepVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Toggle Sonar Sweep"
                )
            }

            FloatingActionButton(
                onClick = {
                    scope.launch {
                        val cacheManager = CacheManager(mapView)
                        val bbox = mapView.boundingBox
                        val zoomMin = mapView.zoomLevelDouble.toInt()
                        val zoomMax = (zoomMin + 2).coerceAtMost(20)

                        withContext(Dispatchers.IO) {
                            cacheManager.downloadAreaAsync(context, bbox, zoomMin, zoomMax)
                        }
                    }
                },
                containerColor = OniSurfaceVariant,
                contentColor = OniAmber,
                shape = CircleShape,
                modifier = Modifier.border(1.dp, OniAmber, CircleShape)
            ) {
                Icon(Icons.Default.Download, contentDescription = "Cache Region Offline")
            }
        }

        // SELECTED NODE ACTION DRAWER (WATCH DOGS STYLE)
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
                                    text = "${dev.macAddress} | ${dev.vendor}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OniNeonBlueVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            IconButton(onClick = { selectedDevice = null }) {
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
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).height(42.dp)
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("HACK / CYBER DECK", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }

                            Button(
                                onClick = {
                                    viewModel.trackDevice(dev)
                                    navController.navigate("compass") { launchSingleTop = true }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = OniDarkRed),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).height(42.dp)
                            ) {
                                Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("LOCK TRACK", fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    }
}
