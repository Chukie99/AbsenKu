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

    @Query("SELECT siswa_id, AVG(CAST(nilai AS REAL)) AS avg_nilai, COUNT(*) AS jumlah FROM nilai WHERE mapel_id = :mapelId GROUP BY siswa_id ORDER BY avg_nilai DESC")
    suspend fun getRankingByMapel(mapelId: Long): List<SiswaNilaiRanking>

    @Query("SELECT siswa_id, AVG(CAST(nilai AS REAL)) AS avg_nilai, COUNT(*) AS jumlah FROM nilai GROUP BY siswa_id ORDER BY avg_nilai DESC")
    suspend fun getRankingAll(): List<SiswaNilaiRanking>

    @Query("SELECT mapel_id, AVG(CAST(nilai AS REAL)) AS avg_nilai, COUNT(*) AS jumlah FROM nilai GROUP BY mapel_id")
    suspend fun getAvgByMapel(): List<MapelAvgNilai>

    @Insert
    suspend fun insert(nilai: Nilai): Long

    @Update
    suspend fun update(nilai: Nilai)
}

data class SiswaNilaiRanking(
    @ColumnInfo(name = "siswa_id") val siswaId: Long,
    @ColumnInfo(name = "avg_nilai") val avgNilai: Double,
    @ColumnInfo(name = "jumlah") val jumlah: Int,
)

data class MapelAvgNilai(
    @ColumnInfo(name = "mapel_id") val mapelId: Long,
    @ColumnInfo(name = "avg_nilai") val avgNilai: Double,
    @ColumnInfo(name = "jumlah") val jumlah: Int,
)
