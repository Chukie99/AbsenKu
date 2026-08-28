package com.absenku.data.database.dao

import androidx.room.*
import com.absenku.data.model.Pengaturan

/** CRUD for key/value settings. */
@Dao
interface PengaturanDao {
    @Query("SELECT * FROM pengaturan WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): Pengaturan?

    @Query("SELECT * FROM pengaturan ORDER BY `key`")
    suspend fun getAll(): List<Pengaturan>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(setting: Pengaturan)

    @Query("INSERT OR REPLACE INTO pengaturan (`key`, value) VALUES (?, ?)")
    suspend fun put(key: String, value: String?)

    @Update
    suspend fun update(setting: Pengaturan)
}
