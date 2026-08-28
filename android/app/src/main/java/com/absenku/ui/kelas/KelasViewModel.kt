package com.absenku.ui.kelas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.absenku.data.model.Kelas
import com.absenku.data.repository.Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ui state for the Kelas list. */
data class KelasUiState(
    val allClasses: List<Kelas> = emptyList(),
    val active: List<Kelas> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class KelasViewModel @Inject constructor(
    private val repo: Repository,
) : ViewModel() {

    private val _state = MutableStateFlow(KelasUiState())
    val state: StateFlow<KelasUiState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            val all = repo.getAllKelas()
            _state.value = KelasUiState(allClasses = all, active = all.filter { it.isActive }, isLoading = false)
        }
    }

    fun addKelas(nama: String, wali: String, tahunAjaran: String) {
        viewModelScope.launch {
            repo.addKelas(Kelas(nama = nama, waliKelas = wali, tahunAjaran = tahunAjaran))
            load()
        }
    }

    fun softDelete(id: Long) {
        viewModelScope.launch {
            repo.softDeleteKelas(id, System.currentTimeMillis())
            load()
        }
    }
}
