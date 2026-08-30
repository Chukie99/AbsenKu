package com.absenku.ui.poin_disiplin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
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
import com.absenku.data.model.PoinDisiplin
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoinDisiplinScreen(viewModel: PoinDisiplinViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Poin Disiplin") },
                actions = {
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter")
                        }
                        DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }) {
                            DropdownMenuItem(text = { Text("Semua") }, onClick = {
                                viewModel.filterBySiswa(null); viewModel.filterByKategori(null); showFilterMenu = false
                            })
                            DropdownMenuItem(text = { Text("Positif saja") }, onClick = {
                                viewModel.filterByKategori("Positif"); showFilterMenu = false
                            })
                            DropdownMenuItem(text = { Text("Negatif saja") }, onClick = {
                                viewModel.filterByKategori("Negatif"); showFilterMenu = false
                            })
                            Divider()
                            s.allSiswa.forEach { sw ->
                                DropdownMenuItem(text = { Text(sw.nama) }, onClick = {
                                    viewModel.filterBySiswa(sw.id); showFilterMenu = false
                                })
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }, containerColor = Color(0xFF1A73E8)) {
                Icon(Icons.Default.Add, contentDescription = "Tambah", tint = Color.White)
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Summary row
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                val positif = s.filteredPoin.filter { it.kategori == "Positif" }.sumOf { it.poin }
                val negatif = s.filteredPoin.filter { it.kategori == "Negatif" }.sumOf { it.poin }
                SummaryChip("Positif", positif, Color(0xFF34A853))
                SummaryChip("Negatif", negatif, Color(0xFFD93025))
                SummaryChip("Net", positif - negatif, Color(0xFF1A73E8))
            }

            if (s.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF1A73E8))
                }
            } else if (s.filteredPoin.isEmpty()) {
                Text("Belum ada poin disiplin.", color = Color(0xFF5F6368), modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn {
                    items(s.filteredPoin, key = { it.id }) { poin ->
                        PoinDisiplinRow(poin, s.allSiswa, onDelete = { viewModel.deletePoinDisiplin(poin.id) })
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddPoinDisiplinDialog(
            siswaList = s.allSiswa,
            onDismiss = { showAddDialog = false },
            onConfirm = { poin ->
                viewModel.addPoinDisiplin(poin) { showAddDialog = false }
            }
        )
    }
}

@Composable
private fun SummaryChip(label: String, value: Int, color: Color) {
    Card(shape = RoundedCornerShape(8.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 11.sp, color = Color(0xFF5F6368))
        }
    }
}

@Composable
private fun PoinDisiplinRow(poin: PoinDisiplin, siswaList: List<com.absenku.data.model.Siswa>, onDelete: () -> Unit) {
    val namaSiswa = siswaList.find { it.id == poin.siswaId }?.nama ?: "Siswa #${poin.siswaId}"
    val color = if (poin.kategori == "Positif") Color(0xFF34A853) else Color(0xFFD93025)
    Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(namaSiswa, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${poin.kategori} · ${poin.poin} poin · ${poin.tanggal}", fontSize = 12.sp, color = color)
                poin.keterangan?.let { Text(it, fontSize = 11.sp, color = Color(0xFF5F6368)) }
                poin.diberikanOleh?.let { Text("Oleh: $it", fontSize = 10.sp, color = Color(0xFF9AA0A6)) }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color(0xFFD93025))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPoinDisiplinDialog(
    siswaList: List<com.absenku.data.model.Siswa>,
    onDismiss: () -> Unit,
    onConfirm: (PoinDisiplin) -> Unit,
) {
    val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    var selectedSiswaId by remember { mutableStateOf(siswaList.firstOrNull()?.id ?: 0L) }
    var openSiswa by remember { mutableStateOf(false) }
    var kategori by remember { mutableStateOf("Negatif") }
    var poinStr by remember { mutableStateOf("10") }
    var keterangan by remember { mutableStateOf("") }
    var diberikanOleh by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Poin Disiplin") },
        text = {
            Column {
                // Siswa dropdown
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = siswaList.find { it.id == selectedSiswaId }?.nama ?: "Pilih Siswa",
                        onValueChange = {}, readOnly = true,
                        label = { Text("Siswa") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.fillMaxSize().clickable { openSiswa = true })
                    DropdownMenu(expanded = openSiswa, onDismissRequest = { openSiswa = false }) {
                        siswaList.forEach { sw ->
                            DropdownMenuItem(text = { Text(sw.nama) }, onClick = {
                                selectedSiswaId = sw.id; openSiswa = false
                            })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Kategori toggle
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kategori == "Negatif", onClick = { kategori = "Negatif" }, label = { Text("Negatif") })
                    FilterChip(selected = kategori == "Positif", onClick = { kategori = "Positif" }, label = { Text("Positif") })
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(value = poinStr, onValueChange = { if (it.all { c -> c.isDigit() }) poinStr = it }, label = { Text("Poin") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = keterangan, onValueChange = { keterangan = it }, label = { Text("Keterangan") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = diberikanOleh, onValueChange = { diberikanOleh = it }, label = { Text("Diberikan Oleh") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(PoinDisiplin(
                    siswaId = selectedSiswaId,
                    tanggal = df.format(Date()),
                    kategori = kategori,
                    poin = poinStr.toIntOrNull() ?: 0,
                    keterangan = keterangan.ifBlank { null },
                    diberikanOleh = diberikanOleh.ifBlank { null },
                ))
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
