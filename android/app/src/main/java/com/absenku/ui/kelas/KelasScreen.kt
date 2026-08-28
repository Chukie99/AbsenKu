package com.absenku.ui.kelas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.absenku.data.model.Kelas

/**
 * KelasScreen — list + soft-delete + add via FAB (simple inline input).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KelasScreen(viewModel: KelasViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Data Kelas") }) },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }, containerColor = Color(0xFF1A73E8)) { Icon(Icons.Default.Add, contentDescription = null, tint = Color.White) } }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (s.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF1A73E8)) }
            } else {
                LazyColumn { items(s.allClasses.filter { it.isActive }, key = { it.id }) { k -> KelasRow(k) { viewModel.softDelete(it.id) } } }
            }
        }
    }

    if (showAdd) AddKelasDialog(
        onDismiss = { showAdd = false },
        onConfirm = { nama, wali, ta -> viewModel.addKelas(nama, wali, ta); showAdd = false }
    )
}

@Composable
private fun KelasRow(kelas: Kelas, onLongPress: (Kelas) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(8.dp).clickable { }, shape = RoundedCornerShape(8.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(kelas.nama, fontWeight = FontWeight.Bold)
                Text("Wali: ${kelas.waliKelas ?: "-"}", fontSize = 12.sp, color = Color(0xFF5F6368))
                Text("T.A.: ${kelas.tahunAjaran ?: "-"}", fontSize = 11.sp, color = Color(0xFF5F6368))
            }
            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color(0xFFD93025), modifier = Modifier.clickable { onLongPress(kelas) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddKelasDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var nama by remember { mutableStateOf("") }
    var wali by remember { mutableStateOf("") }
    var ta by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Tambah Kelas") },
        text = {
            Column {
                OutlinedTextField(value = nama, onValueChange = { nama = it }, label = { Text("Nama Kelas") })
                OutlinedTextField(value = wali, onValueChange = { wali = it }, label = { Text("Wali Kelas") })
                OutlinedTextField(value = ta, onValueChange = { ta = it }, label = { Text("Tahun Ajaran") }, placeholder = { Text("2025/2026") })
            }
        },
        confirmButton = {
            TextButton(onClick = { if (nama.isNotBlank() && wali.isNotBlank()) onConfirm(nama, wali, ta); onDismiss() }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}
