package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.theme.*
import com.example.viewmodel.BluetoothTrackerViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

enum class Screen(val route: String, val title: String, val icon: ImageVector) {
    List("list", "Nodes", Icons.AutoMirrored.Filled.List),
    Map("map", "Radar", Icons.Default.Map),
    GeoMap("geomap", "Sonar Map", Icons.Default.Language),
    CyberDeck("cyberdeck", "Cyber Deck", Icons.Default.Bolt),
    Compass("compass", "Tracker", Icons.Default.Explore),
    Settings("settings", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: BluetoothTrackerViewModel = viewModel()) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    val trackingDevice by viewModel.trackingDevice.collectAsState()
    val generalSettings by viewModel.generalSettings.collectAsState()

    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    val permissionState = rememberMultiplePermissionsState(permissions)

    LaunchedEffect(Unit) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
    }

    // Proximity "Geiger Counter" haptic & audio feedback loop
    LaunchedEffect(trackingDevice?.macAddress, generalSettings.hapticFeedback, generalSettings.geigerAudioEnabled) {
        val mac = trackingDevice?.macAddress ?: return@LaunchedEffect
        while (isActive) {
            val currentDevice = viewModel.devices.value.find { it.macAddress == mac }
            if (currentDevice != null) {
                val dist = currentDevice.distanceMeters

                if (generalSettings.hapticFeedback) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(40)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (generalSettings.geigerAudioEnabled) {
                    viewModel.triggerGeigerTick(1.0f)
                }

                // Closer = faster ticking rate
                val delayMs = (dist * 90).toLong().coerceIn(90L, 1800L)
                delay(delayMs)
            } else {
                delay(1000)
            }
        }
    }

    // Breach Alerts
    LaunchedEffect(Unit) {
        viewModel.alertEvents.collect { event ->
            if (event.isSecurityBreach) {
                try {
                    val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    val r = RingtoneManager.getRingtone(context, notification)
                    r?.play()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                try {
                    val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(pattern, -1)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                viewModel.fastTrackDeviceByMac(event.macAddress)
                navController.navigate(Screen.Compass.route) {
                    launchSingleTop = true
                }
            }

            snackbarHostState.showSnackbar(
                message = "${event.deviceName}: ${event.message}",
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        topBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            if (currentRoute != "splash") {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(OniNeonGreen, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CTOS // SPECTRE SCANNER",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = OniNeonBlue
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.triggerSonarAudioPing() }) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Sonar Ping", tint = OniNeonGreen)
                        }
                        IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.LightGray)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = OniDarkSurface,
                        titleContentColor = Color.White
                    ),
                    modifier = Modifier.border(1.dp, OniDarkBorder)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            if (currentRoute != "splash") {
                NavigationBar(
                    containerColor = OniDarkSurface,
                    modifier = Modifier.border(1.dp, OniDarkBorder)
                ) {
                    Screen.values().forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    screen.icon,
                                    contentDescription = screen.title,
                                    tint = if (isSelected) OniNeonBlue else Color.Gray
                                )
                            },
                            label = {
                                Text(
                                    screen.title,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) OniNeonBlue else Color.Gray
                                )
                            },
                            selected = isSelected,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = OniSurfaceVariant
                            ),
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (permissionState.allPermissionsGranted) {
            LaunchedEffect(Unit) {
                viewModel.startScan()
            }
            NavHost(
                navController = navController,
                startDestination = "splash",
                modifier = Modifier
                    .padding(innerPadding)
                    .background(Color.Black)
            ) {
                composable("splash") {
                    SplashScreen(onNavigateToMain = {
                        navController.navigate(Screen.List.route) {
                            popUpTo("splash") { inclusive = true }
                        }
                    })
                }
                composable(Screen.List.route) {
                    DeviceListScreen(
                        viewModel = viewModel,
                        navController = navController,
                        onNavigateToCompass = {
                            navController.navigate(Screen.Compass.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable(Screen.Map.route) {
                    MapScreen(viewModel, navController)
                }
                composable(Screen.GeoMap.route) {
                    GeoMapScreen(viewModel, navController)
                }
                composable(Screen.CyberDeck.route) {
                    CyberDeckScreen(viewModel, navController)
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(viewModel, navController)
                }
                composable(Screen.Compass.route) {
                    CompassScreen(viewModel)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = OniNeonBlue,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "RF SCANNING PERMISSION REQUIRED",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Location and Bluetooth hardware access needed for live Sonar Epicenter tracking and packet inspection.",
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { permissionState.launchMultiplePermissionRequest() },
                        colors = ButtonDefaults.buttonColors(containerColor = OniNeonBlue),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "AUTHORIZE SENSORS",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
