package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.viewmodel.BluetoothTrackerViewModel
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.tileprovider.tilesource.ITileSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.example.model.SignalType

@Composable
fun GeoMapScreen(viewModel: BluetoothTrackerViewModel, navController: NavController) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Maintain MapView reference for cleanup
    val mapView = remember { MapView(context) }
    
    DisposableEffect(mapView) {
        onDispose {
            mapView.onDetach()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                mapView.apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(16.0)
                    
                    // Add MyLocation overlay
                    val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this)
                    locationOverlay.enableMyLocation()
                    overlays.add(locationOverlay)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                // Keep overlays that aren't markers
                val nonMarkerOverlays = view.overlays.filter { it !is Marker && it !is FolderOverlay }
                view.overlays.clear()
                view.overlays.addAll(nonMarkerOverlays)
                
                // Add device markers
                devices.forEach { device ->
                    if (device.latitude != null && device.longitude != null) {
                        val marker = Marker(view)
                        marker.position = GeoPoint(device.latitude, device.longitude)
                        marker.title = device.displayName
                        
                        val lastSeen = (System.currentTimeMillis() - device.lastSeenTimestamp) / 1000
                        val status = if (lastSeen < 10) "Live" else "${lastSeen}s ago"
                        marker.snippet = "[$status] ${device.rssi} dBm | ~${String.format("%.1f", device.distanceMeters)}m"
                        
                        val icon = when (device.signalType) {
                            SignalType.WIFI -> android.R.drawable.presence_online
                            SignalType.EMF -> android.R.drawable.ic_dialog_alert
                            else -> android.R.drawable.presence_invisible
                        }
                        marker.icon = context.getDrawable(icon)
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        
                        marker.setOnMarkerClickListener { m, _ ->
                            m.showInfoWindow()
                            true
                        }
                        
                        view.overlays.add(marker)
                    }
                }
                
                // If we have a location but haven't centered yet
                if (currentLocation != null && view.mapCenter.latitude == 0.0) {
                    view.controller.setCenter(GeoPoint(currentLocation!!.latitude, currentLocation!!.longitude))
                }
                
                view.invalidate()
            }
        )

        // Overlay UI
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    currentLocation?.let {
                        mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude))
                    }
                }
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Center on me")
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
                }
            ) {
                Icon(Icons.Default.Download, contentDescription = "Cache Region Offline")
            }
        }
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Offline Map Manager",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Automatic caching is active. Tap the download icon to pre-load the current region for complete offline use.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
