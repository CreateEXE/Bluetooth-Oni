package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceAliasDao {
    @Query("SELECT * FROM device_aliases")
    fun getAllAliases(): Flow<List<DeviceAliasEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlias(alias: DeviceAliasEntity)
}
