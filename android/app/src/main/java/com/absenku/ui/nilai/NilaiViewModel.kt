package com.absenku.ui.nilai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.absenku.data.model.Nilai
import com.absenku.data.repository.Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** ViewModel — input & rekap nilai with audit-log. */
@HiltViewModel
class NilaiViewModel @Inject constructor(
    private val repo: Repository,
) : ViewModel() {

    private val _state = MutableStateFlow(NilaiUiState())
    val state: StateFlow<NilaiUiState> = _state

    fun loadBySiswa(siswaId: Long) {
        viewModelScope.launch {
            val list = repo.getAllMapel() // need per-mapel join — handled in repo
            _state.value = _state.value.copy(nilai = emptyList(), isLoading = false)
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

data class NilaiUiState(
    val nilai: List<Nilai> = emptyList(),
    val isLoading: Boolean = true,
)
