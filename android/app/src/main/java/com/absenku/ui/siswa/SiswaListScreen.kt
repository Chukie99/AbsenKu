package com.absenku.ui.siswa

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.absenku.data.model.Siswa

/** SiswaListScreen — list, search, filter by kelas, soft-delete (long press). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiswaListScreen(
    onAdd: () -> Unit = {},
    onEdit: (Siswa) -> Unit = {},
    viewModel: SiswaViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Data Siswa") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = Color(0xFF1A73E8)) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Search + class filter
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = s.search,
                    onValueChange = { viewModel.search(it) },
                    placeholder = { Text("Cari NIS / nama...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.weight(1f), singleLine = true,
                )
                TextButton(onClick = { expanded = true }) {
                    Text(if (s.selectedKelas != null) "Kelas #${s.selectedKelas}" else "Semua Kelas")
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(onClick = { viewModel.filterByKelas(null); expanded = false }) { Text("Semua Kelas") }
                    s.allClasses.forEach { k ->
                        DropdownMenuItem(onClick = { viewModel.filterByKelas(k.id); expanded = false }) { Text(k.nama) }
                    }
                }
            }

            if (s.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF1A73E8))
                }
            } else if (s.filtered.isEmpty()) {
                Text("Belum ada siswa.", color = Color(0xFF5F6368), modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn {
                    items(s.filtered, key = { it.id }) { siswa ->
                        SiswaRow(siswa, onEdit, onLongPress = { viewModel.softDelete(siswa.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SiswaRow(
    siswa: Siswa,
    onEdit: (Siswa) -> Unit,
    onLongPress: (Siswa) -> Unit,
) {
    val painter = rememberAsyncImagePainter(
        model = siswa.foto,
        placeholder = androidx.compose.ui.res.painterResource(0),
        error = androidx.compose.ui.res.painterResource(0),
    )
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onEdit(siswa) },
                    onLongPress = { onLongPress(siswa) },
                )
            },
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Foto — fallback to a colored circle if null
                Box(Modifier.size(48.dp)) {
                    if (!siswa.foto.isNullOrEmpty()) {
                        Image(painter = painter, contentDescription = "Foto ${siswa.nama}", modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                    } else {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(color = Color(0xFFE8F0FE), style = androidx.compose.ui.graphics.drawstyle.Fill)
                            val p = Path().apply {
                                moveTo(size.minDimension / 2 - 10, size.minDimension / 2 + 6)
                                lineTo(size.minDimension / 2 + 10, size.minDimension / 2 + 6)
                            }
                            drawContext.canvas.nativeCanvas.apply {
                                val txt = if (siswa.nama.isNotEmpty()) siswa.nama[0].toString() else "?"
                                drawText(txt, (size.minDimension / 2 - 6).toFloat(), size.minDimension / 2 + 20,
                                    android.text.TextPaint().apply { this.textAlign = android.text.Layout.Alignment.ALIGN_CENTER; this.textSize = 18f; this.color = android.graphics.Color.parseColor("#1A73E8") })
                            }
                        }
                    }
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(siswa.nama, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("NIS: ${siswa.nis} | Kelas: ${siswa.kelasId}", fontSize = 12.sp, color = Color(0xFF5F6368))
                }
            }
            IconButton(onClick = { onEdit(siswa) }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
        }
    }
}
