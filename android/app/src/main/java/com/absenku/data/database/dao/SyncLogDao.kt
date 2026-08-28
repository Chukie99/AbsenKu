package com.absenku.data.database.dao

import androidx.room.*
import com.absenku.data.model.SyncLog

/** CRUD for synchronization log. */
@Dao
interface SyncLogDao {
    @Query("SELECT * FROM sync_log ORDER BY created_at DESC LIMIT 200")
    suspend fun getRecent(): List<SyncLog>

    @Insert
    suspend fun insert(log: SyncLog)
}
