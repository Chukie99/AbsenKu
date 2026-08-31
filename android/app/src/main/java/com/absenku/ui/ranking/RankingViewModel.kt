package com.absenku.ui.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.absenku.data.model.Siswa
import com.absenku.data.repository.Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A single ranking entry for the leaderboard. */
data class RankingEntry(
    val rank: Int,
    val siswa: Siswa,
    val avgNilai: Double,
    val jumlahNilai: Int,
)

/** A single discipline point ranking entry. */
data class PoinRankingEntry(
    val rank: Int,
    val siswa: Siswa,
    val netPoin: Int,
)

/** Tab selection: NILAI (grade-based) or POIN (discipline-based). */
enum class RankingTab { NILAI, POIN }

/** UI state for ranking screen. */
data class RankingUiState(
    val selectedTab: RankingTab = RankingTab.NILAI,
    val nilairanking: List<RankingEntry> = emptyList(),
    val poinRanking: List<PoinRankingEntry> = emptyList(),
    val selectedMapelId: Long? = null,  // null = all subjects
    val allMapelNames: List<Pair<Long, String>> = emptyList(),
    val isLoading: Boolean = true,
    val errorMsg: String? = null,
)

@HiltViewModel
class RankingViewModel @Inject constructor(
    private val repo: Repository,
) : ViewModel() {

    private val _state = MutableStateFlow(RankingUiState())
    val state: StateFlow<RankingUiState> = _state

    init { loadRanking() }

    fun loadRanking() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMsg = null)
            try {
                val allMapel = repo.getAllMapel()
                _state.value = _state.value.copy(allMapelNames = allMapel.map { it.id to it.nama })

                loadNilaiRanking()
                loadPoinRanking()
                _state.value = _state.value.copy(isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, errorMsg = e.message)
            }
        }
    }

    fun selectTab(tab: RankingTab) {
        _state.value = _state.value.copy(selectedTab = tab)
    }

    fun selectMapel(mapelId: Long?) {
        _state.value = _state.value.copy(selectedMapelId = mapelId)
        viewModelScope.launch { loadNilaiRanking() }
    }

    private suspend fun loadNilaiRanking() {
        val mapelId = _state.value.selectedMapelId
        val rankingData = if (mapelId != null) {
            repo.getRankingByNilai(mapelId)
        } else {
            repo.getRankingAllNilai()
        }
        val allSiswa = repo.getAllSiswa().associateBy { it.id }

        val entries = rankingData
            .filter { allSiswa.containsKey(it.siswaId) }
            .mapIndexed { index, r ->
                RankingEntry(
                    rank = index + 1,
                    siswa = allSiswa[r.siswaId]!!,
                    avgNilai = r.avgNilai,
                    jumlahNilai = r.jumlah,
                )
            }
        _state.value = _state.value.copy(nilairanking = entries)
    }

    private suspend fun loadPoinRanking() {
        val poinData = repo.getRankingByPoinAll()
        val allSiswa = repo.getAllSiswa().associateBy { it.id }

        val entries = poinData
            .filter { allSiswa.containsKey(it.siswaId) }
            .mapIndexed { index, p ->
                PoinRankingEntry(
                    rank = index + 1,
                    siswa = allSiswa[p.siswaId]!!,
                    netPoin = p.netPoin,
                )
            }
        _state.value = _state.value.copy(poinRanking = entries)
    }
}
