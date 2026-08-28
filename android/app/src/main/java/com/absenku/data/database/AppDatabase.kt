package com.absenku.data.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.absenku.data.database.dao.*
import com.absenku.data.model.*

/**
 * Room database for AbsenKu.
 * Holds 9 tables: siswa, kelas, mapel, absensi, nilai, pengaturan,
 * aktivasi, paired_devices, audit_log, sync_log.
 * Singleton (per the existing pattern in KasirPro).
 */
@Database(
    entities = [
        Siswa::class, Kelas::class, Mapel::class,
        Absensi::class, Nilai::class, Pengaturan::class,
        Aktivasi::class, PairedDevice::class, AuditLog::class, SyncLog::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun siswaDao(): SiswaDao
    abstract fun kelasDao(): KelasDao
    abstract fun mapelDao(): MapelDao
    abstract fun absensiDao(): AbsensiDao
    abstract fun nilaiDao(): NilaiDao
    abstract fun pengaturanDao(): PengaturanDao
    abstract fun aktivasiDao(): AktivasiDao
    abstract fun pairedDeviceDao(): PairedDeviceDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun syncLogDao(): SyncLogDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java, "absenku.db"
                ).addMigrations(MIGRATION_1_2) // placeholder for future migrations
                 .build()
                 .also { INSTANCE = it }
            }

        /** Migration stub to add new columns later without losing data. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // no-op: reserved for future schema changes
            }
        }
    }
}
