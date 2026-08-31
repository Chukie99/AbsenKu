package com.absenku.ui.student_detail

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.absenku.data.model.PoinDisiplin
import com.absenku.data.repository.Repository
import com.absenku.utils.PdfGenerator
import com.absenku.utils.QrCodeGenerator
import com.absenku.utils.StudentCardData
import java.io.File

/**
 * StudentDetailScreen — full detail view for a single student.
 *
 * Sections:
 *  1. Photo / initials avatar
 *  2. QR code preview
 *  3. Biodata (NIS, Nama, Kelas, Tgl Lahir, Alamat, No HP Ortu)
 *  4. Attendance summary (Hadir/Izin/Sakit/Alfa counts)
 *  5. Grade summary per subject
 *  6. Discipline points summary (positif / negatif)
 *  7. Print actions (Kartu Siswa PDF, Biodata PDF)
 *  8. Archive button (soft-delete)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onArchived: () -> Unit,
    viewModel: StudentDetailViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var showArchiveDialog by remember { mutableStateOf(false) }

    val cardPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let {
            val schoolName = runCatching {
                // Best-effort fetch from a local scope; the ViewModel handles it
                null
            }.getOrNull()
            viewModel.printStudentCard(ctx, it, null)
        }
    }

    val biodataPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { viewModel.printBiodata(ctx, it, null) }
    }

    // Print result snackbar
    LaunchedEffect(s.printResult) {
        s.printResult?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            viewModel.clearPrintResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.siswa?.nama ?: "Detail Siswa") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Kembali") }
                },
                actions = {
                    s.siswa?.let { siswa ->
                        IconButton(onClick = { onEdit(siswa.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (s.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF1A73E8))
            }
            return@Scaffold
        }

        if (s.errorMsg != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.errorMsg ?: "", color = Color(0xFFD93025), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.loadDetail() }) { Text("Coba Lagi") }
                }
            }
            return@Scaffold
        }

        val siswa = s.siswa ?: return@Scaffold
        val qrData = QrCodeGenerator.studentQrData(siswa.nis, siswa.nama, siswa.kelasId)
        val qrBitmap = remember(qrData) { QrCodeGenerator.generate(qrData, 300) }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 1. PHOTO + QR CODE ──
            item {
                Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Photo or initials
                        Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                            if (!siswa.foto.isNullOrEmpty() && File(siswa.foto!!).exists()) {
                                val painter = rememberAsyncImagePainter(model = siswa.foto)
                                Image(painter, contentDescription = "Foto", Modifier.size(100.dp).clip(CircleShape))
                            } else {
                                Box(
                                    Modifier.size(100.dp).clip(CircleShape).background(Color(0xFFE8F0FE)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        siswa.nama.firstOrNull()?.toString()?.uppercase() ?: "?",
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A73E8),
                                    )
                                }
                            }
                        }
                        // QR code
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.size(120.dp),
                            )
                            Text("Scan untuk verifikasi", fontSize = 10.sp, color = Color(0xFF5F6368))
                        }
                    }
                }
            }

            // ── 2. BIODATA ──
            item {
                Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Biodata Siswa", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        InfoRow("NIS", siswa.nis)
                        InfoRow("Nama Lengkap", siswa.nama)
                        InfoRow("Kelas", s.kelasInfo?.nama ?: "N/A")
                        s.kelasInfo?.waliKelas?.let { InfoRow("Wali Kelas", it) }
                        InfoRow("Tanggal Lahir", siswa.tanggalLahir ?: "-")
                        InfoRow("Alamat", siswa.alamat ?: "-")
                        InfoRow("No. HP Orang Tua", siswa.noHpOrtu ?: "-")
                    }
                }
            }

            // ── 3. ATTENDANCE SUMMARY ──
            item {
                Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Rekap Kehadiran", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        if (s.attendanceSummary.isEmpty()) {
                            Text("Belum ada data absensi", color = Color(0xFF5F6368), fontSize = 13.sp)
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                s.attendanceSummary.forEach { item ->
                                    val color = when (item.status) {
                                        "Hadir" -> Color(0xFF34A853)
                                        "Izin" -> Color(0xFFFBBC04)
                                        "Sakit" -> Color(0xFFFF9800)
                                        "Alfa" -> Color(0xFFD93025)
                                        else -> Color(0xFF5F6368)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(item.count.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
                                        Text(item.status, fontSize = 11.sp, color = Color(0xFF5F6368))
                                    }
                                }
                            }
                            Text("Total: ${s.totalAbsensi} hari", fontSize = 12.sp, color = Color(0xFF5F6368), modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }

            // ── 4. GRADES PER SUBJECT ──
            if (s.subjectGrades.isNotEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Daftar Nilai", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
                items(s.subjectGrades) { g ->
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(1.dp),
                    ) {
                        Row(
                            Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(g.mapelNama, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Terakhir: ${g.latestNilai} · ${g.jumlah} nilai", fontSize = 11.sp, color = Color(0xFF5F6368))
                            }
                            Text(String.format("%.1f", g.avgNilai), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1A73E8))
                        }
                    }
                }
            }

            // ── 5. DISCIPLINE POINTS ──
            item {
                Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Poin Disiplin", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("+${s.totalPoinPositif}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34A853))
                                Text("Prestasi", fontSize = 11.sp, color = Color(0xFF5F6368))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("-${s.totalPoinNegatif}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD93025))
                                Text("Pelanggaran", fontSize = 11.sp, color = Color(0xFF5F6368))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val net = s.totalPoinPositif - s.totalPoinNegatif
                                Text("${if (net >= 0) "+" else ""}$net", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                                    color = if (net >= 0) Color(0xFF34A853) else Color(0xFFD93025))
                                Text("Net", fontSize = 11.sp, color = Color(0xFF5F6368))
                            }
                        }
                    }
                }
            }

            // ── 6. PRINT ACTIONS ──
            item {
                Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Cetak", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { cardPdfLauncher.launch("kartu_${siswa.nis}.pdf") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Cetak Kartu Siswa")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { biodataPdfLauncher.launch("biodata_${siswa.nis}.pdf") },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Cetak Biodata (A4)")
                        }
                    }
                }
            }

            // ── 7. ARCHIVE ──
            item {
                Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Arsip", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showArchiveDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD93025)),
                        ) {
                            Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Arsipkan Siswa")
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }

        // Archive confirmation dialog
        if (showArchiveDialog) {
            AlertDialog(
                onDismissRequest = { showArchiveDialog = false },
                title = { Text("Arsipkan Siswa?") },
                text = { Text("Apakah Anda yakin ingin mengarsipkan ${siswa.nama}? Siswa yang diarsipkan tidak akan muncul di daftar aktif.") },
                confirmButton = {
                    TextButton(onClick = {
                        showArchiveDialog = false
                        viewModel.archiveStudent { onArchived() }
                    }) { Text("Arsipkan", color = Color(0xFFD93025)) }
                },
                dismissButton = {
                    TextButton(onClick = { showArchiveDialog = false }) { Text("Batal") }
                },
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(140.dp)) {
            Text(label, fontSize = 13.sp, color = Color(0xFF5F6368))
        }
        Text(value, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}
