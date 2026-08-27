package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Settings
import com.example.model.FilterSettings
import com.example.model.GeneralSettings
import com.example.model.TrackedDevice
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.viewmodel.BluetoothTrackerViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import android.media.RingtoneManager
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context

import androidx.compose.material.icons.filled.Language

enum class Screen(val route: String, val title: String, val icon: ImageVector) {
    List("list", "Devices", Icons.AutoMirrored.Filled.List),
    Map("map", "Radar", Icons.Default.Map),
    GeoMap("geomap", "World Map", Icons.Default.Language),
    Compass("compass", "Compass", Icons.Default.Explore),
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

    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
    }

    val permissionState = rememberMultiplePermissionsState(permissions)

    LaunchedEffect(Unit) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
    }
    
    // Proximity "Geiger Counter" haptic feedback
    LaunchedEffect(trackingDevice?.macAddress) {
        val mac = trackingDevice?.macAddress ?: return@LaunchedEffect
        while(isActive) {
            val currentDevice = viewModel.devices.value.find { it.macAddress == mac }
            if (currentDevice != null) {
                val dist = currentDevice.distanceMeters
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(50)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                // Calculate delay: closer = faster vibration
                val delayMs = (dist * 100).toLong().coerceIn(100L, 2000L)
                delay(delayMs)
            } else {
                delay(1000)
            }
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.alertEvents.collect { event ->
            if (event.isSecurityBreach) {
                // Play alarm sound
                try {
                    val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    val r = RingtoneManager.getRingtone(context, notification)
                    r.play()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                // Vibrate with breach pattern
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
                
                // Fast track device and switch to compass immediately
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
                    title = { Text("Tracker") },
                    actions = {
                        var expanded by remember { mutableStateOf(false) }
                        val filterSettings by viewModel.filterSettings.collectAsState()
                        IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            if (currentRoute != "splash") {
                NavigationBar {
                    Screen.values().forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
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
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("splash") {
                    SplashScreen(onNavigateToMain = {
                        navController.navigate(Screen.List.route) {
                            popUpTo("splash") { inclusive = true }
                        }
                    })
                }
                composable(Screen.List.route) {
                    DeviceListScreen(viewModel, onNavigateToCompass = {
                        navController.navigate(Screen.Compass.route) {
                            launchSingleTop = true
                        }
                    })
                }
                composable(Screen.Map.route) {
                    MapScreen(viewModel, navController)
                }
                composable(Screen.GeoMap.route) {
                    GeoMapScreen(viewModel, navController)
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(viewModel, navController)
                }
                composable(Screen.Compass.route) {
                    CompassScreen(viewModel)
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Column(modifier = Modifier.align(androidx.compose.ui.Alignment.Center)) {
                    Text("Permissions required to scan for Bluetooth devices.")
                    Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                        Text("Grant Permissions")
                    }
                }
            }
        }
    }
}
