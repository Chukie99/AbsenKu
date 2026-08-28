package com.absenku.data.database.dao

import androidx.room.*
import com.absenku.data.model.Absensi

/** CRUD + lookup for Attendance records. */
@Dao
interface AbsensiDao {
    @Query("SELECT * FROM absensi WHERE tanggal = :tanggal AND siswa_id = :siswaId ORDER BY waktu_masuk DESC")
    suspend fun getBySiswaAndDate(siswaId: Long, tanggal: String): List<Absensi>

    @Query("SELECT * FROM absensi WHERE tanggal = :tanggal ORDER BY siswa_id")
    suspend fun getByDate(tanggal: String): List<Absensi>

    @Query("SELECT * FROM absensi WHERE siswa_id = :siswaId ORDER BY tanggal DESC, created_at DESC")
    suspend fun getBySiswa(siswaId: Long): List<Absensi>

    @Query("SELECT DISTINCT tanggal FROM absensi ORDER BY tanggal DESC")
    suspend fun getAllDates(): List<String>

    @Query("SELECT status, COUNT(*) AS count FROM absensi WHERE tanggal = :tanggal GROUP BY status")
    suspend fun dailySummary(tanggal: String): List<android.util.Pair<String, Int>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(absensi: Absensi): Long

    @Update
    suspend fun update(absensi: Absensi)

    @Query("DELETE FROM absensi WHERE id = :id")
    suspend fun delete(id: Long)
}
