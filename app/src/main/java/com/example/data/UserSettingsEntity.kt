package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = 1,
    // General Settings
    val hapticFeedback: Boolean = true,
    val flashlightAlert: Boolean = false,
    val backgroundScanning: Boolean = false,
    val sonarSoundEnabled: Boolean = true,
    val sonarEpicenterAutoFollow: Boolean = true,
    val sonarSweepAnimation: Boolean = true,
    val geigerAudioEnabled: Boolean = false,
    // Filter Settings
    val showBluetooth: Boolean = true,
    val showWifi: Boolean = true,
    val showEmf: Boolean = true,
    val showNamedOnly: Boolean = false,
    val showLockedWifi: Boolean = true,
    val showOpenWifi: Boolean = true,
    val showNewOnly: Boolean = false,
    val showTrackedOnly: Boolean = false,
    val minSignalStrength: Int = -100
)
