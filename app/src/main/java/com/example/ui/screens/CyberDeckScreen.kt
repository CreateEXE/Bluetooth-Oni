package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.model.*
import com.example.ui.theme.*
import com.example.viewmodel.BluetoothTrackerViewModel

enum class CyberModule(val label: String, val icon: ImageVector) {
    GATT("GATT", Icons.Default.Bolt),
    NETWORK("NET", Icons.Default.Wifi),
    DECOMPILER("HEX", Icons.Default.Code),
    ACOUSTIC("AUDIO", Icons.Default.VolumeUp),
    INTEGRITY("ANTI-SPOOF", Icons.Default.Shield),
    HARDWARE("HW", Icons.Default.Memory)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CyberDeckScreen(viewModel: BluetoothTrackerViewModel, navController: NavController) {
    val devices by viewModel.devices.collectAsState()
    val cyberTarget by viewModel.cyberTargetDevice.collectAsState()
    val gattServices by viewModel.bleGattController.services.collectAsState()
    val gattState by viewModel.bleGattController.connectionState.collectAsState()
    val logs by viewModel.bleGattController.terminalLogs.collectAsState()
    val portResults by viewModel.portScanResults.collectAsState()
    val isPortScanning by viewModel.isPortScanning.collectAsState()

    var selectedModule by remember { mutableStateOf(CyberModule.GATT) }
    var targetIpInput by remember { mutableStateOf("192.168.1.1") }
    var writePayloadInput by remember { mutableStateOf("PING") }
    var showTerminalLogs by remember { mutableStateOf(true) }

    // Pulsing cyber border animation
    val infiniteTransition = rememberInfiniteTransition(label = "cyberGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // TOP HUD HEADER
        Surface(
            color = OniDarkSurface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, OniNeonBlue.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(OniNeonGreen, shape = RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CTOS // CYBER DECK v4.2",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = OniNeonBlue,
                            letterSpacing = 1.5.sp
                        )
                    }

                    Surface(
                        color = Color.Black,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, OniDarkBorder)
                    ) {
                        Text(
                            text = "OFFLINE SECURE",
                            color = OniNeonGreen,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Target Selector Bar
                Text(
                    text = "TARGET SELECTION [${devices.size} NODES DISCOVERED]:",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(4.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(devices) { dev ->
                        val isSelected = cyberTarget?.macAddress == dev.macAddress
                        Surface(
                            onClick = { viewModel.selectCyberTarget(dev) },
                            color = if (isSelected) OniNeonBlue.copy(alpha = 0.2f) else OniSurfaceVariant,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) OniNeonBlue else OniDarkBorder
                            ),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val dotColor = when (dev.signalType) {
                                    SignalType.WIFI -> OniNeonGreen
                                    SignalType.EMF -> Color(0xFFE040FB)
                                    else -> OniNeonBlue
                                }
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(dotColor, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = dev.displayName.take(16),
                                    color = if (isSelected) OniNeonBlue else Color.White,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        // ACTIVE TARGET TELEMETRY CARD
        if (cyberTarget != null) {
            val target = cyberTarget!!
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .border(1.dp, OniNeonBlue.copy(alpha = glowAlpha), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = OniSurfaceVariant),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = target.displayName.uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "MAC: ${target.macAddress} | VENDOR: ${target.vendor}",
                                style = MaterialTheme.typography.bodySmall,
                                color = OniNeonBlueVariant,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${target.rssi} dBm",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (target.rssi > -65) OniNeonGreen else OniAmber,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "~${String.format("%.1f", target.distanceMeters)}m",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.trackDevice(target)
                                navController.navigate("compass") { launchSingleTop = true }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OniDarkRed),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.weight(1f).height(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("TARGET LOCK", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }

                        Button(
                            onClick = {
                                viewModel.triggerSonarAudioPing()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OniSurfaceVariant),
                            border = androidx.compose.foundation.BorderStroke(1.dp, OniNeonGreen),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.weight(1f).height(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = OniNeonGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SONAR PING", color = OniNeonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // MODULE SELECTOR TABS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CyberModule.values().forEach { module ->
                val isSelected = selectedModule == module
                Surface(
                    onClick = { selectedModule = module },
                    color = if (isSelected) OniNeonBlue else OniDarkSurface,
                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) OniNeonBlue else OniDarkBorder
                    ),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = module.icon,
                            contentDescription = null,
                            tint = if (isSelected) Color.Black else Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = module.label,
                            color = if (isSelected) Color.Black else Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // MAIN MODULE INTERFACE CONTAINER
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .background(OniDarkSurface)
                .border(1.dp, OniDarkBorder)
        ) {
            when (selectedModule) {
                CyberModule.GATT -> {
                    GattModuleView(
                        target = cyberTarget,
                        state = gattState,
                        services = gattServices,
                        onConnect = { target ->
                            target?.let { viewModel.bleGattController.connect(it.macAddress, viewModel.bluetoothAdapter) }
                        },
                        onDisconnect = { viewModel.bleGattController.disconnect() },
                        onReadChar = { sUuid, cUuid -> viewModel.bleGattController.readCharacteristic(sUuid, cUuid) },
                        onWriteChar = { sUuid, cUuid, payload -> viewModel.bleGattController.writeCharacteristic(sUuid, cUuid, payload) },
                        payloadInput = writePayloadInput,
                        onPayloadChange = { writePayloadInput = it }
                    )
                }
                CyberModule.NETWORK -> {
                    NetworkModuleView(
                        target = cyberTarget,
                        targetIp = targetIpInput,
                        onIpChange = { targetIpInput = it },
                        isScanning = isPortScanning,
                        results = portResults,
                        onStartScan = { ip -> viewModel.startPortScanOnTarget(ip) }
                    )
                }
                CyberModule.DECOMPILER -> {
                    PacketDecompilerView(
                        target = cyberTarget,
                        decoded = cyberTarget?.let { viewModel.getDecodedBeacon(it) }
                    )
                }
                CyberModule.ACOUSTIC -> {
                    AcousticInterferenceView(
                        onSonarPing = { viewModel.triggerSonarAudioPing() },
                        onCyberTone = { viewModel.triggerCyberTone() },
                        onGeigerTick = { viewModel.triggerGeigerTick(1.0f) },
                        onFlashlightStrobe = { viewModel.triggerFlashlightStrobe() }
                    )
                }
                CyberModule.INTEGRITY -> {
                    val integrityReport by viewModel.locationIntegrityReport.collectAsState()
                    LocationIntegrityModuleView(
                        report = integrityReport,
                        onRefresh = {
                            viewModel.forceRefreshGps()
                            viewModel.refreshLocationIntegrity()
                        },
                        onRecalibrate = { lat, lon ->
                            viewModel.setCustomCoordinates(lat, lon)
                        }
                    )
                }
                CyberModule.HARDWARE -> {
                    HardwareAuditModuleView(
                        report = remember { viewModel.getHardwareAuditReport() },
                        isDaemonRunning = viewModel.isDaemonRunning.collectAsState().value,
                        daemonStatus = viewModel.daemonStatusText.collectAsState().value,
                        onToggleDaemon = { viewModel.toggleDaemon(it) }
                    )
                }
            }
        }

        // BOTTOM TERMINAL LOG DRAWER
        Surface(
            color = Color.Black,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (showTerminalLogs) 160.dp else 36.dp)
                .border(1.dp, OniDarkBorder)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OniDarkSurface)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable { showTerminalLogs = !showTerminalLogs },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = null,
                            tint = OniNeonGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SYSTEM TERMINAL LOG [${logs.size} EVENTS]",
                            color = OniNeonGreen,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row {
                        IconButton(
                            onClick = { viewModel.bleGattController.clearLogs() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = { showTerminalLogs = !showTerminalLogs },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                if (showTerminalLogs) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                contentDescription = "Toggle",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                if (showTerminalLogs) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs) { entry ->
                            val color = when (entry.level) {
                                LogLevel.SUCCESS -> OniNeonGreen
                                LogLevel.WARNING -> OniAmber
                                LogLevel.CRITICAL -> OniRed
                                LogLevel.DATA -> OniNeonBlue
                                LogLevel.INFO -> Color.LightGray
                            }
                            Text(
                                text = "[${entry.tag}] ${entry.message}",
                                color = color,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// MODULE 1: GATT SERVICE EXPLORER & CHARACTERISTIC HACKER
// -------------------------------------------------------------
@Composable
fun GattModuleView(
    target: TrackedDevice?,
    state: String,
    services: List<GattServiceInfo>,
    onConnect: (TrackedDevice?) -> Unit,
    onDisconnect: () -> Unit,
    onReadChar: (String, String) -> Unit,
    onWriteChar: (String, String, String) -> Unit,
    payloadInput: String,
    onPayloadChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "GATT PIPELINE STATUS:",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = state,
                    color = if (state.startsWith("CONNECTED")) OniNeonGreen else OniAmber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (state.startsWith("CONNECTED")) {
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(containerColor = OniDarkRed),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("DISCONNECT", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            } else {
                Button(
                    onClick = { onConnect(target) },
                    enabled = target != null,
                    colors = ButtonDefaults.buttonColors(containerColor = OniNeonBlue),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("INITIATE GATT LINK", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (services.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (target == null) "SELECT A TARGET TO CONNECT" else "CONNECT TO DISCOVER REMOTE GATT ATTRIBUTES",
                    color = Color.DarkGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(services) { service ->
                    Surface(
                        color = OniSurfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, OniDarkBorder)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = service.name.uppercase(),
                                    color = OniNeonBlue,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "${service.characteristics.size} ATTRIBUTES",
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = service.uuid,
                                color = Color.DarkGray,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            service.characteristics.forEach { char ->
                                Surface(
                                    color = OniDarkSurface,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = char.name,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = char.properties.joinToString("|"),
                                                color = OniAmber,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        if (char.readValue != null) {
                                            Text(
                                                text = "VAL: ${char.readValue}",
                                                color = OniNeonGreen,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(vertical = 2.dp)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (char.canRead) {
                                                TextButton(
                                                    onClick = { onReadChar(service.uuid, char.uuid) },
                                                    modifier = Modifier.height(28.dp),
                                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                                ) {
                                                    Text("READ", color = OniNeonBlue, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                                }
                                            }
                                            if (char.canWrite) {
                                                TextButton(
                                                    onClick = { onWriteChar(service.uuid, char.uuid, payloadInput) },
                                                    modifier = Modifier.height(28.dp),
                                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                                ) {
                                                    Text("INJECT", color = OniDarkRed, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// MODULE 2: NETWORK & PORT VULNERABILITY PROBER
// -------------------------------------------------------------
@Composable
fun NetworkModuleView(
    target: TrackedDevice?,
    targetIp: String,
    onIpChange: (String) -> Unit,
    isScanning: Boolean,
    results: List<PortProbeResult>,
    onStartScan: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        Text(
            text = "TARGET HOST IP / SUBNET GATEWAY:",
            color = Color.Gray,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = targetIp,
                onValueChange = onIpChange,
                singleLine = true,
                modifier = Modifier.weight(1f).height(50.dp),
                textStyle = LocalTextStyle.current.copy(
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OniNeonBlue,
                    unfocusedBorderColor = OniDarkBorder,
                    cursorColor = OniNeonBlue
                )
            )

            Button(
                onClick = { onStartScan(targetIp) },
                enabled = !isScanning,
                colors = ButtonDefaults.buttonColors(containerColor = OniNeonGreen),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.height(48.dp)
            ) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black)
                } else {
                    Text("SWEEP", color = Color.Black, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "READY TO PROBE OPEN TCP SOCKETS ON LOCAL NETWORK",
                    color = Color.DarkGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(results) { port ->
                    Surface(
                        color = if (port.isOpen) OniSurfaceVariant else OniDarkSurface,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (port.isOpen) OniNeonGreen.copy(alpha = 0.6f) else OniDarkBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "PORT ${port.port}",
                                        color = if (port.isOpen) OniNeonGreen else Color.Gray,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = port.serviceName,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                if (port.banner != null) {
                                    Text(
                                        text = "BANNER: ${port.banner}",
                                        color = OniNeonBlueVariant,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Text(
                                text = if (port.isOpen) "${port.latencyMs}ms [OPEN]" else "CLOSED",
                                color = if (port.isOpen) OniNeonGreen else Color.DarkGray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// MODULE 3: RAW ADVERTISING PACKET DECOMPILER
// -------------------------------------------------------------
@Composable
fun PacketDecompilerView(
    target: TrackedDevice?,
    decoded: BeaconDecodedData?
) {
    if (decoded == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "SELECT A NODE TO DECOMPILE ADVERTISING BYTE MATRIX",
                color = Color.DarkGray,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Surface(
                color = OniSurfaceVariant,
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, OniDarkBorder)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "BEACON PROTOCOL SIGNATURE:",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = decoded.protocol.uppercase(),
                        color = OniNeonBlue,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "MANUFACTURER: ${decoded.companyName} (${decoded.companyIdHex})",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    if (decoded.uuid != null) {
                        Text(
                            text = "UUID: ${decoded.uuid}",
                            color = OniNeonGreen,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (decoded.major != null && decoded.minor != null) {
                        Text(
                            text = "MAJOR: ${decoded.major} | MINOR: ${decoded.minor} | TX CAL: ${decoded.txPowerCalibrated} dBm",
                            color = OniAmber,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (decoded.flags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        decoded.flags.forEach { flag ->
                            Text(
                                text = "• $flag",
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "HEXADECIMAL DUMP MATRIX:",
                color = Color.Gray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        items(decoded.hexMatrixLines) { line ->
            Surface(
                color = Color.Black,
                shape = RoundedCornerShape(2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, OniDarkBorder)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${line.offsetHex}:",
                        color = OniAmber,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = line.bytesHex,
                        color = OniNeonBlue,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "|${line.asciiPreview}|",
                        color = OniNeonGreen,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// MODULE 4: ACOUSTIC & SIGNAL EMISSION
// -------------------------------------------------------------
@Composable
fun AcousticInterferenceView(
    onSonarPing: () -> Unit,
    onCyberTone: () -> Unit,
    onGeigerTick: () -> Unit,
    onFlashlightStrobe: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "TACTICAL ACOUSTIC & HARDWARE EMISSION:",
            color = Color.Gray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        Button(
            onClick = onSonarPing,
            colors = ButtonDefaults.buttonColors(containerColor = OniSurfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, OniNeonGreen),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = OniNeonGreen)
            Spacer(modifier = Modifier.width(8.dp))
            Text("FIRE TACTICAL SONAR PING (880Hz RESONANT CHIRP)", color = OniNeonGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = onCyberTone,
            colors = ButtonDefaults.buttonColors(containerColor = OniSurfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, OniNeonBlue),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.Sensors, contentDescription = null, tint = OniNeonBlue)
            Spacer(modifier = Modifier.width(8.dp))
            Text("DATA LINK SYNTH PULSE (600Hz-1800Hz)", color = OniNeonBlue, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = onGeigerTick,
            colors = ButtonDefaults.buttonColors(containerColor = OniSurfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, OniAmber),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.Sensors, contentDescription = null, tint = OniAmber)
            Spacer(modifier = Modifier.width(8.dp))
            Text("PROXIMITY GEIGER CLICKER BURST", color = OniAmber, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = onFlashlightStrobe,
            colors = ButtonDefaults.buttonColors(containerColor = OniSurfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, OniRed),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.FlashOn, contentDescription = null, tint = OniRed)
            Spacer(modifier = Modifier.width(8.dp))
            Text("STROBE FLASH BEACON INTERCEPT (OPTICAL)", color = OniRed, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HardwareAuditModuleView(
    report: com.example.model.HardwareAuditReport,
    isDaemonRunning: Boolean,
    daemonStatus: String,
    onToggleDaemon: (Boolean) -> Unit
) {
    var selectedSection by remember { mutableStateOf(0) } // 0: RF RADIOS, 1: PHYSICAL SENSORS

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // HOST SOC & HARDWARE SPECS
        item {
            Surface(
                color = Color.Black,
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, OniNeonBlue.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HOST SILICON TELEMETRY",
                            color = OniNeonBlue,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = report.androidVersion,
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "DEVICE: ${report.deviceModel}",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "CHIPSET/SOC: ${report.socManufacturer.uppercase()} • ${report.totalPhysicalSensors} PHYSICAL SENSORS DETECTED",
                        color = OniNeonGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // BACKGROUND DAEMON CONTROL CARD
        item {
            Surface(
                color = if (isDaemonRunning) OniSurfaceVariant else Color.Black,
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isDaemonRunning) OniNeonGreen else OniDarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (isDaemonRunning) OniNeonGreen else Color.Gray,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isDaemonRunning) "SPECTRE DAEMON RUNNING" else "DAEMON OFFLINE",
                                color = if (isDaemonRunning) OniNeonGreen else Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = daemonStatus,
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }

                    Switch(
                        checked = isDaemonRunning,
                        onCheckedChange = onToggleDaemon
                    )
                }
            }
        }

        // SECTION SWITCHER
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { selectedSection = 0 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedSection == 0) OniNeonBlue else OniSurfaceVariant
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Text(
                        text = "RF RADIOS & SPECTRUM (${report.radioSpecs.size})",
                        color = if (selectedSection == 0) Color.Black else Color.White,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { selectedSection = 1 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedSection == 1) OniNeonGreen else OniSurfaceVariant
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Text(
                        text = "HARDWARE SENSORS (${report.sensors.size})",
                        color = if (selectedSection == 1) Color.Black else Color.White,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (selectedSection == 0) {
            // RF RADIOS LIST
            items(report.radioSpecs) { radio ->
                Surface(
                    color = Color.Black,
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (radio.isSupported) OniDarkBorder else OniRed.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = radio.title,
                                color = if (radio.isSupported) OniNeonBlue else Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Surface(
                                color = if (radio.isSupported) OniNeonGreen.copy(alpha = 0.2f) else OniRed.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(2.dp)
                            ) {
                                Text(
                                    text = if (radio.isSupported) "SUPPORTED" else "UNAVAILABLE",
                                    color = if (radio.isSupported) OniNeonGreen else OniRed,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "SPECTRUM / BAND: ${radio.frequencyBand}",
                            color = OniAmber,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "STANDARD: ${radio.standard}",
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = radio.details,
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        } else {
            // PHYSICAL SENSORS LIST
            items(report.sensors) { sensor ->
                Surface(
                    color = Color.Black,
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OniDarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sensor.typeString,
                                color = OniNeonGreen,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            if (sensor.isWakeUp) {
                                Surface(
                                    color = OniAmber.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(2.dp)
                                ) {
                                    Text(
                                        text = "WAKE-UP",
                                        color = OniAmber,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "CHIP: ${sensor.name} (${sensor.vendor})",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "POWER: ${sensor.powerMa} mA",
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            )
                            Text(
                                text = "RANGE: ${sensor.maxRange}",
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            )
                            Text(
                                text = "RES: ${sensor.resolution}",
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocationIntegrityModuleView(
    report: com.example.model.LocationIntegrityReport,
    onRefresh: () -> Unit,
    onRecalibrate: (Double, Double) -> Unit
) {
    var showRecalibrateDialog by remember { mutableStateOf(false) }
    var latInput by remember { mutableStateOf("") }
    var lonInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            // INTEGRITY STATUS HERO CARD
            val badgeStyle = when (report.integrityLevel) {
                com.example.model.LocationIntegrityLevel.AUTHENTIC_GNSS -> com.example.model.IntegrityBadgeStyle(
                    OniNeonGreen.copy(alpha = 0.12f),
                    OniNeonGreen,
                    "AUTHENTIC HARDWARE GNSS // VERIFIED",
                    OniNeonGreen
                )
                com.example.model.LocationIntegrityLevel.SUSPICIOUS_MOCK -> com.example.model.IntegrityBadgeStyle(
                    OniDarkRed.copy(alpha = 0.2f),
                    OniDarkRed,
                    "SECURITY WARNING: MOCK LOCATION ACTIVE",
                    OniDarkRed
                )
                com.example.model.LocationIntegrityLevel.VPN_CLOAKED -> com.example.model.IntegrityBadgeStyle(
                    OniAmber.copy(alpha = 0.15f),
                    OniAmber,
                    "NETWORK CLOAKED: ACTIVE VPN TUNNEL DETECTED",
                    OniAmber
                )
                com.example.model.LocationIntegrityLevel.EMULATOR_VIRTUAL -> com.example.model.IntegrityBadgeStyle(
                    OniNeonBlue.copy(alpha = 0.15f),
                    OniNeonBlue,
                    "VIRTUAL RUNTIME: EMULATOR CONTAINER DETECTED",
                    OniNeonBlue
                )
                com.example.model.LocationIntegrityLevel.COARSE_CELLULAR -> com.example.model.IntegrityBadgeStyle(
                    OniAmber.copy(alpha = 0.15f),
                    OniAmber,
                    "DEGRADED FIX: CELLULAR / WI-FI TRIANGULATION",
                    OniAmber
                )
                com.example.model.LocationIntegrityLevel.NO_FIX -> com.example.model.IntegrityBadgeStyle(
                    Color.DarkGray.copy(alpha = 0.2f),
                    Color.Gray,
                    "NO FIX ACQUIRED: SEARCHING CONSTELLATIONS...",
                    Color.LightGray
                )
            }

            Surface(
                color = badgeStyle.backgroundColor,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, badgeStyle.borderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = badgeStyle.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = badgeStyle.tintColor
                        )
                        Icon(
                            imageVector = if (report.isMockLocation || report.isVpnActive) Icons.Default.Shield else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = badgeStyle.tintColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = report.diagnosticSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontSize = 11.5.sp
                    )
                }
            }
        }

        item {
            // ACTION BUTTONS ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OniNeonGreen),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OniNeonGreen)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("RE-AUDIT GPS", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showRecalibrateDialog = true },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OniNeonBlue)
                ) {
                    Icon(Icons.Default.EditLocation, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("RECALIBRATE", color = Color.Black, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            // ANTI-SPOOFING DIAGNOSTICS CARD
            CyberSectionCard(title = "ANTI-SPOOFING & CLOAKING MATRIX", icon = Icons.Default.VpnKey) {
                CyberMetricRow("VPN Tunnel Interface", if (report.isVpnActive) "ACTIVE (${report.vpnInterfaceName ?: "tun0"})" else "NO VPN DETECTED", if (report.isVpnActive) OniAmber else OniNeonGreen)
                CyberMetricRow("Mock GPS Status", if (report.isMockLocation) "SPOOFER DETECTED!" else "AUTHENTIC OS GNSS", if (report.isMockLocation) OniDarkRed else OniNeonGreen)
                CyberMetricRow("System HTTP/SOCKS Proxy", if (report.isProxyActive) report.proxyDetails ?: "ACTIVE" else "DIRECT (NO PROXY)", if (report.isProxyActive) OniAmber else OniNeonGreen)
                CyberMetricRow("Runtime Container", if (report.isEmulator) "VIRTUAL EMULATOR" else "PHYSICAL SILICON", if (report.isEmulator) OniNeonBlue else Color.White)
            }
        }

        item {
            // POSITIONING TELEMETRY CARD
            CyberSectionCard(title = "GNSS SATELLITE & FIX TELEMETRY", icon = Icons.Default.MyLocation) {
                val latStr = String.format("%.6f", report.latitude)
                val lonStr = String.format("%.6f", report.longitude)
                CyberMetricRow("Coordinates (Lat / Lon)", "$latStr, $lonStr", OniNeonBlueVariant)
                CyberMetricRow("Location Provider", report.locationProvider.uppercase(), Color.White)
                CyberMetricRow("Horizontal Accuracy", "±${String.format("%.1f", report.accuracyMeters)} meters", if (report.accuracyMeters < 15f) OniNeonGreen else OniAmber)
                CyberMetricRow("Ground Speed", "${String.format("%.1f", report.speedMps)} m/s (${String.format("%.0f", report.speedMps * 3.6f)} km/h)", Color.White)
                CyberMetricRow("Fix Freshness / Age", "${report.locationAgeSeconds} seconds ago", if (report.locationAgeSeconds < 15L) OniNeonGreen else OniAmber)
            }
        }

        if (report.anomalyWarnings.isNotEmpty()) {
            item {
                CyberSectionCard(title = "INTEGRITY ANOMALIES DETECTED", icon = Icons.Default.Warning, borderColor = OniDarkRed) {
                    report.anomalyWarnings.forEach { warning ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(">>", color = OniDarkRed, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(warning, style = MaterialTheme.typography.bodySmall, color = OniAmber, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        item {
            // EDUCATIONAL NOTICE
            Surface(
                color = OniDarkSurface,
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, OniDarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "LOCATION INTEGRITY ARCHITECTURE",
                        style = MaterialTheme.typography.labelSmall,
                        color = OniNeonBlueVariant,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "The anti-spoofing engine queries Android low-level NetworkCapabilities, Linux tun/ppp virtual interfaces, and the Android 12+ isMock hardware flag to detect GPS spoofers, Mock Providers, VPN routing, and emulator containers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        fontSize = 10.5.sp
                    )
                }
            }
        }
    }

    if (showRecalibrateDialog) {
        AlertDialog(
            onDismissRequest = { showRecalibrateDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val lat = latInput.toDoubleOrNull()
                        val lon = lonInput.toDoubleOrNull()
                        if (lat != null && lon != null) {
                            onRecalibrate(lat, lon)
                        }
                        showRecalibrateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OniNeonBlue),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("SET FIX", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecalibrateDialog = false }) {
                    Text("CANCEL", color = Color.Gray, fontFamily = FontFamily.Monospace)
                }
            },
            title = {
                Text("MANUAL GNSS COORDINATE INJECTION", style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace, color = OniNeonBlue)
            },
            text = {
                Column {
                    Text("Enter custom decimal coordinates to manually calibrate map position:", style = MaterialTheme.typography.bodySmall, color = Color.White)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = latInput,
                        onValueChange = { latInput = it },
                        label = { Text("Latitude (e.g. 37.7749)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = lonInput,
                        onValueChange = { lonInput = it },
                        label = { Text("Longitude (e.g. -122.4194)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            containerColor = OniDarkSurface,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun CyberSectionCard(
    title: String,
    icon: ImageVector,
    borderColor: Color = OniDarkBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = Color.Black,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = OniNeonBlue, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = OniNeonBlueVariant,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun CyberMetricRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp
        )
    }
}

