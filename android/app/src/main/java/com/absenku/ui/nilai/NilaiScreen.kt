package com.absenku.ui.nilai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.absenku.data.database.dao.MapelAvgNilai
import com.absenku.data.model.Siswa

private val CHART_COLORS = listOf(
    Color(0xFF1A73E8), Color(0xFF34A853), Color(0xFFFBBC04),
    Color(0xFFEA4335), Color(0xFF9C27B0), Color(0xFF00BCD4),
    Color(0xFFFF9800), Color(0xFF795548), Color(0xFF607D8B),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NilaiScreen(viewModel: NilaiViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Nilai Siswa") }) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("Input Nilai", modifier = Modifier.padding(12.dp))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1; viewModel.loadChartData() }) {
                    Text("Grafik", modifier = Modifier.padding(12.dp))
                }
            }

            when (selectedTab) {
                0 -> InputNilaiTab(s, viewModel)
                1 -> ChartTab(s)
            }
        }
    }
}

@Composable
private fun InputNilaiTab(s: NilaiUiState, viewModel: NilaiViewModel) {
    var selectedSiswaId by remember { mutableStateOf(0L) }
    var selectedMapelId by remember { mutableStateOf(0L) }
    var nilaiStr by remember { mutableStateOf("") }
    var semester by remember { mutableStateOf("1") }
    var tahunAjaran by remember { mutableStateOf("") }
    var showResult by remember { mutableStateOf(false) }

    LazyColumn(Modifier.padding(12.dp)) {
        item {
            Text("Input / Edit Nilai", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))

            // Siswa dropdown
            DropdownField("Siswa", s.allSiswa.map { it.id to it.nama }, selectedSiswaId) { selectedSiswaId = it }
            Spacer(Modifier.height(8.dp))

            // Mapel dropdown
            DropdownField("Mapel", s.allMapel.map { it.id to it.nama }, selectedMapelId) { selectedMapelId = it }
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(value = nilaiStr, onValueChange = { nilaiStr = it }, label = { Text("Nilai (0-100)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = semester, onValueChange = { semester = it }, label = { Text("Semester") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = tahunAjaran, onValueChange = { tahunAjaran = it }, label = { Text("Tahun Ajaran") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    if (selectedSiswaId > 0 && selectedMapelId > 0 && nilaiStr.isNotBlank()) {
                        viewModel.saveNilai(selectedSiswaId, selectedMapelId, nilaiStr, semester, tahunAjaran)
                        showResult = true
                        nilaiStr = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
            ) {
                Text("Simpan Nilai", fontWeight = FontWeight.Bold)
            }

            if (showResult) {
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F4EA))) {
                    Text("Nilai berhasil disimpan!", modifier = Modifier.padding(12.dp), color = Color(0xFF34A853))
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
        }

        // Existing nilai list
        if (s.nilai.isNotEmpty()) {
            item { Text("Daftar Nilai", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp)) }
            items(s.nilai, key = { it.id }) { n ->
                val namaMapel = s.allMapel.find { it.id == n.mapelId }?.nama ?: "Mapel#${n.mapelId}"
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp), shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(namaMapel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Semester ${n.semester} · ${n.tahunAjaran}", fontSize = 11.sp, color = Color(0xFF5F6368))
                        }
                        Text(n.nilai, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1A73E8))
                    }
                }
            }
        }
    }
}

@Composable
private fun DropdownField(label: String, items: List<Pair<Long, String>>, selectedId: Long, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = items.find { it.first == selectedId }?.second ?: "Pilih $label",
            onValueChange = {}, readOnly = true, label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.fillMaxSize().clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}

@Composable
private fun ChartTab(s: NilaiUiState) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Grafik Rata-rata Nilai Per Mata Pelajaran", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))

        if (s.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF1A73E8))
            }
        } else if (s.chartData.isEmpty()) {
            Text("Belum ada data nilai untuk grafik.", color = Color(0xFF5F6368))
        } else {
            // Bar chart
            NilaiBarChart(
                entries = s.chartData,
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
            Spacer(Modifier.height(16.dp))

            // Detail cards
            Text("Rincian per Mapel", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn {
                items(s.chartData.size) { idx ->
                    val entry = s.chartData[idx]
                    val color = CHART_COLORS[idx % CHART_COLORS.size]
                    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp), shape = RoundedCornerShape(8.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Canvas(Modifier.size(12.dp)) { drawRect(color) }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("${entry.count} nilai", fontSize = 11.sp, color = Color(0xFF5F6368))
                            }
                            Text(String.format("%.1f", entry.value), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
                        }
                    }
                }
            }
        }
    }
}

/** Simple bar chart using Compose Canvas for nilai data. */
@Composable
private fun NilaiBarChart(entries: List<NilaiChartEntry>, modifier: Modifier = Modifier) {
    val maxVal = (entries.maxOfOrNull { it.value } ?: 100f).coerceAtLeast(1f)
    val axisColor = Color(0xFFD1D5DB)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(Modifier.padding(12.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val barW = size.width / entries.size * 0.6f
                val slotW = size.width / entries.size
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#5F6368")
                    textSize = 24f
                    textAlign = android.graphics.Paint.Align.CENTER
                }

                // Axis
                drawLine(axisColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)

                entries.forEachIndexed { idx, e ->
                    val barH = (e.value / maxVal) * size.height * 0.75f
                    val x = slotW * idx + (slotW - barW) / 2
                    val yTop = size.height - barH - 40f

                    // Bar
                    drawRect(
                        color = CHART_COLORS[idx % CHART_COLORS.size],
                        topLeft = Offset(x, yTop),
                        size = Size(barW, barH),
                    )

                    // Value text on top
                    drawContext.canvas.nativeCanvas.drawText(
                        String.format("%.0f", e.value),
                        x + barW / 2,
                        yTop - 8f,
                        textPaint
                    )

                    // Label text at bottom (truncated)
                    val shortLabel = if (e.label.length > 8) e.label.take(8) + ".." else e.label
                    drawContext.canvas.nativeCanvas.drawText(
                        shortLabel,
                        x + barW / 2,
                        size.height - 8f,
                        textPaint
                    )
                }
            }
        }
    }
}

// Spacer overlay handles click for dropdowns — no extra Modifier extension needed
