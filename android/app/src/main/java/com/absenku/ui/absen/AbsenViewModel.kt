package com.absenku.ui.absen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.absenku.data.model.Absensi
import com.absenku.data.model.Siswa
import com.absenku.data.repository.Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/** Ui state for the attendance (scan) flow. */
data class AbsenUiState(
    val isLoading: Boolean = false,
    val todayAbsensi: List<Absensi> = emptyList(),
    val selectedSiswa: Siswa? = null,
    val showStatusDialog: Boolean = false,
    val scannedValue: String = "",
    val errorMsg: String? = null,
)

/** ViewModel — handles barcode scan → siswa lookup → duplicate check + saving attendance. */
@HiltViewModel
class AbsenViewModel @Inject constructor(
    private val repo: Repository,
) : ViewModel() {

    private val _state = MutableStateFlow(AbsenUiState())
    val state: StateFlow<AbsenUiState> = _state

    private var scanLockUntil: Long = 0L

    /** Called by the BarcodeScanner Composable when a code is detected. */
    fun onBarcodeScanned(raw: String) {
        // Debounce: lock scanner for 1.5s after a successful scan
        if (System.currentTimeMillis() < scanLockUntil) return

        val now = System.currentTimeMillis()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        viewModelScope.launch {
            // raw may include a prefix like "scanned:NIS123" — extract NIS
            val nis = raw.removePrefix("scanned:").takeIf { it.isNotBlank() } ?: raw
            val siswa = repo.getSiswaByNis(nis.trim())
            if (siswa == null) {
                _state.value = _state.value.copy(errorMsg = "NIS tidak ditemukan: $nis")
                return@launch
            }

            // Duplicate check: already absen today for this siswa/mapel
            val existing = repo.getBySiswaAndDate(siswa.id, today)
            if (existing.isNotEmpty()) {
                // lock scanner regardless (1.5s debounce applies to ALL scans)
                scanLockUntil = System.currentTimeMillis() + 1500
                _state.value = _state.value.copy(
                    selectedSiswa = siswa,
                    scannedValue = nis,
                    showStatusDialog = true,
                    errorMsg = "Sudah absen hari ini jam ${existing.first().waktuMasuk ?: "-"}. Timpa status?",
                )
            } else {
                scanLockUntil = System.currentTimeMillis() + 1500
                _state.value = _state.value.copy(
                    selectedSiswa = siswa,
                    scannedValue = nis,
                    showStatusDialog = true,
                    errorMsg = null,
                )
            }
        }
    }

    /** Set status: Hadir / Izin / Sakit / Alfa + record waktu masuk/keluar. */
    fun setStatus(absensiStatus: String) {
        val siswa = _state.value.selectedSiswa ?: return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val now = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        viewModelScope.launch {
            val existing = repo.getBySiswaAndDate(siswa.id, today)
            val mapelId = 0L // default/general absence (mapel-specific when teacher chooses)
            if (existing.isNotEmpty()) {
                // update (overwrite) last occurrence + audit log
                val old = existing.first()
                repo.updateAbsensi(Absensi(id = old.id, siswa_id = old.siswaId, tanggal = today, waktuMasuk = old.waktuMasuk, waktuKeluar = if (absensiStatus=="Keluar") now else old.waktuKeluar, status = absensiStatus, mapelId = old.mapelId, createdAt = old.createdAt, updatedAt = System.currentTimeMillis()))
                repo.logAudit(
                    com.absenku.data.model.AuditLog(tableName = "absensi", recordId = old.id, fieldName = "status",
                        oldValue = old.status, newValue = absensiStatus, changedBy = "HP-scan")
                )
                // update only changed status (overwrite) keeps first-entry waktu_masuk.
                // To *replace*, caller may call deleteAbsensi then addAbsensi — handled here:
                repo.deleteAbsensi(old.id)
                repo.addAbsensi(Absensi(siswaId = siswa.id, tanggal = today, waktuMasuk = now, status = absensiStatus, mapelId = mapelId))
            } else {
                repo.addAbsensi(Absensi(siswaId = siswa.id, tanggal = today, waktuMasuk = now, status = absensiStatus, mapelId = mapelId))
            }
            _state.value = _state.value.copy(selectedSiswa = null, showStatusDialog = false)
            loadToday()
        }
    }

    /** Manual pick from student list — same path as scan. */
    fun selectManual(siswa: Siswa) {
        _state.value = _state.value.copy(selectedSiswa = siswa, showStatusDialog = true, scannedValue = siswa.nis, errorMsg = null)
    }

    fun dismissDialog() {
        _state.value = _state.value.copy(selectedSiswa = null, showStatusDialog = false)
    }

    fun loadToday() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        viewModelScope.launch {
            _state.value = _state.value.copy(
                todayAbsensi = repo.getByDateAbsensi(today),
                isLoading = false,
            )
        }
    }

    /** Manual entry mode — add a row without scanning. */
    fun addManual(nis: String) {
        viewModelScope.launch {
            val s = repo.getSiswaByNis(nis.trim())
            if (s == null) {
                _state.value = _state.value.copy(errorMsg = "NIS tidak ditemukan")
            } else {
                _state.value = _state.value.copy(selectedSiswa = s, showStatusDialog = true, scannedValue = nis, errorMsg = null)
            }
        }
    }
}
