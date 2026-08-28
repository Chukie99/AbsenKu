package com.absenku.data.database.dao

import androidx.room.*
import com.absenku.data.model.AuditLog

/** CRUD for audit trail records. */
@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_log ORDER BY changed_at DESC LIMIT 100")
    suspend fun getRecent(): List<AuditLog>

    @Insert
    suspend fun insert(log: AuditLog)
}
