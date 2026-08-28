package com.absenku.ui.siswa

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.absenku.data.model.Kelas
import com.absenku.data.model.Siswa

/**
 * SiswaFormScreen — add new or edit existing student.
 * If siswa == null → insert mode; else edit mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiswaFormScreen(
    siswa: Siswa? = null,
    kelasList: List<Kelas> = emptyList(),
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    viewModel: SiswaViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    var nama by remember { mutableStateOf(siswa?.nama ?: "") }
    var nis by remember { mutableStateOf(siswa?.nis ?: "") }
    var alamat by remember { mutableStateOf(siswa?.alamat ?: "") }
    var noHp by remember { mutableStateOf(siswa?.noHpOrtu ?: "") }
    var tglLahir by remember { mutableStateOf(siswa?.tanggalLahir ?: "") }
    var selectedKelas by remember { mutableStateOf(siswa?.kelasId ?: 0L) }
    var openKelas by remember { mutableStateOf(false) }
    var fotoPath by remember { mutableStateOf(siswa?.foto ?: "") }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bmp = com.absenku.utils.ImageCompressor.uriToBitmap(ctx, uri)
            // save scaled
            val tahun = "2025/2026"
            val path = com.absenku.utils.ImageCompressor.saveStudentPhoto(
                ctx, bmp!!, tahun, selectedKelas, nis.ifEmpty { "temp_${System.currentTimeMillis()}" }
            )
            fotoPath = path
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(if (siswa == null) "Tambah Siswa" else "Edit Siswa") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (nis.isBlank() || nama.isBlank()) {
                    onSaved() // caller shows error
                    return@FloatingActionButton
                }
                val siswaData = Siswa(
                    id = siswa?.id ?: 0,
                    nis = nis,
                    nama = nama,
                    kelasId = selectedKelas,
                    foto = fotoPath.ifBlank { null },
                    alamat = alamat.ifBlank { null },
                    noHpOrtu = noHp.ifBlank { null },
                    tanggalLahir = tglLahir.ifBlank { null },
                )
                if (siswa == null) {
                    viewModel.addSiswa(siswaData) { onSaved() }
                } else {
                    viewModel.updateSiswa(siswaData) { onSaved() }
                }
            }, containerColor = Color(0xFF1A73E8)) {
                Text("Simpan", fontWeight = FontWeight.Bold)
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
        ) {
            // Foto picker
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (fotoPath.isNotBlank()) {
                    Image(
                        painter = rememberAsyncImagePainter(fotoPath),
                        contentDescription = "Foto",
                        modifier = Modifier.size(96.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(Modifier.size(96.dp).clip(CircleShape).background(Color(0xFFE8F0FE)), contentAlignment = Alignment.Center) {
                        Text(if (nama.isNotBlank()) nama[0].toString() else "?", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A73E8))
                    }
                }
                Box(Modifier.align(Alignment.BottomCenter).padding(top = 60.dp).clip(CircleShape).background(Color(0xFF1A73E8)).clickable { pickImage.launch("image/*") }) {
                    Icon(Icons.Default.Add, contentDescription = "Ambil Foto", tint = Color.White, modifier = Modifier.padding(4.dp).size(20.dp))
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(value = nis, onValueChange = { if (it.length <= 20) nis = it }, label = { Text("NIS (Unik)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = nama, onValueChange = { nama = it }, label = { Text("Nama Lengkap") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))

            // Kelas dropdown
            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = kelasList.find { it.id == selectedKelas }?.nama ?: "Pilih Kelas",
                    onValueChange = {}, readOnly = true,
                    label = { Text("Kelas") }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenu(expanded = openKelas, onDismissRequest = { openKelas = false }) {
                    kelasList.forEach { k ->
                        DropdownMenuItem(text = { Text(k.nama) }, onClick = { selectedKelas = k.id; openKelas = false })
                    }
                }
                // make whole row clickable for dropdown
                Spacer(Modifier.fillMaxSize().clickable { openKelas = true })
            }
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(value = tglLahir, onValueChange = { tglLahir = it }, label = { Text("Tanggal Lahir (yyyy-mm-dd)") }, placeholder = { Text("2008-05-16") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = alamat, onValueChange = { alamat = it }, label = { Text("Alamat") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = noHp, onValueChange = { noHp = it }, label = { Text("No HP Orang Tua") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}
