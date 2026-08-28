package com.absenku.data.database.dao

import androidx.room.*
import com.absenku.data.model.Mapel

/** CRUD for Mata Pelajaran. */
@Dao
interface MapelDao {
    @Query("SELECT * FROM mapel ORDER BY nama")
    suspend fun getAll(): List<Mapel>

    @Query("SELECT * FROM mapel WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Mapel?

    @Insert
    suspend fun insert(mapel: Mapel): Long

    @Update
    suspend fun update(mapel: Mapel)
}
