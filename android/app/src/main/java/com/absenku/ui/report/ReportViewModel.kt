package com.absenku.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.absenku.data.model.Absensi
import com.absenku.data.model.Siswa
import com.absenku.data.model.SyncLog
import com.absenku.data.database.dao.SiswaPoinRanking
import com.absenku.data.repository.Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportUiState(
    val todayAbsensi: List<Absensi> = emptyList(),
    val syncLogs: List<SyncLog> = emptyList(),
    val rankingPoin: List<SiswaPoinRanking> = emptyList(),
    val rankingPoinIndexed: List<Pair<Int, SiswaPoinRanking>> = emptyList(),
    val allSiswaMap: Map<Long, Siswa> = emptyMap(),
    val isLoading: Boolean = false,
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val repo: Repository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state

    fun loadByDate(tanggal: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, todayAbsensi = repo.getByDateAbsensi(tanggal))
            _state.value = _state.value.copy(isLoading = false, syncLogs = repo.getSyncLog())
        }
    }

    fun loadRanking() {
        viewModelScope.launch {
            val ranking = repo.getRankingByPoinAll()
            val allSiswa = repo.getAllSiswa()
            val siswaMap = allSiswa.associateBy { it.id }
            _state.value = _state.value.copy(
                rankingPoin = ranking,
                rankingPoinIndexed = ranking.mapIndexed { index, item -> Pair(index + 1, item) },
                allSiswaMap = siswaMap,
            )
        }
    }
}
