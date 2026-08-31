package com.absenku.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * PDF generation utilities using Android native PdfDocument API.
 *
 * Provides three document types mirroring the KelasFun features:
 *  - generateStudentCards → ID-card sized PDF with QR codes
 *  - generateBiodata → A4 biodata form
 *  - generateReportCard → A4 academic report card
 *
 * All output is written to a content-resolver Uri (SAF) or returned as a File.
 */
object PdfGenerator {

    private const val PAGE_W = 595   // A4 width in points
    private const val PAGE_H = 842   // A4 height in points
    private const val MARGIN = 40f
    private const val ID_PAGE_W = 153  // 54mm ≈ 153pt
    private const val ID_PAGE_H = 244  // 86mm ≈ 244pt

    private val paint = Paint().apply { isAntiAlias = true }

    // ─────────────────────────────────────────────────────────────────────
    // 1) STUDENT ID CARDS — one page per student
    // ─────────────────────────────────────────────────────────────────────

    fun generateStudentCards(
        context: Context,
        students: List<StudentCardData>,
        schoolName: String?,
        output: Uri,
    ): Boolean = try {
        val stream = context.contentResolver.openOutputStream(output) ?: return false
        val pdf = PdfDocument()
        students.forEach { s ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(ID_PAGE_W, ID_PAGE_H, students.indexOf(s) + 1).create())
            drawIdCard(page.canvas, s, schoolName)
            pdf.finishPage(page)
        }
        pdf.writeTo(stream)
        pdf.close()
        stream.close()
        true
    } catch (e: Exception) { e.printStackTrace(); false }

    fun generateStudentCardsToFile(
        students: List<StudentCardData>,
        schoolName: String?,
        file: File,
    ): File = try {
        val out = FileOutputStream(file)
        val pdf = PdfDocument()
        students.forEachIndexed { i, s ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(ID_PAGE_W, ID_PAGE_H, i + 1).create())
            drawIdCard(page.canvas, s, schoolName)
            pdf.finishPage(page)
        }
        pdf.writeTo(out)
        pdf.close()
        out.close()
        file
    } catch (e: Exception) { e.printStackTrace(); file }

    private fun drawIdCard(canvas: Canvas, s: StudentCardData, schoolName: String?) {
        val w = ID_PAGE_W.toFloat()
        // Header background
        paint.color = Color.parseColor("#2D3748")
        canvas.drawRect(0f, 0f, w, 50f, paint)
        // Header text
        paint.color = Color.WHITE; paint.textSize = 10f; paint.textAlign = Paint.Align.CENTER
        canvas.drawText(schoolName?.takeIf { it.isNotBlank() } ?: "SEKOLAH", w / 2, 22f, paint)
        paint.textSize = 8f
        canvas.drawText("KARTU SISWA", w / 2, 38f, paint)
        // QR code
        try {
            val qr = QrCodeGenerator.generate("ABS:${s.nis}:${s.nama}:${s.kelasNama}", 80)
            canvas.drawBitmap(qr, (w - 80) / 2, 55f, null)
        } catch (_: Exception) {}
        // Info
        paint.textAlign = Paint.Align.CENTER; paint.textSize = 8f; paint.color = Color.BLACK
        canvas.drawText("NIS: ${s.nis}", w / 2, 150f, paint)
        paint.textSize = 10f; paint.isFakeBoldText = true
        canvas.drawText(s.nama, w / 2, 168f, paint)
        paint.isFakeBoldText = false; paint.textSize = 8f; paint.color = Color.DKGRAY
        canvas.drawText("Kelas: ${s.kelasNama}", w / 2, 184f, paint)
        // Footer
        paint.textSize = 6f; paint.color = Color.GRAY
        canvas.drawText("SCAN UNTUK VERIFIKASI", w / 2, 235f, paint)
    }

    // ─────────────────────────────────────────────────────────────────────
    // 2) BIODATA — A4 form
    // ─────────────────────────────────────────────────────────────────────

    fun generateBiodata(
        context: Context, nis: String, fullName: String, className: String,
        gender: String? = null, birthDate: String? = null, address: String? = null,
        parentPhone: String? = null, schoolName: String? = null, output: Uri,
    ): Boolean = try {
        val stream = context.contentResolver.openOutputStream(output) ?: return false
        val pdf = PdfDocument()
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        val c = page.canvas; var y = 60f
        if (!schoolName.isNullOrBlank()) { paint.textSize = 16f; paint.isFakeBoldText = true; paint.textAlign = Paint.Align.CENTER; c.drawText(schoolName, PAGE_W / 2f, y, paint); y += 24f }
        paint.textSize = 14f; c.drawText("FORM BIODATA SISWA", PAGE_W / 2f, y, paint); y += 30f
        paint.isFakeBoldText = false; paint.textAlign = Paint.Align.LEFT; paint.textSize = 11f
        listOf("NIS" to nis, "Nama Lengkap" to fullName, "Kelas" to className,
            "Jenis Kelamin" to (gender ?: "-"), "Tanggal Lahir" to (birthDate ?: "-"),
            "Alamat" to (address ?: "-"), "No. HP Orang Tua" to (parentPhone ?: "-")
        ).forEach { (l, v) -> c.drawText("$l : $v", MARGIN, y, paint); y += 22f }
        pdf.finishPage(page); pdf.writeTo(stream); pdf.close(); stream.close(); true
    } catch (e: Exception) { e.printStackTrace(); false }

    // ─────────────────────────────────────────────────────────────────────
    // 3) REPORT CARD — A4 with grades table
    // ─────────────────────────────────────────────────────────────────────

    data class ReportGradeRow(val subject: String, val uts: String, val uas: String, val tugas: String, val average: String)

    fun generateReportCard(
        context: Context, studentName: String, nis: String, className: String,
        semester: String, grades: List<ReportGradeRow>, totalViolationPoints: Int,
        totalAchievementPoints: Int, rank: Int, totalStudents: Int,
        schoolName: String?, output: Uri,
    ): Boolean = try {
        val stream = context.contentResolver.openOutputStream(output) ?: return false
        val pdf = PdfDocument()
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        val c = page.canvas; var y = 60f
        if (!schoolName.isNullOrBlank()) { paint.textSize = 16f; paint.isFakeBoldText = true; paint.textAlign = Paint.Align.CENTER; c.drawText(schoolName, PAGE_W / 2f, y, paint); y += 24f }
        paint.textSize = 14f; c.drawText("LAPORAN HASIL BELAJAR", PAGE_W / 2f, y, paint); y += 20f
        paint.textSize = 11f; paint.isFakeBoldText = false; c.drawText("Semester: $semester", PAGE_W / 2f, y, paint); y += 30f
        paint.textAlign = Paint.Align.LEFT; paint.textSize = 11f
        listOf("Nama" to studentName, "NIS" to nis, "Kelas" to className).forEach { (l, v) -> c.drawText("$l : $v", MARGIN, y, paint); y += 20f }
        y += 10f; paint.isFakeBoldText = true; c.drawText("Daftar Nilai", MARGIN, y, paint); y += 20f; paint.isFakeBoldText = false
        // Table header
        val cols = floatArrayOf(MARGIN, MARGIN + 30, MARGIN + 180, MARGIN + 250, MARGIN + 320, MARGIN + 390, MARGIN + 460)
        paint.isFakeBoldText = true; paint.textSize = 9f
        listOf("No", "Mapel", "UTS", "UAS", "Tugas", "Rata-rata").forEachIndexed { i, h -> c.drawText(h, cols[i], y, paint) }
        y += 4f; paint.strokeWidth = 1f; c.drawLine(MARGIN, y, PAGE_W - MARGIN, y, paint); y += 14f; paint.isFakeBoldText = false
        paint.textSize = 9f
        grades.forEachIndexed { i, g ->
            c.drawText("${i + 1}", cols[0], y, paint); c.drawText(g.subject, cols[1], y, paint)
            c.drawText(g.uts, cols[2], y, paint); c.drawText(g.uas, cols[3], y, paint)
            c.drawText(g.tugas, cols[4], y, paint); c.drawText(g.average, cols[5], y, paint); y += 16f
        }
        y += 10f; paint.textSize = 11f
        listOf("Poin Prestasi" to "+$totalAchievementPoints", "Poin Pelanggaran" to "-$totalViolationPoints",
            "Peringkat" to "$rank dari $totalStudents").forEach { (l, v) -> c.drawText("$l : $v", MARGIN, y, paint); y += 20f }
        pdf.finishPage(page); pdf.writeTo(stream); pdf.close(); stream.close(); true
    } catch (e: Exception) { e.printStackTrace(); false }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private fun bitmapToBytes(bmp: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private fun loadBitmapFromFile(path: String): Bitmap? = try {
        val file = File(path)
        if (file.exists() && file.length() > 0) {
            android.graphics.BitmapFactory.decodeFile(path)
        } else null
    } catch (e: Exception) { null }
}

/** Plain data carrier for a single student ID card. */
data class StudentCardData(
    val nis: String,
    val nama: String,
    val kelasNama: String,
    val fotoPath: String? = null,
)
