package com.absenku.ui.siswa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.absenku.data.model.Kelas
import com.absenku.data.model.Siswa
import com.absenku.data.repository.Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ui state for the Student list. */
data class SiswaUiState(
    val all: List<Siswa> = emptyList(),
    val filtered: List<Siswa> = emptyList(),
    val allClasses: List<Kelas> = emptyList(),
    val selectedKelas: Long? = null,
    val search: String = "",
    val isLoading: Boolean = true,
    val errorMsg: String? = null,
)

/** ViewModel — lists, search, soft-delete, class filter. */
@HiltViewModel
class SiswaViewModel @Inject constructor(
    private val repo: Repository,
) : ViewModel() {

    private val _state = MutableStateFlow(SiswaUiState())
    val state: StateFlow<SiswaUiState> = _state

    init { loadAll() }

    fun loadAll() {
        viewModelScope.launch {
            val classes = repo.getAllKelas()
            _state.value = _state.value.copy(allClasses = classes, isLoading = true)
            applyFilters()
        }
    }

    fun search(q: String) {
        _state.value = _state.value.copy(search = q)
        applyFilters()
    }

    fun filterByKelas(kelasId: Long?) {
        _state.value = _state.value.copy(selectedKelas = kelasId)
        applyFilters()
    }

    private suspend fun applyFilters() {
        val all = if (_state.value.selectedKelas != null) {
            repo.getSiswaByKelas(_state.value.selectedKelas!!)
        } else {
            repo.getAllSiswa()
        }
        val filtered = if (_state.value.search.isBlank()) all
        else all.filter { it.nama.contains(_state.value.search, ignoreCase = true) || it.nis.contains(_state.value.search, ignoreCase = true) }
        _state.value = _state.value.copy(
            all = all,
            filtered = filtered,
            isLoading = false,
        )
    }

    fun softDelete(id: Long) {
        viewModelScope.launch {
            repo.softDeleteSiswa(id, System.currentTimeMillis())
            loadAll()
        }
    }
}
