package com.example.telemetry.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "telemetry_logs")
data class TelemetryRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val eventType: String,
    val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val ipAddress: String?,
    val accuracyMeters: Float?,
    val payloadJson: String,
    val isSynced: Boolean = false
)
