package com.absenku.ui.jadwal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.absenku.data.model.JadwalPelajaran
import com.absenku.data.model.Kelas
import com.absenku.data.model.Mapel
import com.absenku.data.repository.Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JadwalUiState(
    val allJadwal: List<JadwalPelajaran> = emptyList(),
    val allKelas: List<Kelas> = emptyList(),
    val allMapel: List<Mapel> = emptyList(),
    val selectedKelasId: Long? = null,
    val isLoading: Boolean = true,
    val errorMsg: String? = null,
)

@HiltViewModel
class JadwalPelajaranViewModel @Inject constructor(
    private val repo: Repository,
) : ViewModel() {

    private val _state = MutableStateFlow(JadwalUiState())
    val state: StateFlow<JadwalUiState> = _state

    init { loadAll() }

    fun loadAll() {
        viewModelScope.launch {
            try {
                val kelas = repo.getAllKelas()
                val mapel = repo.getAllMapel()
                _state.value = _state.value.copy(allKelas = kelas, allMapel = mapel, isLoading = true)
                refreshJadwal()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, errorMsg = e.message)
            }
        }
    }

    fun filterByKelas(kelasId: Long?) {
        _state.value = _state.value.copy(selectedKelasId = kelasId)
        viewModelScope.launch { refreshJadwal() }
    }

    private suspend fun refreshJadwal() {
        val all = if (_state.value.selectedKelasId != null) {
            repo.getJadwalByKelas(_state.value.selectedKelasId!!)
        } else {
            repo.getAllJadwalActive()
        }
        _state.value = _state.value.copy(allJadwal = all, isLoading = false)
    }

    fun addJadwal(jadwal: JadwalPelajaran, onResult: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.addJadwal(jadwal)
            refreshJadwal()
            onResult(id)
        }
    }

    fun deleteJadwal(id: Long) {
        viewModelScope.launch {
            repo.softDeleteJadwal(id, System.currentTimeMillis())
            refreshJadwal()
        }
    }
}
