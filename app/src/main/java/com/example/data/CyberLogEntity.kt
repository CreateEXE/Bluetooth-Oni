package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cyber_logs")
data class CyberLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tag: String,
    val message: String,
    val level: String,
    val timestamp: Long = System.currentTimeMillis()
)
