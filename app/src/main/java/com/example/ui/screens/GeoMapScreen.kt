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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import com.example.model.LocationIntegrityLevel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoMapScreen(viewModel: BluetoothTrackerViewModel, navController: NavController) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val userAzimuth by viewModel.userAzimuth.collectAsState()
    val generalSettings by viewModel.generalSettings.collectAsState()
    val integrityReport by viewModel.locationIntegrityReport.collectAsState()
    val scope = rememberCoroutineScope()

    var sonarLockEnabled by remember { mutableStateOf(generalSettings.sonarEpicenterAutoFollow) }
    var sonarSweepVisible by remember { mutableStateOf(generalSettings.sonarSweepAnimation) }
    var heatMapVisible by remember { mutableStateOf(true) }
    var selectedDevice by remember { mutableStateOf<TrackedDevice?>(null) }
    var lastCenteredLocation by remember { mutableStateOf<GeoPoint?>(null) }

    // Dialog states
    var showIntegrityDialog by remember { mutableStateOf(false) }
    var showHeatMapInfoDialog by remember { mutableStateOf(false) }
    var showRecalibrateDialog by remember { mutableStateOf(false) }
    var manualLatInput by remember { mutableStateOf("") }
    var manualLonInput by remember { mutableStateOf("") }

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

    // Heat map thermal pulse
    val heatPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heatPulse"
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

        // REAL-TIME TACTICAL SONAR & RF HEAT MAP CANVAS OVERLAY
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = minOf(size.width, size.height) * 0.44f

            // 1. RF SIGNAL HEAT MAP LAYER (Logarithmic Electromagnetic Density)
            if (heatMapVisible && devices.isNotEmpty()) {
                devices.forEach { device ->
                    val distanceRatio = (device.distanceMeters / 30.0).coerceIn(0.05, 1.0)
                    val r = maxRadius * distanceRatio.toFloat()
                    val angle = (device.macAddress.hashCode() and 0x7FFFFFFF) % 360f
                    val angleRad = Math.toRadians(angle.toDouble()) - Math.PI / 2
                    val nodeX = center.x + (r * cos(angleRad)).toFloat()
                    val nodeY = center.y + (r * sin(angleRad)).toFloat()
                    val nodeCenter = Offset(nodeX, nodeY)

                    // Thermal contour based on RSSI
                    val (coreColor, haloColor, heatRadius) = when {
                        device.rssi >= -55 -> Triple(Color(0xFFFF1744), Color(0xFFFF9100), 75f) // Hot (Immediate Red/Orange)
                        device.rssi >= -70 -> Triple(Color(0xFFFFAB00), Color(0xFFFFD600), 55f) // Warm (Amber/Yellow)
                        device.rssi >= -85 -> Triple(Color(0xFF00E5FF), Color(0xFF00B0FF), 40f) // Moderate (Cyan)
                        else -> Triple(Color(0xFF304FFE), Color(0xFF651FFF), 28f)               // Cold (Deep Blue)
                    }

                    // Outer thermal diffusion halo
                    drawCircle(
                        color = haloColor.copy(alpha = heatPulseAlpha * 0.35f),
                        radius = heatRadius * 1.5f,
                        center = nodeCenter
                    )
                    // Mid thermal radiation ring
                    drawCircle(
                        color = coreColor.copy(alpha = heatPulseAlpha * 0.6f),
                        radius = heatRadius,
                        center = nodeCenter
                    )
                    // Inner hot core
                    drawCircle(
                        color = coreColor.copy(alpha = 0.85f),
                        radius = heatRadius * 0.35f,
                        center = nodeCenter
                    )
                }
            }

            // 2. SONAR EPICENTER RANGE RINGS & ROTATING SWEEP
            if (sonarSweepVisible) {
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

        // TOP TACTICAL HUD TELEMETRY & PRIVACY INTEGRITY BAR
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .align(Alignment.TopCenter),
            color = OniDarkSurface.copy(alpha = 0.94f),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, OniNeonBlue.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Header row
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

                Spacer(modifier = Modifier.height(6.dp))

                // Real-Time Coordinates & Heading
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

                Spacer(modifier = Modifier.height(6.dp))

                // LOCATION & PRIVACY INTEGRITY STATUS BADGE (Clickable Audit Launcher)
                val badge = when (integrityReport.integrityLevel) {
                    LocationIntegrityLevel.AUTHENTIC_GNSS -> GeoIntegrityBadge(
                        OniNeonGreen.copy(alpha = 0.15f),
                        OniNeonGreen,
                        "GNSS: HARDWARE FIX (±${String.format("%.1f", integrityReport.accuracyMeters)}m)",
                        Icons.Default.VerifiedUser
                    )
                    LocationIntegrityLevel.SUSPICIOUS_MOCK -> GeoIntegrityBadge(
                        OniDarkRed.copy(alpha = 0.25f),
                        OniDarkRed,
                        "ALERT: MOCK GPS DETECTED (SPOOFER ACTIVE)",
                        Icons.Default.Warning
                    )
                    LocationIntegrityLevel.VPN_CLOAKED -> GeoIntegrityBadge(
                        OniAmber.copy(alpha = 0.2f),
                        OniAmber,
                        "VPN ACTIVE: ${integrityReport.vpnInterfaceName ?: "TUNNEL"} (GEOIP CLOAKED)",
                        Icons.Default.VpnKey
                    )
                    LocationIntegrityLevel.EMULATOR_VIRTUAL -> GeoIntegrityBadge(
                        OniNeonBlue.copy(alpha = 0.2f),
                        OniNeonBlue,
                        "RUNTIME: VIRTUAL EMULATOR (SYNTHETIC FIX)",
                        Icons.Default.Sensors
                    )
                    LocationIntegrityLevel.COARSE_CELLULAR -> GeoIntegrityBadge(
                        OniAmber.copy(alpha = 0.2f),
                        OniAmber,
                        "COARSE FIX (±${String.format("%.0f", integrityReport.accuracyMeters)}m CELL/WIFI)",
                        Icons.Default.CellTower
                    )
                    LocationIntegrityLevel.NO_FIX -> GeoIntegrityBadge(
                        Color.DarkGray.copy(alpha = 0.3f),
                        Color.Gray,
                        "NO GNSS FIX (ACQUIRING SATELLITES...)",
                        Icons.Default.GpsNotFixed
                    )
                }

                Surface(
                    color = badge.bg,
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badge.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showIntegrityDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(badge.icon, contentDescription = null, tint = badge.border, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = badge.text,
                                color = badge.border,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "[AUDIT >>]",
                            color = OniNeonBlueVariant,
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // FLOATING ACTION CONTROLS (Map Controls & Heat Map Toggle)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Recenter
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

            // Toggle Sonar Sweep
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

            // Toggle RF Heat Map
            FloatingActionButton(
                onClick = {
                    heatMapVisible = !heatMapVisible
                },
                containerColor = OniSurfaceVariant,
                contentColor = if (heatMapVisible) Color(0xFFFF5252) else Color.Gray,
                shape = CircleShape,
                modifier = Modifier.border(1.dp, if (heatMapVisible) Color(0xFFFF5252) else Color.Gray, CircleShape)
            ) {
                Icon(Icons.Default.Whatshot, contentDescription = "Toggle RF Heat Map")
            }

            // RF Heat Map Help & Explanation
            FloatingActionButton(
                onClick = {
                    showHeatMapInfoDialog = true
                },
                containerColor = OniSurfaceVariant,
                contentColor = OniAmber,
                shape = CircleShape,
                modifier = Modifier.border(1.dp, OniAmber, CircleShape)
            ) {
                Icon(Icons.Default.Info, contentDescription = "What is Heat Map?")
            }

            // Offline Cache
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
                contentColor = OniNeonBlueVariant,
                shape = CircleShape,
                modifier = Modifier.border(1.dp, OniNeonBlueVariant, CircleShape)
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
                                Text("CYBER DECK", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
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

    // LOCATION & PRIVACY INTEGRITY AUDIT DIALOG
    if (showIntegrityDialog) {
        AlertDialog(
            onDismissRequest = { showIntegrityDialog = false },
            confirmButton = {
                TextButton(onClick = { showIntegrityDialog = false }) {
                    Text("CLOSE", color = OniNeonBlue, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = OniNeonBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LOCATION INTEGRITY & CLOAKING AUDIT", style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = integrityReport.diagnosticSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = OniDarkBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("ANTI-SPOOFING & NETWORK TELEMETRY", style = MaterialTheme.typography.labelMedium, color = OniNeonBlueVariant, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(6.dp))

                    IntegrityField("VPN Tunnel Detection", if (integrityReport.isVpnActive) "ACTIVE (${integrityReport.vpnInterfaceName ?: "tun0"})" else "NO VPN DETECTED", if (integrityReport.isVpnActive) OniAmber else OniNeonGreen)
                    IntegrityField("Mock Location (Spoofer)", if (integrityReport.isMockLocation) "MOCK DETECTED!" else "AUTHENTIC HARDWARE", if (integrityReport.isMockLocation) OniDarkRed else OniNeonGreen)
                    IntegrityField("HTTP/SOCKS Proxy", if (integrityReport.isProxyActive) integrityReport.proxyDetails ?: "CONFIGURED" else "DIRECT CONNECTION", if (integrityReport.isProxyActive) OniAmber else OniNeonGreen)
                    IntegrityField("GPS Provider", integrityReport.locationProvider.uppercase(), Color.White)
                    IntegrityField("Fix Precision Accuracy", "±${String.format("%.1f", integrityReport.accuracyMeters)} meters", if (integrityReport.accuracyMeters < 15f) OniNeonGreen else OniAmber)
                    IntegrityField("Fix Age / Freshness", "${integrityReport.locationAgeSeconds}s ago", if (integrityReport.locationAgeSeconds < 10L) OniNeonGreen else OniAmber)
                    IntegrityField("Runtime Environment", if (integrityReport.isEmulator) "EMULATOR CONTAINER" else "PHYSICAL HARDWARE", if (integrityReport.isEmulator) OniNeonBlue else Color.White)

                    if (integrityReport.anomalyWarnings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("ACTIVE ANOMALY WARNINGS", style = MaterialTheme.typography.labelMedium, color = OniDarkRed, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(4.dp))
                        integrityReport.anomalyWarnings.forEach { warn ->
                            Text("• $warn", style = MaterialTheme.typography.bodySmall, color = OniAmber, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = OniDarkBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("GPS ACTIONS & CALIBRATION", style = MaterialTheme.typography.labelMedium, color = OniNeonGreen, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.forceRefreshGps()
                            viewModel.refreshLocationIntegrity()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OniNeonGreen)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("FORCE HIGH-PRECISION GNSS FIX", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedButton(
                        onClick = {
                            showIntegrityDialog = false
                            showRecalibrateDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OniNeonBlue)
                    ) {
                        Icon(Icons.Default.EditLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("MANUAL GNSS RECALIBRATION", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            },
            containerColor = OniDarkSurface,
            shape = RoundedCornerShape(12.dp)
        )
    }

    // MANUAL GPS RECALIBRATION DIALOG
    if (showRecalibrateDialog) {
        AlertDialog(
            onDismissRequest = { showRecalibrateDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val lat = manualLatInput.toDoubleOrNull()
                        val lon = manualLonInput.toDoubleOrNull()
                        if (lat != null && lon != null) {
                            viewModel.setCustomCoordinates(lat, lon)
                        }
                        showRecalibrateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OniNeonBlue),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("APPLY RECALIBRATION", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecalibrateDialog = false }) {
                    Text("CANCEL", color = Color.Gray, fontFamily = FontFamily.Monospace)
                }
            },
            title = {
                Text("MANUAL GNSS RECALIBRATION", style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace, color = OniNeonBlue)
            },
            text = {
                Column {
                    Text(
                        "If you are running in an emulator or indoors without a satellite line-of-sight, you can set your coordinates manually:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = manualLatInput,
                        onValueChange = { manualLatInput = it },
                        label = { Text("Latitude (e.g. 37.7749)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualLonInput,
                        onValueChange = { manualLonInput = it },
                        label = { Text("Longitude (e.g. -122.4194)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("QUICK PRESETS", style = MaterialTheme.typography.labelSmall, color = OniNeonBlueVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PresetButton("SF / Bay", "37.7749", "-122.4194") { manualLatInput = it.first; manualLonInput = it.second }
                        PresetButton("New York", "40.7128", "-74.0060") { manualLatInput = it.first; manualLonInput = it.second }
                        PresetButton("London", "51.5074", "-0.1278") { manualLatInput = it.first; manualLonInput = it.second }
                    }
                }
            },
            containerColor = OniDarkSurface,
            shape = RoundedCornerShape(12.dp)
        )
    }

    // RF HEAT MAP EXPLANATION DIALOG
    if (showHeatMapInfoDialog) {
        AlertDialog(
            onDismissRequest = { showHeatMapInfoDialog = false },
            confirmButton = {
                TextButton(onClick = { showHeatMapInfoDialog = false }) {
                    Text("UNDERSTOOD", color = OniNeonBlue, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Whatshot, contentDescription = null, tint = Color(0xFFFF5252))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("WHAT IS THE RF HEAT MAP?", style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "The RF Signal Heat Map is a tactical chromatic visualization of electromagnetic radio frequency density across your local physical environment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("1. INVERSE-SQUARE LAW & LOG-DISTANCE ATTENUATION", style = MaterialTheme.typography.labelMedium, color = OniNeonBlueVariant, fontFamily = FontFamily.Monospace)
                    Text(
                        text = "Radio waves decay over distance according to the Free-Space Path Loss formula. When you approach a BLE beacon or Wi-Fi transmitter, the Received Signal Strength Indicator (RSSI in dBm) surges logarithmically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("2. THERMAL GRADIENT COLOR CODING", style = MaterialTheme.typography.labelMedium, color = OniNeonGreen, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(4.dp))
                    ThermalLegendRow("CRIMSON / ORANGE", "HOT ZONE (RSSI > -55 dBm): Immediate physical proximity (0 - 3m)", Color(0xFFFF1744))
                    ThermalLegendRow("AMBER / YELLOW", "WARM ZONE (RSSI -55 to -70 dBm): Moderate proximity (3 - 10m)", Color(0xFFFFAB00))
                    ThermalLegendRow("CYAN / BLUE", "COOL ZONE (RSSI -70 to -85 dBm): Outer perimeter (10 - 20m)", Color(0xFF00E5FF))
                    ThermalLegendRow("DEEP INDIGO", "FRINGE ZONE (RSSI < -85 dBm): Edge of radio detection (> 20m)", Color(0xFF304FFE))

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("3. TRIANGULATION & PHYSICAL NODE RECON", style = MaterialTheme.typography.labelMedium, color = OniAmber, fontFamily = FontFamily.Monospace)
                    Text(
                        text = "As you move around your physical space with the Sonar Epicenter locked, overlapping thermal halos reveal the physical focal points where transmitters, rogue trackers, or Wi-Fi APs are hidden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }
            },
            containerColor = OniDarkSurface,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun IntegrityField(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray, fontSize = 11.sp)
        Text(value, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }
}

@Composable
private fun ThermalLegendRow(label: String, desc: String, dotColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(8.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = dotColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.LightGray, fontSize = 10.5.sp)
        }
    }
}

@Composable
private fun RowScope.PresetButton(name: String, lat: String, lon: String, onSelect: (Pair<String, String>) -> Unit) {
    OutlinedButton(
        onClick = { onSelect(Pair(lat, lon)) },
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(name, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}

private data class GeoIntegrityBadge(
    val bg: Color,
    val border: Color,
    val text: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
