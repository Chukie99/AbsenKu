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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.absenku.data.model.Kelas
import com.absenku.data.model.Siswa
import com.absenku.utils.QrCodeGenerator

/**
 * SiswaFormScreen — add new or edit existing student.
 * If siswaId == 0 → insert mode; else edit mode (loaded from DB).
 * Includes QR code preview for existing students.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiswaFormScreen(
    siswaId: Long = 0L,
    siswa: Siswa? = null,
    kelasList: List<Kelas> = emptyList(),
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    viewModel: SiswaViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    var loadedSiswa by remember { mutableStateOf(siswa) }
    var isLoaded by remember { mutableStateOf(false) }

    // Load siswa by ID if not provided directly
    LaunchedEffect(siswaId) {
        if (siswaId > 0 && siswa == null && !isLoaded) {
            viewModel.loadSiswaById(siswaId) { loadedSiswa = it; isLoaded = true }
        } else {
            isLoaded = true
        }
    }

    val currentSiswa = loadedSiswa

    var nama by remember(currentSiswa) { mutableStateOf(currentSiswa?.nama ?: "") }
    var nis by remember(currentSiswa) { mutableStateOf(currentSiswa?.nis ?: "") }
    var alamat by remember(currentSiswa) { mutableStateOf(currentSiswa?.alamat ?: "") }
    var noHp by remember(currentSiswa) { mutableStateOf(currentSiswa?.noHpOrtu ?: "") }
    var tglLahir by remember(currentSiswa) { mutableStateOf(currentSiswa?.tanggalLahir ?: "") }
    var selectedKelas by remember(currentSiswa) { mutableStateOf(currentSiswa?.kelasId ?: 0L) }
    var openKelas by remember { mutableStateOf(false) }
    var fotoPath by remember(currentSiswa) { mutableStateOf(currentSiswa?.foto ?: "") }
    var showQr by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bmp = com.absenku.utils.ImageCompressor.uriToBitmap(ctx, uri)
            val tahun = "2025/2026"
            val path = com.absenku.utils.ImageCompressor.saveStudentPhoto(
                ctx, bmp!!, tahun, selectedKelas, nis.ifEmpty { "temp_${System.currentTimeMillis()}" }
            )
            fotoPath = path
        }
    }

    // QR code bitmap for existing siswa
    val qrBitmap = remember(currentSiswa) {
        if (currentSiswa != null && currentSiswa.nis.isNotBlank()) {
            QrCodeGenerator.generate(QrCodeGenerator.studentQrData(currentSiswa.nis, currentSiswa.nama, currentSiswa.kelasId))
        } else null
    }

    if (!isLoaded && siswaId > 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF1A73E8))
        }
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(if (currentSiswa == null) "Tambah Siswa" else "Edit Siswa") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (nis.isBlank() || nama.isBlank()) {
                    onSaved()
                    return@FloatingActionButton
                }
                val siswaData = Siswa(
                    id = currentSiswa?.id ?: 0,
                    nis = nis,
                    nama = nama,
                    kelasId = selectedKelas,
                    foto = fotoPath.ifBlank { null },
                    alamat = alamat.ifBlank { null },
                    noHpOrtu = noHp.ifBlank { null },
                    tanggalLahir = tglLahir.ifBlank { null },
                )
                if (currentSiswa == null) {
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
                Spacer(Modifier.fillMaxSize().clickable { openKelas = true })
            }
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(value = tglLahir, onValueChange = { tglLahir = it }, label = { Text("Tanggal Lahir (yyyy-mm-dd)") }, placeholder = { Text("2008-05-16") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = alamat, onValueChange = { alamat = it }, label = { Text("Alamat") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = noHp, onValueChange = { noHp = it }, label = { Text("No HP Orang Tua") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            // QR Code section
            if (currentSiswa != null && qrBitmap != null) {
                Spacer(Modifier.height(16.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("QR Code Siswa", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        if (showQr) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "QR Code ${currentSiswa.nama}",
                                modifier = Modifier.size(200.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(currentSiswa.nis, fontSize = 12.sp, color = Color(0xFF5F6368))
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { showQr = !showQr }) {
                            Text(if (showQr) "Sembunyikan QR" else "Tampilkan QR Code")
                        }
                    }
                }
            }
        }
    }
}
