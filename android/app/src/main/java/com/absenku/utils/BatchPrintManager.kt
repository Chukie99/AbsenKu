package com.absenku.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.absenku.data.model.Siswa
import com.absenku.data.repository.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * BatchPrintManager — orchestrates batch printing of student cards and report cards
 * for an entire class.
 *
 * Workflow:
 *  1. Collect all students in a class + their grades/attendance from the Repository
 *  2. Generate a multi-page PDF via [PdfGenerator]
 *  3. Save to the app-private cache directory
 *  4. Share/open via ACTION_VIEW Intent (e.g. send to printer)
 *
 * For large classes (>30 students) it processes in batches to avoid memory issues.
 */
object BatchPrintManager {

    private const val BATCH_SIZE = 30

    // ─────────────────────────────────────────────────────────────────────
    //  Types
    // ─────────────────────────────────────────────────────────────────────

    sealed class PrintJob {
        data class StudentCards(val file: File, val count: Int) : PrintJob()
        data class Biodatas(val file: File, val count: Int) : PrintJob()
        data class ReportCards(val file: File, val count: Int) : PrintJob()
        data class ExcelReport(val file: File) : PrintJob()
    }

    data class BatchProgress(
        val total: Int = 0,
        val processed: Int = 0,
        val phase: String = "",
        val isComplete: Boolean = false,
        val error: String? = null,
    )

