package com.absenku.data.database.dao

import androidx.room.*
import com.absenku.data.model.Kelas

/** CRUD + soft-delete + lookup for Kelas. */
@Dao
interface KelasDao {
    @Query("SELECT * FROM kelas WHERE is_active = 1 ORDER BY nama")
    suspend fun getAllActive(): List<Kelas>

    @Query("SELECT * FROM kelas ORDER BY nama")
    suspend fun getAll(): List<Kelas>

    @Query("SELECT * FROM kelas WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Kelas?

    @Insert
    suspend fun insert(kelas: Kelas): Long

    @Update
    suspend fun update(kelas: Kelas)

    @Query("UPDATE kelas SET is_active = 0, deleted_at = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)

    @Query("SELECT COUNT(*) FROM kelas WHERE is_active = 1")
    suspend fun countActive(): Int
}
