package com.absenku.ui.jadwal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.absenku.data.model.JadwalPelajaran
import com.absenku.data.model.Kelas
import com.absenku.data.model.Mapel

private val HARI_LIST = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu")
private val HARI_COLORS = mapOf(
    "Senin" to Color(0xFF1A73E8),
    "Selasa" to Color(0xFF34A853),
    "Rabu" to Color(0xFFFBBC04),
    "Kamis" to Color(0xFFEA4335),
    "Jumat" to Color(0xFF9C27B0),
    "Sabtu" to Color(0xFF00BCD4),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JadwalPelajaranScreen(viewModel: JadwalPelajaranViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Jadwal Pelajaran") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }, containerColor = Color(0xFF1A73E8)) {
                Icon(Icons.Default.Add, contentDescription = "Tambah", tint = Color.White)
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Kelas filter chips
            ScrollableTabRow(selectedTabIndex = 0, edgePadding = 8.dp, modifier = Modifier.padding(top = 8.dp)) {
                Tab(selected = s.selectedKelasId == null, onClick = { viewModel.filterByKelas(null) }) {
                    Text("Semua", modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                }
                s.allKelas.forEach { k ->
                    Tab(selected = s.selectedKelasId == k.id, onClick = { viewModel.filterByKelas(k.id) }) {
                        Text(k.nama, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                    }
                }
            }

            if (s.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF1A73E8))
                }
            } else if (s.allJadwal.isEmpty()) {
                Text("Belum ada jadwal.", color = Color(0xFF5F6368), modifier = Modifier.padding(16.dp))
            } else {
                // Weekly grid
                WeeklyGrid(
                    jadwal = s.allJadwal,
                    allKelas = s.allKelas,
                    allMapel = s.allMapel,
                    onDelete = { viewModel.deleteJadwal(it) }
                )
            }
        }
    }

    if (showAddDialog) {
        AddJadwalDialog(
            kelasList = s.allKelas,
            mapelList = s.allMapel,
            onDismiss = { showAddDialog = false },
            onConfirm = { jadwal -> viewModel.addJadwal(jadwal) { showAddDialog = false } }
        )
    }
}

@Composable
private fun WeeklyGrid(
    jadwal: List<JadwalPelajaran>,
    allKelas: List<Kelas>,
    allMapel: List<Mapel>,
    onDelete: (Long) -> Unit,
) {
    val grouped = jadwal.groupBy { it.hari }

    Column(Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState())) {
        HARI_LIST.forEach { hari ->
            val items = grouped[hari] ?: emptyList()
            if (items.isNotEmpty()) {
                // Day header
                Box(
                    Modifier.fillMaxWidth().background(HARI_COLORS[hari] ?: Color.Gray, RoundedCornerShape(8.dp)).padding(8.dp)
                ) {
                    Text(hari, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(Modifier.height(4.dp))

                items.sortedBy { it.jamMulai }.forEach { jadwal ->
                    val namaMapel = allMapel.find { it.id == jadwal.mapelId }?.nama ?: "Mapel #${jadwal.mapelId}"
                    val namaKelas = allKelas.find { it.id == jadwal.kelasId }?.nama ?: "Kelas #${jadwal.kelasId}"
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(namaMapel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("${jadwal.jamMulai} - ${jadwal.jamSelesai}", fontSize = 11.sp, color = Color(0xFF5F6368))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Kelas: $namaKelas", fontSize = 10.sp, color = Color(0xFF9AA0A6))
                                    jadwal.guru?.let { Text("Guru: $it", fontSize = 10.sp, color = Color(0xFF9AA0A6)) }
                                }
                            }
                            IconButton(onClick = { onDelete(jadwal.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color(0xFFD93025), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // Empty state for days without jadwal
        if (jadwal.isEmpty()) {
            HARI_LIST.forEach { hari ->
                Box(
                    Modifier.fillMaxWidth().background(HARI_COLORS[hari]?.copy(alpha = 0.1f) ?: Color.LightGray, RoundedCornerShape(8.dp)).padding(8.dp)
                ) {
                    Column {
                        Text(hari, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = HARI_COLORS[hari] ?: Color.Gray)
                        Text("Kosong", fontSize = 12.sp, color = Color(0xFF9AA0A6))
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddJadwalDialog(
    kelasList: List<Kelas>,
    mapelList: List<Mapel>,
    onDismiss: () -> Unit,
    onConfirm: (JadwalPelajaran) -> Unit,
) {
    var selectedKelasId by remember { mutableStateOf(kelasList.firstOrNull()?.id ?: 0L) }
    var selectedMapelId by remember { mutableStateOf(mapelList.firstOrNull()?.id ?: 0L) }
    var selectedHari by remember { mutableStateOf("Senin") }
    var openKelas by remember { mutableStateOf(false) }
    var openMapel by remember { mutableStateOf(false) }
    var openHari by remember { mutableStateOf(false) }
    var jamMulai by remember { mutableStateOf("07:00") }
    var jamSelesai by remember { mutableStateOf("08:30") }
    var guru by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Jadwal") },
        text = {
            Column {
                // Kelas
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = kelasList.find { it.id == selectedKelasId }?.nama ?: "Pilih Kelas",
                        onValueChange = {}, readOnly = true, label = { Text("Kelas") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.fillMaxSize().clickable { openKelas = true })
                    DropdownMenu(expanded = openKelas, onDismissRequest = { openKelas = false }) {
                        kelasList.forEach { k -> DropdownMenuItem(text = { Text(k.nama) }, onClick = { selectedKelasId = k.id; openKelas = false }) }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Mapel
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = mapelList.find { it.id == selectedMapelId }?.nama ?: "Pilih Mapel",
                        onValueChange = {}, readOnly = true, label = { Text("Mata Pelajaran") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.fillMaxSize().clickable { openMapel = true })
                    DropdownMenu(expanded = openMapel, onDismissRequest = { openMapel = false }) {
                        mapelList.forEach { m -> DropdownMenuItem(text = { Text(m.nama) }, onClick = { selectedMapelId = m.id; openMapel = false }) }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Hari
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = selectedHari, onValueChange = {}, readOnly = true, label = { Text("Hari") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.fillMaxSize().clickable { openHari = true })
                    DropdownMenu(expanded = openHari, onDismissRequest = { openHari = false }) {
                        HARI_LIST.forEach { h -> DropdownMenuItem(text = { Text(h) }, onClick = { selectedHari = h; openHari = false }) }
                    }
                }
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = jamMulai, onValueChange = { jamMulai = it }, label = { Text("Jam Mulai") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = jamSelesai, onValueChange = { jamSelesai = it }, label = { Text("Jam Selesai") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = guru, onValueChange = { guru = it }, label = { Text("Guru") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(JadwalPelajaran(
                    kelasId = selectedKelasId, mapelId = selectedMapelId,
                    hari = selectedHari, jamMulai = jamMulai, jamSelesai = jamSelesai,
                    guru = guru.ifBlank { null },
                ))
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
