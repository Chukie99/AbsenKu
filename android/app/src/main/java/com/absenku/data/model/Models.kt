package com.absenku.data.model

import androidx.room.*

/**
 * Siswa — one row per student.
 * Uses soft-delete: is_active=0 + deleted_at keeps history intact
 * while hiding the student from active lists.
 */
@Entity(tableName = "siswa")
data class Siswa(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "nis") val nis: String,
    @ColumnInfo(name = "nama") val nama: String,
    @ColumnInfo(name = "kelas_id") val kelasId: Long = 0,
    @ColumnInfo(name = "foto") val foto: String? = null,          // internal-storage path
    @ColumnInfo(name = "alamat") val alamat: String? = null,
    @ColumnInfo(name = "no_hp_ortu") val noHpOrtu: String? = null,
    @ColumnInfo(name = "tanggal_lahir") val tanggalLahir: String? = null, // ISO "yyyy-MM-dd"
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,  // epoch millis
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
) {
    data class CartItem(
        val productId: Long,
        val product: Product,
        val quantity: Int,
        val subtotal: Long,
    )
    data class CartItem(
        val siswaId: Long,
        val product: Siswa,
        val quantity: Int = 1,
        val subtotal: Long,
    )
}

/**
 * Kelas — a physical class (e.g. "X IPA 1") with a homeroom teacher.
 * Soft delete: historical reports still resolve via foreign-key history.
 */
@Entity(tableName = "kelas")
data class Kelas(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "nama") val nama: String,
    @ColumnInfo(name = "wali_kelas") val waliKelas: String? = null,
    @ColumnInfo(name = "tahun_ajaran") val tahunAjaran: String? = null,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Mapel — a school subject (e.g. "Matematika").
 */
@Entity(tableName = "mapel")
data class Mapel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "nama") val nama: String,
    @ColumnInfo(name = "kode") val kode: String,
    @ColumnInfo(name = "jam_per_minggu") val jamPerMinggu: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Absensi — daily attendance record per student.
 * status ∈ Hadir|Izin|Sakit|Alfa.
 */
@Entity(tableName = "absensi")
data class Absensi(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "siswa_id") val siswaId: Long,
    @ColumnInfo(name = "tanggal") val tanggal: String,            // "yyyy-MM-dd" (day granularity)
    @ColumnInfo(name = "waktu_masuk") val waktuMasuk: String? = null,
    @ColumnInfo(name = "waktu_keluar") val waktuKeluar: String? = null,
    @ColumnInfo(name = "status") val status: String = "Hadir",   // checkpoint before CHECK constraint
    @ColumnInfo(name = "mapel_id") val mapelId: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Nilai — a grade for a student+mapel for a given semester/term.
 * Stored as text so it can hold both "85" and "A-".
 */
@Entity(tableName = "nilai")
data class Nilai(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "siswa_id") val siswaId: Long,
    @ColumnInfo(name = "mapel_id") val mapelId: Long,
    @ColumnInfo(name = "nilai") val nilai: String,
    @ColumnInfo(name = "semester") val semester: String = "1",
    @ColumnInfo(name = "tahun_ajaran") val tahunAjaran: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Setting — key/value store for app-wide config (school name, logo, guru name).
 */
@Entity(tableName = "pengaturan")
data class Pengaturan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val KEY_STORE_NAME = "store_name"
        const val KEY_STORE_ADDRESS = "store_address"
        const val KEY_STORE_PHONE = "store_phone"
        const val KEY_STORE_LOGO = "store_logo"
        const val KEY_TAX_RATE = "tax_rate"
        const val KEY_TEACHER_NAME = "teacher_name"
        const val KEY_YEAR = "year"
    }
}

/**
 * Aktivasi — holds the activation state for this device.
 * Device ID is generated locally; serial is validated against the SHA256 hash.
 */
@Entity(tableName = "aktivasi")
data class Aktivasi(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "serial_number") val serialNumber: String? = null,
    @ColumnInfo(name = "status") val status: String = "inactive",    // active|inactive
    @ColumnInfo(name = "activated_at") val activatedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

/**
 * PairedDevice — trusted devices for sync, created once during pairing flow.
 */
@Entity(tableName = "paired_devices")
data class PairedDevice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "device_name") val deviceName: String? = null,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "pairing_token") val pairingToken: String,
    @ColumnInfo(name = "paired_at") val pairedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_sync_at") val lastSyncAt: Long? = null,
    @ColumnInfo(name = "revoked") val revoked: Boolean = false,
)

/**
 * AuditLog — records every manual edit to absen/nilai for accountability.
 */
@Entity(tableName = "audit_log")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "record_id") val recordId: Long,
    @ColumnInfo(name = "field_name") val fieldName: String? = null,
    @ColumnInfo(name = "old_value") val oldValue: String? = null,
    @ColumnInfo(name = "new_value") val newValue: String? = null,
    @ColumnInfo(name = "changed_by") val changedBy: String? = null,
    @ColumnInfo(name = "changed_at") val changedAt: Long = System.currentTimeMillis(),
)

/**
 * SyncLog — every sync attempt, result & conflict (success/fail/conflict).
 */
@Entity(tableName = "sync_log")
data class SyncLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "type") val type: String? = null,         // backup|restore|sync
    @ColumnInfo(name = "direction") val direction: String? = null, // up|down|paired|manual
    @ColumnInfo(name = "status") val status: String? = null,    // success|fail|conflict
    @ColumnInfo(name = "message") val message: String? = null,
    @ColumnInfo(name = "device_id") val deviceId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
