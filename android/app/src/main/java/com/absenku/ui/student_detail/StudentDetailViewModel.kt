package com.absenku.ui.student_detail

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.absenku.data.model.Absensi
import com.absenku.data.model.Nilai
import com.absenku.data.model.PoinDisiplin
import com.absenku.data.model.Siswa
import com.absenku.data.repository.Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Detail info about the student's kelas (resolved by ID). */
data class KelasInfo(
    val nama: String,
    val waliKelas: String? = null,
)

/** A single attendance summary row (by status). */
data class AttendanceStatusCount(
    val status: String,
    val count: Int,
)

/** Aggregated grade row for a subject. */
data class SubjectGradeInfo(
    val mapelId: Long,
    val mapelNama: String,
    val latestNilai: String,
    val avgNilai: Double,
    val jumlah: Int,
)

/** UI state for the student detail screen. */
data class StudentDetailUiState(
    val siswa: Siswa? = null,
    val kelasInfo: KelasInfo? = null,
    val totalAbsensi: Int = 0,
    val attendanceSummary: List<AttendanceStatusCount> = emptyList(),
    val recentAbsensi: List<Absensi> = emptyList(),
    val subjectGrades: List<SubjectGradeInfo> = emptyList(),
    val totalPoinPositif: Int = 0,
    val totalPoinNegatif: Int = 0,
    val recentPoin: List<PoinDisiplin> = emptyList(),
    val isLoading: Boolean = true,
    val errorMsg: String? = null,
    val printResult: String? = null,
)

@HiltViewModel
class StudentDetailViewModel @Inject constructor(
    private val repo: Repository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val siswaId: Long = savedStateHandle.get<Long>("siswaId") ?: 0L

    private val _state = MutableStateFlow(StudentDetailUiState())
    val state: StateFlow<StudentDetailUiState> = _state

    init {
        if (siswaId > 0) loadDetail() else _state.value = StudentDetailUiState(isLoading = false, errorMsg = "ID siswa tidak valid")
    }

    /** Reload all detail data for the student. */
    fun loadDetail() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMsg = null, printResult = null)
            try {
                val siswa = repo.getSiswaById(siswaId)
                if (siswa == null) {
                    _state.value = StudentDetailUiState(isLoading = false, errorMsg = "Siswa tidak ditemukan")
                    return@launch
                }

                val kelas = repo.getAllKelas().find { it.id == siswa.kelasId }

                // Attendance
                val absensiList = repo.getAbsensiBySiswa(siswaId)
                val summary = absensiList
                    .groupBy { it.status }
                    .map { (status, list) -> AttendanceStatusCount(status, list.size) }
                val recentAbsensi = absensiList.sortedByDescending { it.tanggal }.take(10)

                // Grades per subject
                val allMapel = repo.getAllMapel()
                val mapelById = allMapel.associateBy { it.id }
                val nilaiList = repo.getNilaiBySiswa(siswaId)
                val subjectGrades = nilaiList
                    .groupBy { it.mapelId }
                    .map { (mapelId, list) ->
                        val avg = list.mapNotNull { it.nilai.toDoubleOrNull() }
                            .takeIf { it.isNotEmpty() }
                            ?.average() ?: 0.0
                        SubjectGradeInfo(
                            mapelId = mapelId,
                            mapelNama = mapelById[mapelId]?.nama ?: "Mapel #$mapelId",
                            latestNilai = list.firstOrNull()?.nilai ?: "-",
                            avgNilai = avg,
                            jumlah = list.size,
                        )
                    }
                    .sortedByDescending { it.avgNilai }

                // Discipline points
                val poinPositif = repo.totalPoinPositif(siswaId)
                val poinNegatif = repo.totalPoinNegatif(siswaId)
                val recentPoin = repo.getPoinDisiplinBySiswa(siswaId).take(10)

                _state.value = StudentDetailUiState(
                    siswa = siswa,
                    kelasInfo = KelasInfo(nama = kelas?.nama ?: "Kelas #${siswa.kelasId}", waliKelas = kelas?.waliKelas),
                    totalAbsensi = absensiList.size,
                    attendanceSummary = summary,
                    recentAbsensi = recentAbsensi,
                    subjectGrades = subjectGrades,
                    totalPoinPositif = poinPositif,
                    totalPoinNegatif = poinNegatif,
                    recentPoin = recentPoin,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _state.value = StudentDetailUiState(isLoading = false, errorMsg = e.message)
            }
        }
    }

    /** Soft-delete (archive) the student. */
    fun archiveStudent(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.softDeleteSiswa(siswaId, System.currentTimeMillis())
                onDone()
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMsg = "Gagal mengarsipkan: ${e.message}")
            }
        }
    }

    /** Print student card as PDF via SAF. */
    fun printStudentCard(context: Context, output: Uri, schoolName: String?) {
        viewModelScope.launch {
            val s = _state.value.siswa ?: return@launch
            val kelasNama = _state.value.kelasInfo?.nama ?: ""
            val card = com.absenku.utils.StudentCardData(s.nis, s.nama, kelasNama, s.foto)
            val ok = com.absenku.utils.PdfGenerator.generateStudentCards(context, listOf(card), schoolName, output)
            _state.value = _state.value.copy(printResult = if (ok) "Kartu siswa berhasil dibuat!" else "Gagal membuat kartu siswa")
        }
    }

    /** Print student biodata PDF via SAF. */
    fun printBiodata(context: Context, output: Uri, schoolName: String?) {
        viewModelScope.launch {
            val s = _state.value.siswa ?: return@launch
            val kelasNama = _state.value.kelasInfo?.nama ?: ""
            val ok = com.absenku.utils.PdfGenerator.generateBiodata(
                context, s.nis, s.nama, kelasNama,
                birthDate = s.tanggalLahir, address = s.alamat, parentPhone = s.noHpOrtu,
                schoolName = schoolName, output = output
            )
            _state.value = _state.value.copy(printResult = if (ok) "Biodata berhasil dibuat!" else "Gagal membuat biodata")
        }
    }

    fun clearPrintResult() {
        _state.value = _state.value.copy(printResult = null)
    }
}
