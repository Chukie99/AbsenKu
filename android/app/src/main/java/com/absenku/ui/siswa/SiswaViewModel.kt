package com.absenku.ui.siswa

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.absenku.data.model.Kelas
import com.absenku.data.model.Siswa
import com.absenku.data.repository.Repository
import com.absenku.utils.CsvImporter
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
    val importResult: String? = null,
)
/** ViewModel — lists, search, soft-delete, class filter, CSV import. */
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
        viewModelScope.launch { applyFilters() }
    }

    fun filterByKelas(kelasId: Long?) {
        _state.value = _state.value.copy(selectedKelas = kelasId)
        viewModelScope.launch { applyFilters() }
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

    fun addSiswa(siswa: Siswa, onResult: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.addSiswa(siswa)
            loadAll()
            onResult(id)
        }
    }

    fun loadSiswaById(id: Long, onResult: (Siswa?) -> Unit) {
        viewModelScope.launch {
            val siswa = repo.getSiswaById(id)
            onResult(siswa)
        }
    }

    fun updateSiswa(siswa: Siswa, onResult: () -> Unit = {}) {
        viewModelScope.launch {
            repo.updateSiswa(siswa)
            loadAll()
            onResult()
        }
    }

    /** Import siswa from a CSV file URI. */
    fun importCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, importResult = null)
            try {
                val result = CsvImporter.importSiswa(context, uri)
                var imported = 0
                var skipped = 0
                for (siswa in result.siswaList) {
                    // Check for duplicate NIS
                    val existing = repo.getSiswaByNis(siswa.nis)
                    if (existing == null) {
                        repo.addSiswa(siswa)
                        imported++
                    } else {
                        skipped++
                    }
                }
                loadAll()
                val msg = buildString {
                    append("Import: $imported siswa ditambahkan")
                    if (skipped > 0) append(", $skipped duplikat dilewati")
                    if (result.errors.isNotEmpty()) append(", ${result.errors.size} error")
                }
                _state.value = _state.value.copy(importResult = msg)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, importResult = "Gagal import: ${e.message}")
            }
        }
    }

    fun clearImportResult() {
        _state.value = _state.value.copy(importResult = null)
    }
}