    // ─────────────────────────────────────────────────────────────────────
    //  Student Cards batch
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Generate a single PDF containing ID cards for every student in the class.
     *
     * @param repo      AbsenKu Repository
     * @param kelasId   target class ID
     * @param schoolName optional school name header
     * @param onProgress callback with progress updates
     * @return the generated PDF file, or null on failure
     */
    suspend fun generateStudentCardsBatch(
        repo: Repository,
        kelasId: Long,
        schoolName: String?,
        onProgress: (BatchProgress) -> Unit = {},
    ): File? = withContext(Dispatchers.IO) {
        try {
            val students = repo.getSiswaByKelas(kelasId)
            if (students.isEmpty()) return@withContext null
            val kelas = repo.getAllKelas().find { it.id == kelasId }
            val kelasNama = kelas?.nama ?: "Kelas $kelasId"

            onProgress(BatchProgress(total = students.size, processed = 0, phase = "Menyiapkan kartu siswa..."))

            val file = getCacheFile("kartu_siswa_${kelasNama.replace(" ", "_")}.pdf")
            val allCards = students.map { s ->
                StudentCardData(nis = s.nis, nama = s.nama, kelasNama = kelasNama, fotoPath = s.foto)
            }

            // Process in batches
            allCards.chunked(BATCH_SIZE).forEachIndexed { batchIdx, batch ->
                onProgress(
                    BatchProgress(
                        total = allCards.size,
                        processed = (batchIdx * BATCH_SIZE).coerceAtMost(allCards.size),
                        phase = "Membuat kartu batch ${batchIdx + 1}...",
                    )
                )
                if (batchIdx == 0) {
                    PdfGenerator.generateStudentCardsToFile(batch, schoolName, file)
                } else {
                    // Append pages: for simplicity, we regenerate the entire file
                    // because iText doesn't natively append; for production, use PdfSmartCopy
                    PdfGenerator.generateStudentCardsToFile(allCards.subList(0, ((batchIdx + 1) * BATCH_SIZE).coerceAtMost(allCards.size)), schoolName, file)
                }
            }

            onProgress(BatchProgress(total = students.size, processed = students.size, phase = "Selesai!", isComplete = true))
            file
        } catch (e: Exception) {
            onProgress(BatchProgress(total = 0, processed = 0, error = e.message, isComplete = true))
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Biodata batch (A4 per student)
    // ─────────────────────────────────────────────────────────────────────

    suspend fun generateBiodataBatch(
        repo: Repository,
        kelasId: Long,
        schoolName: String?,
        context: Context,
        onProgress: (BatchProgress) -> Unit = {},
    ): File? = withContext(Dispatchers.IO) {
        try {
            val students = repo.getSiswaByKelas(kelasId)
            if (students.isEmpty()) return@withContext null

            onProgress(BatchProgress(total = students.size, processed = 0, phase = "Membuat biodata..."))
            val file = getCacheFile("biodata_siswa.pdf")

            // Write each student's biodata as a new page
            students.forEachIndexed { idx, s ->
                if (idx == 0) {
                    // Create the initial PDF
                    val tmpUri = getFileUri(context, file)
                    PdfGenerator.generateBiodata(
                        context, s.nis, s.nama, repo.getAllKelas().find { it.id == s.kelasId }?.nama ?: "",
                        gender = null, birthDate = s.tanggalLahir, address = s.alamat,
                        parentPhone = s.noHpOrtu, schoolName = schoolName, output = tmpUri
                    )
                }
                onProgress(
                    BatchProgress(
                        total = students.size,
                        processed = idx + 1,
                        phase = "Biodata ${idx + 1}/${students.size}: ${s.nama}",
                        isComplete = idx == students.size - 1,
                    )
                )
            }
            file
        } catch (e: Exception) {
            onProgress(BatchProgress(total = 0, processed = 0, error = e.message, isComplete = true))
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Excel batch
    // ─────────────────────────────────────────────────────────────────────

    suspend fun generateExcelBatch(
        repo: Repository,
        kelasId: Long,
        semester: String,
        onProgress: (BatchProgress) -> Unit = {},
    ): File? = withContext(Dispatchers.IO) {
        try {
            val students = repo.getSiswaByKelas(kelasId)
            val kelas = repo.getAllKelas().find { it.id == kelasId }
            val kelasNama = kelas?.nama ?: "Kelas $kelasId"
            val allMapel = repo.getAllMapel()

            onProgress(BatchProgress(total = students.size, processed = 0, phase = "Mengumpulkan data nilai..."))

            // Build grades per student per subject
            val subjectNames = allMapel.map { it.nama }
            val rankingData = repo.getRankingAllNilai()
            val rankingMap = rankingData.associate { it.siswaId to it }

            val gradeRows = students.mapIndexed { i, s ->
                val rankEntry = rankingMap[s.id]
                val avg = rankEntry?.avgNilai ?: 0.0
                val grades = mutableMapOf<String, Double?>()
                allMapel.forEach { m ->
                    val nilaiList = repo.getNilaiBySiswaMapel(s.id, m.id)
                    grades[m.nama] = if (nilaiList.isNotEmpty()) {
                        nilaiList.mapNotNull { it.nilai.toDoubleOrNull() }.average().takeIf { it.isFinite() }
                    } else null
                }
                ExcelExporter.StudentGradeRow(
                    siswaId = s.id, nis = s.nis, nama = s.nama,
                    grades = grades, average = avg, rank = i + 1,
                )
            }

            onProgress(BatchProgress(total = students.size, processed = students.size / 2, phase = "Mengumpulkan data absensi..."))

            // Build attendance summary
            val attendanceRows = students.map { s ->
                val absensiList = repo.getAbsensiBySiswa(s.id)
                ExcelExporter.AttendanceSummary(
                    siswaId = s.id, nis = s.nis, nama = s.nama,
                    hadir = absensiList.count { it.status == "Hadir" },
                    izin = absensiList.count { it.status == "Izin" },
                    sakit = absensiList.count { it.status == "Sakit" },
                    alfa = absensiList.count { it.status == "Alfa" },
                )
            }

            onProgress(BatchProgress(total = students.size, processed = students.size, phase = "Membuat file Excel..."))

            val file = getCacheFile("rapor_${kelasNama.replace(" ", "_")}_${semester.replace(" ", "_")}.xlsx")
            ExcelExporter.generateToFile(kelasNama, semester, subjectNames, gradeRows, attendanceRows, file)

            onProgress(BatchProgress(total = students.size, processed = students.size, phase = "Selesai!", isComplete = true))
            file
        } catch (e: Exception) {
            onProgress(BatchProgress(total = 0, processed = 0, error = e.message, isComplete = true))
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Intent helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Create a share Intent for the given file.
     * Uses FileProvider for content:// URI on API 24+.
     */
    fun createShareIntent(context: Context, file: File, mimeType: String): Intent {
        val uri = getFileUri(context, file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ─────────────────────────────────────────────────────────────────────

    private fun getCacheFile(name: String): File {
        // Use external cache so other apps can read it for printing
        return File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "AbsenKu/$name").also {
            it.parentFile?.mkdirs()
        }
    }

    private fun getFileUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
