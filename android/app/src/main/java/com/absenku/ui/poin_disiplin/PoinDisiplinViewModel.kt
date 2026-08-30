package com.absenku.ui.poin_disiplin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.absenku.data.model.PoinDisiplin
import com.absenku.data.model.Siswa
import com.absenku.data.repository.Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PoinDisiplinUiState(
    val allPoin: List<PoinDisiplin> = emptyList(),
    val filteredPoin: List<PoinDisiplin> = emptyList(),
    val allSiswa: List<Siswa> = emptyList(),
    val selectedSiswaId: Long? = null,
    val selectedKategori: String? = null,  // null=Semua, "Positif", "Negatif"
    val isLoading: Boolean = true,
    val errorMsg: String? = null,
)

@HiltViewModel
class PoinDisiplinViewModel @Inject constructor(
    private val repo: Repository,
) : ViewModel() {

    private val _state = MutableStateFlow(PoinDisiplinUiState())
    val state: StateFlow<PoinDisiplinUiState> = _state

    init { loadAll() }

    fun loadAll() {
        viewModelScope.launch {
            try {
                val siswa = repo.getAllSiswa()
                _state.value = _state.value.copy(allSiswa = siswa, isLoading = true)
                refreshPoin()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, errorMsg = e.message)
            }
        }
    }

    fun filterBySiswa(siswaId: Long?) {
        _state.value = _state.value.copy(selectedSiswaId = siswaId)
        viewModelScope.launch { refreshPoin() }
    }

    fun filterByKategori(kategori: String?) {
        _state.value = _state.value.copy(selectedKategori = kategori)
        viewModelScope.launch { refreshPoin() }
    }

    private suspend fun refreshPoin() {
        val all = repo.getAllPoinDisiplin()
        val filtered = all.filter { p ->
            (_state.value.selectedSiswaId == null || p.siswaId == _state.value.selectedSiswaId) &&
            (_state.value.selectedKategori == null || p.kategori == _state.value.selectedKategori)
        }
        _state.value = _state.value.copy(allPoin = all, filteredPoin = filtered, isLoading = false)
    }

    fun addPoinDisiplin(poin: PoinDisiplin, onResult: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.addPoinDisiplin(poin)
            refreshPoin()
            onResult(id)
        }
    }

    fun deletePoinDisiplin(id: Long) {
        viewModelScope.launch {
            repo.deletePoinDisiplin(id)
            refreshPoin()
        }
    }
}
