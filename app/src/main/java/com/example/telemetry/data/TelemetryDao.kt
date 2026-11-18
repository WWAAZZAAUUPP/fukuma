package com.example.telemetry.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TelemetryDao {
    @Insert
    suspend fun insert(record: TelemetryRecord)

    @Query("SELECT * FROM telemetry_logs WHERE isSynced = 0 LIMIT :limit")
    suspend fun getUnsynced(limit: Int): List<TelemetryRecord>

    @Query("UPDATE telemetry_logs SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)
}
