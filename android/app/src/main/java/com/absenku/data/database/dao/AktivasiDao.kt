package com.absenku.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.absenku.data.model.Aktivasi

/**
 * CRUD & lookups for the device activation record.
 */
@Dao
interface AktivasiDao {
    @Query("SELECT * FROM aktivasi LIMIT 1")
    suspend fun get(): Aktivasi?

    @Query("SELECT * FROM aktivasi WHERE device_id = :deviceId LIMIT 1")
    suspend fun getByDevice(deviceId: String): Aktivasi?

    @Insert
    suspend fun insert(aktivasi: Aktivasi): Long

    @Update
    suspend fun update(aktivasi: Aktivasi)
}
