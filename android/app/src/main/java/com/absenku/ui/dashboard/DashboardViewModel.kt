package com.absenku.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.absenku.data.model.Absensi
import com.absenku.data.repository.Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/** Ui state — daily summary counts + weekly bar chart entries. */
data class DashboardUiState(
    val todayHadir: Int = 0,
    val todayIzin: Int = 0,
    val todaySakit: Int = 0,
    val todayAlfa: Int = 0,
    val weekly: List<WeeklyEntry> = emptyList(),
    val isLoading: Boolean = true,
    val errorMsg: String? = null,
)

data class WeeklyEntry(val day: String, val count: Int)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: Repository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState(isLoading = true))
    val state: StateFlow<DashboardUiState> = _state

    init { loadToday() }

    fun loadToday() {
        viewModelScope.launch {
            try {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val list = repo.getByDateAbsensi(today)
                _state.value = DashboardUiState(
                    todayHadir = list.count { it.status == "Hadir" },
                    todayIzin = list.count { it.status == "Izin" },
                    todaySakit = list.count { it.status == "Sakit" },
                    todayAlfa = list.count { it.status == "Alfa" },
                    weekly = buildWeekly(),
                    isLoading = false,
                )
            } catch (e: Exception) {
                _state.value = DashboardUiState(isLoading = false, errorMsg = e.message)
            }
        }
    }

    /** Build 7-day bar chart of attendance counts. */
    private suspend fun buildWeekly(): List<WeeklyEntry> {
        val cal = Calendar.getInstance()
        val entries = mutableListOf<WeeklyEntry>()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayFmt = SimpleDateFormat("EE", Locale("id", "ID"))
        for (i in 6 downTo 0) {
            cal.timeInMillis = System.currentTimeMillis()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val date = fmt.format(cal.time)
            val day = dayFmt.format(cal.time)
            val count = repo.getByDateAbsensi(date).size
            entries.add(WeeklyEntry(day, count))
        }
        return entries
    }
}

// Extension to access repo — added to Repository
