package com.example.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DeviceLocationEntity
import com.example.model.AlertEvent
import com.example.model.AlertSettings
import com.example.model.AlertType
import com.example.model.DeviceCategory
import com.example.model.TrackedDevice
import com.example.util.BleUtils
import com.example.util.GeoUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BluetoothTrackerViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val database = AppDatabase.getDatabase(application)
    private val locationDao = database.deviceLocationDao()
    private val aliasDao = database.deviceAliasDao()

    private val _aliases = MutableStateFlow<Map<String, String>>(emptyMap())

    private val _devices = MutableStateFlow<Map<String, TrackedDevice>>(emptyMap())
    val devices: StateFlow<List<TrackedDevice>> = MutableStateFlow(emptyList())

    private val _userAzimuth = MutableStateFlow(0f)
    val userAzimuth = _userAzimuth.asStateFlow()
    
    private val _userPitch = MutableStateFlow(0f)
    val userPitch = _userPitch.asStateFlow()
    
    private val _userRoll = MutableStateFlow(0f)
    val userRoll = _userRoll.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _trackingDevice = MutableStateFlow<TrackedDevice?>(null)
    val trackingDevice = _trackingDevice.asStateFlow()
    
    private val _deviceAlertSettings = MutableStateFlow<Map<String, AlertSettings>>(emptyMap())
    val deviceAlertSettings = _deviceAlertSettings.asStateFlow()
    
    private val _alertEvents = MutableSharedFlow<AlertEvent>()
    val alertEvents = _alertEvents.asSharedFlow()

    // Used to throttle db inserts
    private val lastInsertTimes = mutableMapOf<String, Long>()
    
    // Hold history for tracked device
    private val _deviceHistory = MutableStateFlow<List<DeviceLocationEntity>>(emptyList())
    val deviceHistory = _deviceHistory.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            val rssi = result.rssi
            val address = device.address
            val name = device.name ?: "Unknown Device"
            
            val distance = GeoUtils.calculateDistance(rssi)
            val majorClass = device.bluetoothClass?.majorDeviceClass ?: android.bluetooth.BluetoothClass.Device.Major.UNCATEGORIZED
            val isConnectable = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                result.isConnectable
            } else false
            
            val deviceCategory = BleUtils.categorizeDevice(result.scanRecord?.serviceUuids, majorClass)
            val customAlias = _aliases.value[address]

            val trackable = TrackedDevice(
                macAddress = address,
                name = name,
                rssi = rssi,
                distanceMeters = distance,
                majorDeviceClass = majorClass,
                isConnectable = isConnectable,
                deviceCategory = deviceCategory,
                customAlias = customAlias
            )

            _devices.update { current ->
                val newMap = current.toMutableMap()
                newMap[address] = trackable
                newMap
            }
            
            checkAlerts(trackable)
            saveLocationToHistory(trackable)
            
            // Update tracking device if it matches
            if (_trackingDevice.value?.macAddress == address) {
                _trackingDevice.value = trackable
            }
        }
    }

    init {
        viewModelScope.launch {
            aliasDao.getAllAliases().collect { list ->
                _aliases.value = list.associate { it.macAddress to it.customName }
            }
        }
        viewModelScope.launch {
            _devices.collect { map ->
                (devices as MutableStateFlow).value = map.values.toList().sortedBy { it.distanceMeters }
            }
        }
        startSensors()
    }
    
    fun setAlias(macAddress: String, alias: String) {
        viewModelScope.launch {
            aliasDao.insertAlias(com.example.data.DeviceAliasEntity(macAddress, alias))
        }
    }
    
    private fun saveLocationToHistory(device: TrackedDevice) {
        val now = System.currentTimeMillis()
        val lastInsert = lastInsertTimes[device.macAddress] ?: 0L
        // Save once every 5 seconds per device
        if (now - lastInsert > 5000) {
            lastInsertTimes[device.macAddress] = now
            viewModelScope.launch {
                locationDao.insertLocation(
                    DeviceLocationEntity(
                        macAddress = device.macAddress,
                        deviceName = device.name,
                        deviceCategory = device.deviceCategory.name,
                        distanceMeters = device.distanceMeters,
                        timestamp = now
                    )
                )
            }
        }
    }
    
    // Alert state tracking to avoid spam
    private val alertState = mutableMapOf<String, Boolean>() // true if already alerted
    
    private fun checkAlerts(device: TrackedDevice) {
        val settings = _deviceAlertSettings.value[device.macAddress] ?: return
        if (!settings.enabled) return
        
        val previouslyAlerted = alertState[device.macAddress] ?: false
        
        if (settings.alertType == AlertType.IN_RANGE && device.distanceMeters <= settings.distanceThresholdMeters) {
            if (!previouslyAlerted) {
                alertState[device.macAddress] = true
                fireAlert(device, "Perimeter Breached: Device is in range! (${String.format("%.1f", device.distanceMeters)}m)", settings.isHostageMode)
            }
        } else if (settings.alertType == AlertType.OUT_OF_RANGE && device.distanceMeters >= settings.distanceThresholdMeters) {
            if (!previouslyAlerted) {
                alertState[device.macAddress] = true
                fireAlert(device, "Perimeter Breached: Device left the safe zone! (${String.format("%.1f", device.distanceMeters)}m)", settings.isHostageMode)
            }
        } else {
            // Reset alert state when condition is no longer met
            alertState[device.macAddress] = false
        }
    }
    
    private fun fireAlert(device: TrackedDevice, message: String, isHostageMode: Boolean) {
        viewModelScope.launch {
            _alertEvents.emit(
                AlertEvent(
                    macAddress = device.macAddress,
                    deviceName = device.name,
                    message = message,
                    isSecurityBreach = isHostageMode
                )
            )
        }
    }
    
    fun updateAlertSettings(macAddress: String, settings: AlertSettings) {
        _deviceAlertSettings.update { current ->
            val newMap = current.toMutableMap()
            newMap[macAddress] = settings
            newMap
        }
        alertState[macAddress] = false
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner != null) {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(null, settings, scanCallback)
            _isScanning.value = true
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        _isScanning.value = false
    }

    fun trackDevice(device: TrackedDevice?) {
        _trackingDevice.value = device
        
        // Load history for this device
        if (device != null) {
            viewModelScope.launch {
                locationDao.getLocationsForDevice(device.macAddress).collect { locations ->
                    _deviceHistory.value = locations
                }
            }
        } else {
            _deviceHistory.value = emptyList()
        }
    }
    
    fun fastTrackDeviceByMac(macAddress: String) {
        val device = _devices.value[macAddress]
        if (device != null) {
            trackDevice(device)
        }
    }

    private fun startSensors() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            
            // Azimuth is orientation[0] in radians
            var azimuthDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (azimuthDegrees < 0) {
                azimuthDegrees += 360f
            }
            _userAzimuth.value = azimuthDegrees
            
            // Pitch is orientation[1]
            _userPitch.value = Math.toDegrees(orientation[1].toDouble()).toFloat()
            
            // Roll is orientation[2]
            _userRoll.value = Math.toDegrees(orientation[2].toDouble()).toFloat()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
