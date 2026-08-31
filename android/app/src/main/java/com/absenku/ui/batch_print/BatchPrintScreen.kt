package com.absenku.ui.batch_print

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.absenku.data.model.Kelas
import com.absenku.data.repository.Repository
import com.absenku.utils.BatchPrintManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for batch print screen. */
data class BatchPrintUiState(
    val kelas: Kelas? = null,
    val totalStudents: Int = 0,
    val progress: BatchPrintManager.BatchProgress? = null,
    val lastFile: java.io.File? = null,
    val isLoading: Boolean = false,
    val errorMsg: String? = null,
)

@HiltViewModel
class BatchPrintViewModel @Inject constructor(
    private val repo: Repository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val kelasId: Long = savedStateHandle.get<Long>("kelasId") ?: 0L
    private val _state = MutableStateFlow(BatchPrintUiState())
    val state: StateFlow<BatchPrintUiState> = _state

    init {
        viewModelScope.launch {
            val kelasList = repo.getAllKelas()
            val kelas = kelasList.find { it.id == kelasId }
            val students = repo.getSiswaByKelas(kelasId)
            _state.value = BatchPrintUiState(kelas = kelas, totalStudents = students.size)
        }
    }

    fun generateStudentCards(context: Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMsg = null)
            val schoolName = runCatching { repo.getSetting("school_name")?.value }.getOrNull()
            val file = BatchPrintManager.generateStudentCardsBatch(repo, kelasId, schoolName) { progress ->
                _state.value = _state.value.copy(progress = progress)
            }
            if (file != null) {
                _state.value = _state.value.copy(isLoading = false, lastFile = file, progress = BatchPrintManager.BatchProgress(total = 0, processed = 0, isComplete = true, phase = "Selesai!"))
                Toast.makeText(context, "Kartu siswa berhasil dibuat: ${file.name}", Toast.LENGTH_LONG).show()
            } else {
                _state.value = _state.value.copy(isLoading = false, errorMsg = "Gagal membuat kartu siswa")
            }
        }
    }

    fun generateBiodata(context: Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMsg = null)
            val schoolName = runCatching { repo.getSetting("school_name")?.value }.getOrNull()
            val file = BatchPrintManager.generateBiodataBatch(repo, kelasId, schoolName, context) { progress ->
                _state.value = _state.value.copy(progress = progress)
            }
            if (file != null) {
                _state.value = _state.value.copy(isLoading = false, lastFile = file, progress = BatchPrintManager.BatchProgress(total = 0, processed = 0, isComplete = true, phase = "Selesai!"))
                Toast.makeText(context, "Biodata berhasil dibuat: ${file.name}", Toast.LENGTH_LONG).show()
            } else {
                _state.value = _state.value.copy(isLoading = false, errorMsg = "Gagal membuat biodata")
            }
        }
    }

    fun generateExcel(context: Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMsg = null)
            val semester = runCatching {
                val now = java.util.Calendar.getInstance()
                val month = now.get(java.util.Calendar.MONTH) + 1
                val year = now.get(java.util.Calendar.YEAR)
                val ganjilGenap = if (month <= 6) "Ganjil" else "Genap"
                "$ganjilGenap $year/${year + 1}"
            }.getOrDefault("1 2025/2026")

            val file = BatchPrintManager.generateExcelBatch(repo, kelasId, semester) { progress ->
                _state.value = _state.value.copy(progress = progress)
            }
            if (file != null) {
                _state.value = _state.value.copy(isLoading = false, lastFile = file, progress = BatchPrintManager.BatchProgress(total = 0, processed = 0, isComplete = true, phase = "Selesai!"))
                Toast.makeText(context, "Excel berhasil dibuat: ${file.name}", Toast.LENGTH_LONG).show()
            } else {
                _state.value = _state.value.copy(isLoading = false, errorMsg = "Gagal membuat Excel")
            }
        }
    }
}

/**
 * BatchPrintScreen — select print type and generate batch PDF/Excel for an entire class.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchPrintScreen(
    kelasId: Long,
    onBack: () -> Unit,
    viewModel: BatchPrintViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cetak Batch") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Kembali") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Class info card
            Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Info Kelas", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    InfoRow("Kelas", s.kelas?.nama ?: "Kelas #$kelasId")
                    s.kelas?.waliKelas?.let { InfoRow("Wali Kelas", it) }
                    InfoRow("Jumlah Siswa", "${s.totalStudents} orang")
                }
            }

            // Print actions
            Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pilih Jenis Cetak", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    Button(
                        onClick = { viewModel.generateStudentCards(ctx) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !s.isLoading && s.totalStudents > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Cetak Kartu Siswa (PDF)")
                    }

                    OutlinedButton(
                        onClick = { viewModel.generateBiodata(ctx) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !s.isLoading && s.totalStudents > 0,
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Cetak Biodata (PDF A4)")
                    }

                    OutlinedButton(
                        onClick = { viewModel.generateExcel(ctx) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !s.isLoading && s.totalStudents > 0,
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Export Rapor (Excel)")
                    }
                }
            }

            // Progress indicator
            if (s.isLoading && s.progress != null) {
                Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(s.progress!!.phase, fontSize = 13.sp, color = Color(0xFF5F6368))
                        Spacer(Modifier.height(8.dp))
                        if (s.progress!!.total > 0) {
                            val fraction = s.progress!!.processed.toFloat() / s.progress!!.total.toFloat()
                            LinearProgressIndicator(
                                progress = { fraction.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = Color(0xFF1A73E8),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("${s.progress!!.processed} / ${s.progress!!.total}", fontSize = 12.sp, color = Color(0xFF5F6368))
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = Color(0xFF1A73E8),
                            )
                        }
                    }
                }
            }

            // Error message
            s.errorMsg?.let { err ->
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFDECEA)),
                ) {
                    Text(err, modifier = Modifier.padding(12.dp), color = Color(0xFFD93025), fontSize = 13.sp)
                }
            }

            if (s.totalStudents == 0 && !s.isLoading) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Tidak ada siswa di kelas ini", color = Color(0xFF5F6368))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(140.dp)) { Text(label, fontSize = 13.sp, color = Color(0xFF5F6368)) }
        Text(value, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}
