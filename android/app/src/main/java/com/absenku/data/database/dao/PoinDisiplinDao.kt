package com.absenku.data.database.dao

import androidx.room.*
import com.absenku.data.model.PoinDisiplin

/** CRUD + queries for PoinDisiplin (discipline points). */
@Dao
interface PoinDisiplinDao {
    @Query("SELECT * FROM poin_disiplin WHERE siswa_id = :siswaId ORDER BY tanggal DESC, created_at DESC")
    suspend fun getBySiswa(siswaId: Long): List<PoinDisiplin>

    @Query("SELECT * FROM poin_disiplin ORDER BY tanggal DESC, created_at DESC")
    suspend fun getAll(): List<PoinDisiplin>

    @Query("SELECT * FROM poin_disiplin WHERE siswa_id = :siswaId AND kategori = :kategori ORDER BY tanggal DESC")
    suspend fun getBySiswaAndKategori(siswaId: Long, kategori: String): List<PoinDisiplin>

    @Query("SELECT SUM(poin) FROM poin_disiplin WHERE siswa_id = :siswaId AND kategori = 'Positif'")
    suspend fun totalPositif(siswaId: Long): Int?

    @Query("SELECT SUM(poin) FROM poin_disiplin WHERE siswa_id = :siswaId AND kategori = 'Negatif'")
    suspend fun totalNegatif(siswaId: Long): Int?

    @Query("SELECT siswa_id, SUM(CASE WHEN kategori='Positif' THEN poin ELSE 0 END) - SUM(CASE WHEN kategori='Negatif' THEN poin ELSE 0 END) AS net_poin FROM poin_disiplin GROUP BY siswa_id ORDER BY net_poin DESC")
    suspend fun getRankingByPoin(): List<SiswaPoinRanking>

    @Insert
    suspend fun insert(poinDisiplin: PoinDisiplin): Long

    @Update
    suspend fun update(poinDisiplin: PoinDisiplin)

    @Query("DELETE FROM poin_disiplin WHERE id = :id")
    suspend fun delete(id: Long)
}

data class SiswaPoinRanking(
    @ColumnInfo(name = "siswa_id") val siswaId: Long,
    @ColumnInfo(name = "net_poin") val netPoin: Int,
)
