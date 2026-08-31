package com.absenku.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.absenku.data.repository.Repository
import com.absenku.data.model.Absensi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

enum class ResultType { SUCCESS, OFFLINE, ERROR }

data class QrScannerUiState(
    val isProcessing: Boolean = false,
    val resultMessage: String? = null,
    val resultType: ResultType? = null,
    val lastScannedNis: String? = null
)

/**
 * QrScannerViewModel — handles barcode scan → student lookup → attendance save.
 * 
 * QR Code format (matches KelasFun):
 * {"n": "NIS", "nama": "Student Name", "k": "Class Name"}
 * 
 * Also supports plain NIS text.
 */
@HiltViewModel
class QrScannerViewModel @Inject constructor(
    private val repository: Repository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(QrScannerUiState())
    val uiState: StateFlow<QrScannerUiState> = _uiState.asStateFlow()
    
    private var lastScanTime = 0L
    private val debounceMs = 2000L // 2 seconds between scans
    
    fun onBarcodeScanned(rawValue: String) {
        val now = System.currentTimeMillis()
        if (now - lastScanTime < debounceMs) return
        if (_uiState.value.isProcessing) return
        
        lastScanTime = now
        _uiState.value = _uiState.value.copy(isProcessing = true)
        
        viewModelScope.launch {
            try {
                val nis = extractNis(rawValue)
                if (nis == null) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        resultMessage = "QR tidak valid",
                        resultType = ResultType.ERROR
                    )
                    delayAndClear()
                    return@launch
                }
                
                // Check if same student scanned recently
                if (nis == _uiState.value.lastScannedNis) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        resultMessage = "Siswa ini sudah di-scan",
                        resultType = ResultType.ERROR
                    )
                    delayAndClear()
                    return@launch
                }
                
                // Look up student by NIS
                val allSiswa = repository.getAllSiswa()
                val siswa = allSiswa.find { it.nis == nis }
                
                if (siswa == null) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        resultMessage = "Siswa dengan NIS $nis tidak ditemukan",
                        resultType = ResultType.ERROR
                    )
                    delayAndClear()
                    return@launch
                }
                
                // Check if already marked today
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val existingAbsen = repository.getByDateAbsensi(today)
                val alreadyMarked = existingAbsen.any { 
                    it.siswaId == siswa.id && it.status == "Hadir" 
                }
                
                if (alreadyMarked) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        resultMessage = "${siswa.nama} sudah hadir hari ini",
                        resultType = ResultType.ERROR
                    )
                    delayAndClear()
                    return@launch
                }
                
                // Save attendance
                val absensi = Absensi(
                    siswaId = siswa.id,
                    tanggal = today,
                    waktuMasuk = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                    status = "Hadir"
                )
                repository.addAbsensi(absensi)
                
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    resultMessage = "✅ ${siswa.nama} - Hadir",
                    resultType = ResultType.SUCCESS,
                    lastScannedNis = nis
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    resultMessage = "Error: ${e.message}",
                    resultType = ResultType.ERROR
                )
            }
            delayAndClear()
        }
    }
    
    /**
     * Extract NIS from barcode value.
     * Supports:
     * - JSON format: {"n": "NIS", "nama": "...", "k": "..."}
     * - Plain NIS text
     */
    private fun extractNis(rawValue: String): String? {
        return try {
            // Try JSON format first (KelasFun compatible)
            if (rawValue.trimStart().startsWith("{")) {
                val json = JSONObject(rawValue)
                json.getString("n")?.takeIf { it.isNotBlank() }
            } else {
                // Plain NIS text
                rawValue.trim().takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            // If JSON parsing fails, treat as plain NIS
            rawValue.trim().takeIf { it.isNotBlank() }
        }
    }
    
    private suspend fun delayAndClear() {
        kotlinx.coroutines.delay(2000)
        _uiState.value = _uiState.value.copy(resultMessage = null, resultType = null)
    }
}
