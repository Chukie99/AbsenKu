package com.absenku.data.repository

import com.absenku.data.database.AppDatabase
import com.absenku.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository — single access point for all data operations.
 * Uses the singleton AppDatabase instance (same pattern as KasirPro).
 */
class Repository private constructor(private val db: AppDatabase) {

    // ── SISWA ──
    suspend fun getAllSiswa(): List<Siswa> = db.siswaDao().getAllActive()
    suspend fun getSiswaByKelas(kelasId: Long): List<Siswa> = db.siswaDao().getByKelas(kelasId)
    suspend fun getSiswaById(id: Long): Siswa? = db.siswaDao().getById(id)
    suspend fun getSiswaByNis(nis: String): Siswa? = db.siswaDao().getByNis(nis)
    suspend fun searchSiswa(q: String): List<Siswa> = db.siswaDao().search(q)
    suspend fun addSiswa(siswa: Siswa): Long = db.siswaDao().insert(siswa)
    suspend fun updateSiswa(siswa: Siswa) = db.siswaDao().update(siswa)
    suspend fun softDeleteSiswa(id: Long, deletedAt: Long) = db.siswaDao().softDelete(id, deletedAt)

    // ── KELAS ──
    suspend fun getAllKelas(): List<Kelas> = db.kelasDao().getAllActive()
    suspend fun addKelas(kelas: Kelas): Long = db.kelasDao().insert(kelas)
    suspend fun updateKelas(kelas: Kelas) = db.kelasDao().update(kelas)
    suspend fun softDeleteKelas(id: Long, deletedAt: Long) = db.kelasDao().softDelete(id, deletedAt)

    // ── MAPEL ──
    suspend fun getAllMapel(): List<Mapel> = db.mapelDao().getAll()
    suspend fun addMapel(mapel: Mapel): Long = db.mapelDao().insert(mapel)
    suspend fun updateMapel(mapel: Mapel) = db.mapelDao().update(mapel)

    // ── ABSENSI ──
    suspend fun getByDateAbsensi(tanggal: String): List<Absensi> = db.absensiDao().getByDate(tanggal)
    fun getAbsensiByDate(tanggal: String): List<Absensi> = db.absensiDao().getByDate(tanggal)  // alias
    suspend fun getBySiswaAndDate(siswaId: Long, tanggal: String) = db.absensiDao().getBySiswaAndDate(siswaId, tanggal)
    suspend fun getAbsensiBySiswa(id: Long): List<Absensi> = db.absensiDao().getBySiswa(id)
    suspend fun addAbsensi(absensi: Absensi): Long = db.absensiDao().insert(absensi)
    suspend fun updateAbsensi(absensi: Absensi) = db.absensiDao().update(absensi)
    suspend fun deleteAbsensi(id: Long) = db.absensiDao().delete(id)

    // ── NILAI ──
    suspend fun getNilaiBySiswaMapel(siswaId: Long, mapelId: Long): List<Nilai> = db.nilaiDao().getBySiswaMapel(siswaId, mapelId)
    suspend fun addNilai(nilai: Nilai): Long = db.nilaiDao().insert(nilai)
    suspend fun updateNilai(nilai: Nilai) = db.nilaiDao().update(nilai)

    // ── PENGATURAN ──
    suspend fun getSetting(key: String): Pengaturan? = db.pengaturanDao().getByKey(key)
    suspend fun putSetting(key: String, value: String?) = db.pengaturanDao().put(key, value)

    // ── AKTIVASI ──
    suspend fun getAktivasi(): Aktivasi? = db.aktivasiDao().get()
    suspend fun addAktivasi(aktivasi: Aktivasi): Long = db.aktivasiDao().insert(aktivasi)
    suspend fun updateAktivasi(aktivasi: Aktivasi) = db.aktivasiDao().update(aktivasi)

    // ── PAIRED DEVICES (sync) ──
    suspend fun getPairedByToken(token: String): PairedDevice? = db.pairedDeviceDao().getByToken(token)
    suspend fun getPairedByDeviceId(deviceId: String): PairedDevice? = db.pairedDeviceDao().getByDeviceId(deviceId)
    suspend fun addPairedDevice(device: PairedDevice): Long = db.pairedDeviceDao().insert(device)
    suspend fun revokePairedDevice(id: Long) = db.pairedDeviceDao().revoke(id)
    suspend fun getAllPaired(): List<PairedDevice> = db.pairedDeviceDao().getAllActive()
    suspend fun updatePairedDevice(device: PairedDevice) = db.pairedDeviceDao().update(device)

    // ── AUDIT & SYNC LOG ──
    suspend fun logAudit(log: AuditLog) = db.auditLogDao().insert(log)
    suspend fun getAuditLog(): List<AuditLog> = db.auditLogDao().getRecent()
    suspend fun logSync(log: SyncLog) = db.syncLogDao().insert(log)
    suspend fun getSyncLog(): List<SyncLog> = db.syncLogDao().getRecent()

    // ── EXPORT FLOW ──
    fun getAllSiswaIncludingInactive(): List<Siswa> = db.siswaDao().getAll()

    companion object {
        @Volatile private var INSTANCE: Repository? = null
        fun getInstance(db: AppDatabase): Repository =
            INSTANCE ?: synchronized(this) { INSTANCE ?: Repository(db).also { INSTANCE = it } }
    }
}
