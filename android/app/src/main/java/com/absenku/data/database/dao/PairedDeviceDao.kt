package com.absenku.data.database.dao

import androidx.room.*
import com.absenku.data.model.PairedDevice

/** CRUD for paired devices (sync trust). */
@Dao
interface PairedDeviceDao {
    @Query("SELECT * FROM paired_devices WHERE revoked = 0 ORDER BY paired_at DESC")
    suspend fun getAllActive(): List<PairedDevice>

    @Query("SELECT * FROM paired_devices WHERE pairing_token = :token AND revoked = 0 LIMIT 1")
    suspend fun getByToken(token: String): PairedDevice?

    @Query("SELECT * FROM paired_devices WHERE device_id = :deviceId AND revoked = 0 LIMIT 1")
    suspend fun getByDeviceId(deviceId: String): PairedDevice?

    @Insert
    suspend fun insert(device: PairedDevice): Long

    @Update
    suspend fun update(device: PairedDevice)

    @Query("UPDATE paired_devices SET revoked = 1 WHERE id = :id")
    suspend fun revoke(id: Long)
}
