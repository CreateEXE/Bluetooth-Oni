package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CyberLogDao {
    @Query("SELECT * FROM cyber_logs ORDER BY timestamp DESC LIMIT 200")
    fun getRecentLogs(): Flow<List<CyberLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: CyberLogEntity)

    @Query("DELETE FROM cyber_logs")
    suspend fun clearLogs()
}
