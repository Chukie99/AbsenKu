package com.absenku.data.database.dao

import androidx.room.*
import com.absenku.data.model.Siswa

/** CRUD + soft-delete + search/filter for Siswa. */
@Dao
interface SiswaDao {
    @Query("SELECT * FROM siswa WHERE is_active = 1 AND deleted_at IS NULL ORDER BY nama")
    suspend fun getAllActive(): List<Siswa>

    @Query("SELECT * FROM siswa WHERE is_active = 1 AND deleted_at IS NULL AND kelas_id = :kelasId ORDER BY nama")
    suspend fun getByKelas(kelasId: Long): List<Siswa>

    @Query("SELECT * FROM siswa WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Siswa?

    @Query("SELECT * FROM siswa WHERE nis = :nis LIMIT 1")
    suspend fun getByNis(nis: String): Siswa?

    @Query("SELECT * FROM siswa WHERE is_active = 1 AND deleted_at IS NULL AND (nama LIKE '%' || :q || '%' OR nis LIKE '%' || :q || '%') ORDER BY nama")
    suspend fun search(q: String): List<Siswa>

    @Insert
    suspend fun insert(siswa: Siswa): Long

    @Update
    suspend fun update(siswa: Siswa)

    @Query("UPDATE siswa SET is_active = 0, deleted_at = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)

    @Query("SELECT COUNT(*) FROM siswa WHERE is_active = 1 AND deleted_at IS NULL")
    suspend fun countActive(): Int
}
