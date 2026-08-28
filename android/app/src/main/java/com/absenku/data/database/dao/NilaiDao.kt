package com.absenku.data.database.dao

import androidx.room.*
import com.absenku.data.model.Nilai

/** CRUD + rekap queries for Nilai. */
@Dao
interface NilaiDao {
    @Query("SELECT * FROM nilai WHERE siswa_id = :siswaId AND mapel_id = :mapelId ORDER BY created_at DESC")
    suspend fun getBySiswaMapel(siswaId: Long, mapelId: Long): List<Nilai>

    @Query("SELECT * FROM nilai WHERE siswa_id = :siswaId ORDER BY created_at DESC")
    suspend fun getBySiswa(siswaId: Long): List<Nilai>

    @Query("SELECT * FROM nilai WHERE mapel_id = :mapelId ORDER BY created_at DESC")
    suspend fun getByMapel(mapelId: Long): List<Nilai>

    @Insert
    suspend fun insert(nilai: Nilai): Long

    @Update
    suspend fun update(nilai: Nilai)
}
