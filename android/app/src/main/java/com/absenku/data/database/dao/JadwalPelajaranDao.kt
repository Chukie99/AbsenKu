package com.absenku.data.database.dao

import androidx.room.*
import com.absenku.data.model.JadwalPelajaran

/** CRUD + queries for JadwalPelajaran (weekly schedule). */
@Dao
interface JadwalPelajaranDao {
    @Query("SELECT * FROM jadwal_pelajaran WHERE is_active = 1 AND deleted_at IS NULL AND kelas_id = :kelasId ORDER BY CASE hari WHEN 'Senin' THEN 1 WHEN 'Selasa' THEN 2 WHEN 'Rabu' THEN 3 WHEN 'Kamis' THEN 4 WHEN 'Jumat' THEN 5 WHEN 'Sabtu' THEN 6 WHEN 'Minggu' THEN 7 ELSE 8 END, jam_mulai")
    suspend fun getByKelas(kelasId: Long): List<JadwalPelajaran>

    @Query("SELECT * FROM jadwal_pelajaran WHERE is_active = 1 AND deleted_at IS NULL ORDER BY kelas_id, CASE hari WHEN 'Senin' THEN 1 WHEN 'Selasa' THEN 2 WHEN 'Rabu' THEN 3 WHEN 'Kamis' THEN 4 WHEN 'Jumat' THEN 5 WHEN 'Sabtu' THEN 6 WHEN 'Minggu' THEN 7 ELSE 8 END, jam_mulai")
    suspend fun getAllActive(): List<JadwalPelajaran>

    @Query("SELECT * FROM jadwal_pelajaran WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): JadwalPelajaran?

    @Insert
    suspend fun insert(jadwal: JadwalPelajaran): Long

    @Update
    suspend fun update(jadwal: JadwalPelajaran)

    @Query("UPDATE jadwal_pelajaran SET is_active = 0, deleted_at = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)

    @Query("SELECT * FROM jadwal_pelajaran WHERE kelas_id = :kelasId AND hari = :hari AND is_active = 1 AND deleted_at IS NULL ORDER BY jam_mulai")
    suspend fun getByKelasAndHari(kelasId: Long, hari: String): List<JadwalPelajaran>
}
