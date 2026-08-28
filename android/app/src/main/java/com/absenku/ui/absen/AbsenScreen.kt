package com.absenku.ui.absen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.absenku.data.model.Absensi
import com.absenku.utils.DateFormatter

/**
 * AbsenScreen — barcode scan attendance.
 * Uses BarcodeScanner Composable (gallery/camera picker fallback).
 * After a scan → dialog to pick status (Hadir/Izin/Sakit/Alfa).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbsenScreen(viewModel: AbsenViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()

    // Scanner Composable — fires onBarcodeScanned on detection
    val scanTrigger = remember { mutableStateOf(0) }
    val scanner = com.absenku.utils.rememberBarcodeScanner { raw ->
        viewModel.onBarcodeScanned(raw)
    }

    Scaffold(
        topBar = { SmallTopAppBar(title = { Text("Absen Siswa") }) },
        floatingActionButton = {
            // Manual entry
            FloatingActionButton(onClick = { scanTrigger.value++ }, containerColor = Color(0xFF1A73E8)) {
                Icon(Icons.Default.Search, contentDescription = "Manual", tint = Color.White)
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            // Today's attendance list
            Text("Absen Hari Ini", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            if (s.todayAbsensi.isEmpty()) {
                Text("Belum ada absen hari ini", color = Color(0xFF5F6368), fontSize = 13.sp)
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(s.todayAbsensi, key = { it.id }) { a ->
                        AbsenItem(absensi = a)
                    }
                }
            }
        }
    }

    // Status picker dialog after scan
    if (s.showStatusDialog && s.selectedSiswa != null) {
        StatusDialog(
            siswaName = s.selectedSiswa!!.nama,
            kelas = s.selectedSiswa!!.kelasId.toString(),
            errorMsg = s.errorMsg,
            onDismiss = { viewModel.dismissDialog() },
            onStatus = { viewModel.setStatus(it) }
        )
    }
}

@Composable
private fun AbsenItem(absensi: Absensi) {
    val bg = when (absensi.status) {
        "Hadir" -> Color(0xFFE6F4EA)
        "Izin" -> Color(0xFFFFECB3)
        "Sakit" -> Color(0xFFFFE0B2)
        else -> Color(0xFFFCE8E6)
    }
    Card(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(absensi.waktuMasuk ?: "-", fontWeight = FontWeight.Bold)
            }
            Text(absensi.status, color = Color(0xFF1A1A1A), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatusDialog(siswaName: String, kelas: String, errorMsg: String?, onDismiss: () -> Unit, onStatus: (String) -> Unit) {
    val statuses = listOf("Hadir", "Izin", "Sakit", "Alfa")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Absen: $siswaName") },
        text = {
            Column {
                Text("Kelas: $kelas", fontSize = 13.sp, color = Color(0xFF5F6368))
                errorMsg?.let { Text(it, color = Color(0xFFD93025), fontSize = 12.sp) }
                Spacer(Modifier.height(8.dp))
                statuses.forEach { st ->
                    TextButton(onClick = { onStatus(st); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text(st)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
