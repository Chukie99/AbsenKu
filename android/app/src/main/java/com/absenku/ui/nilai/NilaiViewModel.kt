package com.absenku.ui.nilai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.absenku.data.database.dao.MapelAvgNilai
import com.absenku.data.model.Mapel
import com.absenku.data.model.Nilai
import com.absenku.data.model.Siswa
import com.absenku.data.repository.Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NilaiChartEntry(
    val label: String,
    val value: Float,
    val count: Int,
)

data class NilaiUiState(
    val nilai: List<Nilai> = emptyList(),
    val allSiswa: List<Siswa> = emptyList(),
    val allMapel: List<Mapel> = emptyList(),
    val chartData: List<NilaiChartEntry> = emptyList(),
    val avgPerMapel: List<MapelAvgNilai> = emptyList(),
    val selectedMapelId: Long? = null,
    val isLoading: Boolean = true,
    val errorMsg: String? = null,
)

@HiltViewModel
class NilaiViewModel @Inject constructor(
    private val repo: Repository,
) : ViewModel() {

    private val _state = MutableStateFlow(NilaiUiState())
    val state: StateFlow<NilaiUiState> = _state

    init { loadAll() }

    fun loadAll() {
        viewModelScope.launch {
            try {
                val siswa = repo.getAllSiswa()
                val mapel = repo.getAllMapel()
                _state.value = _state.value.copy(allSiswa = siswa, allMapel = mapel, isLoading = true)
                loadChartData()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, errorMsg = e.message)
            }
        }
    }

    fun loadChartData(mapelId: Long? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(selectedMapelId = mapelId)
            val avgPerMapel = repo.getAvgNilaiByMapel()
            val chartEntries = avgPerMapel.map { avg ->
                val namaMapel = _state.value.allMapel.find { it.id == avg.mapelId }?.nama ?: "Mapel#${avg.mapelId}"
                NilaiChartEntry(
                    label = namaMapel,
                    value = avg.avgNilai.toFloat(),
                    count = avg.jumlah,
                )
            }
            _state.value = _state.value.copy(
                avgPerMapel = avgPerMapel,
                chartData = chartEntries,
                isLoading = false,
            )
        }
    }

    fun loadBySiswa(siswaId: Long) {
        viewModelScope.launch {
            val list = repo.getNilaiBySiswa(siswaId)
            _state.value = _state.value.copy(nilai = list, isLoading = false)
        }
    }

    /** Input / edit nilai — logs old→new value in audit_log. */
    fun saveNilai(siswaId: Long, mapelId: Long, nilaiStr: String, semester: String, tahunAjaran: String) {
        viewModelScope.launch {
            val existing = repo.getNilaiBySiswaMapel(siswaId, mapelId)
            if (existing.isNotEmpty()) {
                val old = existing.first()
                repo.updateNilai(old.copy(nilai = nilaiStr, semester = semester, tahunAjaran = tahunAjaran, updatedAt = System.currentTimeMillis()))
                repo.logAudit(
                    com.absenku.data.model.AuditLog(tableName = "nilai", recordId = old.id, fieldName = "nilai",
                        oldValue = old.nilai, newValue = nilaiStr, changedBy = "HP-nilai")
                )
            } else {
                repo.addNilai(Nilai(siswaId = siswaId, mapelId = mapelId, nilai = nilaiStr, semester = semester, tahunAjaran = tahunAjaran))
            }
        }
    }
}
