package com.absenku.ui.report

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Leaderboard
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
    LaunchedEffect(Unit) { viewModel.loadByDate(today); viewModel.loadRanking() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        uri?.let {
            val ok = com.absenku.utils.CsvExporter.exportAbsensi(ctx, it, s.todayAbsensi)
            Toast.makeText(ctx, if (ok) "Export OK" else "Gagal export", Toast.LENGTH_LONG).show()
        }
    }

    var showPicker by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(today) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Laporan") }) },
        floatingActionButton = {
            if (s.todayAbsensi.isNotEmpty()) {
                FloatingActionButton(onClick = { exportLauncher.launch("absen_${System.currentTimeMillis()}.csv") }, containerColor = Color(0xFF1A73E8)) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Export", tint = Color.White)
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Tab row: Absensi + Ranking
            var selectedTab by remember { mutableIntStateOf(0) }
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("Absensi", modifier = Modifier.padding(12.dp))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1; viewModel.loadRanking() }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Leaderboard, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Ranking", modifier = Modifier.padding(12.dp))
                    }
                }
            }

            when (selectedTab) {
                0 -> AbsensiTab(s, picked, showPicker, viewModel, df) { millis ->
                    showPicker = false
                    picked = df.format(Calendar.getInstance().apply { timeInMillis = millis }.time)
                }
                1 -> RankingTab(s)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AbsensiTab(
    s: ReportUiState,
    picked: String,
    showPicker: Boolean,
    viewModel: ReportViewModel,
    df: SimpleDateFormat,
    onDatePicked: (Long) -> Unit,
) {
    val datePickerState = rememberDatePickerState()
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = picked, onValueChange = {}, readOnly = true,
            label = { Text("Tanggal") },
            trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, Modifier.clickable { }) },
            modifier = Modifier.fillMaxWidth(),
        )

        if (showPicker) {
            DatePickerDialog(
                onDismissRequest = { },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onDatePicked(millis)
                            viewModel.loadByDate(df.format(java.util.Date(millis)))
                        }
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { }) { Text("Batal") } }
            ) { DatePicker(state = datePickerState) }
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

@Composable
private fun RankingTab(s: ReportUiState) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Ranking Siswa Berdasarkan Poin Disiplin", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))

        if (s.rankingPoin.isEmpty()) {
            Text("Belum ada data poin disiplin.", color = Color(0xFF5F6368))
        } else {
            LazyColumn {
                items(s.rankingPoinIndexed, key = { it.first }) { (rank, item) ->
                    val namaSiswa = s.allSiswaMap[item.siswaId]?.nama ?: "Siswa #${item.siswaId}"
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (rank) {
                                1 -> Color(0xFFFFF3E0) // gold-ish
                                2 -> Color(0xFFF3E5F5) // silver-ish
                                3 -> Color(0xFFE8F5E9) // bronze-ish
                                else -> MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("#$rank", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = when(rank) {
                                1 -> Color(0xFFFF9800)
                                2 -> Color(0xFF9C27B0)
                                3 -> Color(0xFF4CAF50)
                                else -> Color(0xFF5F6368)
                            }, modifier = Modifier.width(40.dp))
                            Column(Modifier.weight(1f)) {
                                Text(namaSiswa, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val net = item.netPoin
                                Text("${if (net >= 0) "+" else ""}$net", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (net >= 0) Color(0xFF34A853) else Color(0xFFD93025))
                                Text("poin", fontSize = 10.sp, color = Color(0xFF5F6368))
                            }
                        }
                    }
                }
            }
        }
    }
}
