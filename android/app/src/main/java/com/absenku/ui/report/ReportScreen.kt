package com.absenku.ui.report

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FileDownload
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.absenku.data.model.SyncLog
import com.absenku.utils.DateFormatter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(viewModel: ReportViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val today = df.format(Date())
    LaunchedEffect(Unit) { viewModel.loadByDate(today) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        uri?.let {
            val ok = com.absenku.utils.CsvExporter.exportAbsensi(ctx, it, s.todayAbsensi)
            Toast.makeText(ctx, if (ok) "Export OK" else "Gagal export", Toast.LENGTH_LONG).show()
        }
    }

    var showPicker by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(today) }
    var datePicker by remember { mutableStateOf<androidx.compose.material3.DatePickerDialog?>(null) }

    Scaffold(
        topBar = { SmallTopAppBar(title = { Text("Laporan") }) },
        floatingActionButton = {
            if (s.todayAbsensi.isNotEmpty()) {
                FloatingActionButton(onClick = { exportLauncher.launch("absen_${System.currentTimeMillis()}.csv") }, containerColor = Color(0xFF1A73E8)) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Export", tint = Color.White)
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(12.dp)) {
            // Date filter
            OutlinedTextField(
                value = picked,
                onValueChange = {}, readOnly = true,
                label = { Text("Tanggal") },
                trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, Modifier.clickable { showPicker = true }) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (showPicker) {
                val cal = Calendar.getInstance().apply { time = df.parse(picked) ?: Date() }
                DatePickerDialog(
                    ctx,
                    { _, y, m, d ->
                        cal.set(y, m, d)
                        picked = df.format(cal.time)
                        viewModel.loadByDate(picked)
                        showPicker = false
                    },
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }

            Spacer(Modifier.height(12.dp))
            Text(DateFormatter.formatDate(System.currentTimeMillis()), fontWeight = FontWeight.Bold, fontSize = 15.sp)

            if (s.isLoading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF1A73E8))
                }
            } else if (s.todayAbsensi.isEmpty()) {
                Text("Belum ada absen untuk tanggal ${picked}.", color = Color(0xFF5F6368))
            } else {
                LazyColumn {
                    items(s.todayAbsensi, key = { it.id }) { a ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(8.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("${a.waktuMasuk ?: "-"} · ${a.status}", fontWeight = FontWeight.Bold)
                                Text("Siswa ID: ${a.siswaId} | Mapel: ${a.mapelId}", fontSize = 12.sp, color = Color(0xFF5F6368))
                            }
                        }
                    }
                }
            }

            Text("Riwayat Sync", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(s.syncLogs, key = { it.id }) { log: SyncLog ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), shape = RoundedCornerShape(6.dp), elevation = CardDefaults.cardElevation(1.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(log.type ?: "?", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Text(log.message ?: "", fontSize = 11.sp, color = Color(0xFF5F6368))
                        }
                    }
                }
            }
        }
    }
}
