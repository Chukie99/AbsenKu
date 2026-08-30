package com.absenku.data.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.absenku.data.database.dao.*
import com.absenku.data.model.*

/**
 * Room database for AbsenKu.
 * Holds 12 tables: siswa, kelas, mapel, absensi, nilai, pengaturan,
 * aktivasi, paired_devices, audit_log, sync_log, poin_disiplin, jadwal_pelajaran.
 */
@Database(
    entities = [
        Siswa::class, Kelas::class, Mapel::class,
        Absensi::class, Nilai::class, Pengaturan::class,
        Aktivasi::class, PairedDevice::class, AuditLog::class, SyncLog::class,
        PoinDisiplin::class, JadwalPelajaran::class,
    ],
    version = 2,
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
    abstract fun poinDisiplinDao(): PoinDisiplinDao
    abstract fun jadwalPelajaranDao(): JadwalPelajaranDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java, "absenku.db"
                ).addMigrations(MIGRATION_1_2)
                 .build()
                 .also { INSTANCE = it }
            }

        /** Migration 1→2: add poin_disiplin and jadwal_pelajaran tables. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `poin_disiplin` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `siswa_id` INTEGER NOT NULL,
                        `tanggal` TEXT NOT NULL,
                        `kategori` TEXT NOT NULL,
                        `poin` INTEGER NOT NULL DEFAULT 0,
                        `keterangan` TEXT,
                        `diberikan_oleh` TEXT,
                        `created_at` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0
                    )
                """)
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `jadwal_pelajaran` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `kelas_id` INTEGER NOT NULL,
                        `mapel_id` INTEGER NOT NULL,
                        `hari` TEXT NOT NULL,
                        `jam_mulai` TEXT NOT NULL,
                        `jam_selesai` TEXT NOT NULL,
                        `guru` TEXT,
                        `is_active` INTEGER NOT NULL DEFAULT 1,
                        `deleted_at` INTEGER,
                        `created_at` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_poin_disiplin_siswa_id` ON `poin_disiplin` (`siswa_id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_jadwal_pelajaran_kelas_id` ON `jadwal_pelajaran` (`kelas_id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_jadwal_pelajaran_hari` ON `jadwal_pelajaran` (`hari`)")
            }
        }
    }
}
