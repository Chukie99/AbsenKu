package com.absenku.utils

import android.content.Context
import android.net.Uri
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.FileOutputStream

/**
 * Excel report generation using Apache POI.
 *
 * Produces a multi-sheet .xlsx file with:
 *  Sheet 1 "Rapor" — per-student grades across all subjects with average & ranking
 *  Sheet 2 "Presensi" — per-student attendance summary (Hadir/Izin/Sakit/Alfa)
 *
 * All data is pulled from Repository / Domain data and written to a SAF Uri.
 */
object ExcelExporter {

    // ─────────────────────────────────────────────────────────────────────
    //  Data carriers
    // ─────────────────────────────────────────────────────────────────────

    data class StudentGradeRow(
        val siswaId: Long,
        val nis: String,
        val nama: String,
        /** subject name → numeric score (null if no entry). */
        val grades: Map<String, Double?>,
        val average: Double,
        val rank: Int,
    )

    data class AttendanceSummary(
        val siswaId: Long,
        val nis: String,
        val nama: String,
        val hadir: Int,
        val izin: Int,
        val sakit: Int,
        val alfa: Int,
    )

    // ─────────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Generate the full report workbook and write to [output] via SAF.
     *
     * @param context  Android context for accessing [Uri]
     * @param output   SAF-created Uri (MIME = application/vnd.openxmlformats-officedocument.spreadsheetml.sheet)
     * @param className e.g. "X IPA 1"
     * @param semester e.g. "Ganjil 2025/2026"
     * @param subjects ordered list of subject names (columns in the grade sheet)
     * @param grades   per-student rows — MUST be pre-sorted by rank
     * @param attendance per-student attendance summary — same order as [grades]
     * @return true on success
     */
    fun generate(
        context: Context,
        output: Uri,
        className: String,
        semester: String,
        subjects: List<String>,
        grades: List<StudentGradeRow>,
        attendance: List<AttendanceSummary>,
    ): Boolean = try {
        val wb = XSSFWorkbook()
        val (headerCellFmt, centerCellFmt) = createStyles(wb)

        // ── Sheet 1: Rapor ──
        val rapor = wb.createSheet("Rapor")

        // Title
        var rowIdx = 0
        val titleRow = rapor.createRow(rowIdx)
        val titleCell = titleRow.createCell(0)
        titleCell.setCellValue("Rapor Digital — Kelas $className — Semester $semester")
        titleCell.cellStyle = headerCellFmt
        rowIdx++

        val subtitleRow = rapor.createRow(rowIdx)
        val subtitleCell = subtitleRow.createCell(0)
        subtitleCell.setCellValue("Periode: $semester")
        rowIdx += 2 // skip a blank row

        // Grade header
        val gradeHeaderRow = rapor.createRow(rowIdx)
        gradeHeaderRow.heightInPoints = 20f
        val gradeHeaders = mutableListOf("No", "NIS", "Nama Siswa")
        gradeHeaders.addAll(subjects)
        gradeHeaders.addAll(listOf("Rata-rata", "Ranking"))
        gradeHeaders.forEachIndexed { col, h ->
            val cell = gradeHeaderRow.createCell(col)
            cell.setCellValue(h)
            cell.cellStyle = headerCellFmt
        }
        rowIdx++

        // Grade data
        grades.forEachIndexed { i, g ->
            val r = rapor.createRow(rowIdx++)
            var col = 0
            r.createCell(col++).apply { setCellValue((i + 1).toDouble()); cellStyle = centerCellFmt }
            r.createCell(col++).apply { setCellValue(g.nis) }
            r.createCell(col++).apply { setCellValue(g.nama) }
            subjects.forEach { subj ->
                val score = g.grades[subj]
                r.createCell(col++).apply {
                    if (score != null) { setCellValue(score); cellStyle = centerCellFmt }
                    else { setCellValue("-") }
                }
            }
            r.createCell(col++).apply { setCellValue(g.average); cellStyle = centerCellFmt }
            r.createCell(col++).apply { setCellValue(g.rank.toDouble()); cellStyle = centerCellFmt }
        }

        // ── Sheet 2: Presensi ──
        val presensi = wb.createSheet("Presensi")
        rowIdx = 0

        val attTitleRow = presensi.createRow(rowIdx++)
        attTitleRow.createCell(0).apply { setCellValue("Rekapitulasi Presensi — Kelas $className"); cellStyle = headerCellFmt }

        rowIdx++ // blank row

        val attHeaderRow = presensi.createRow(rowIdx++)
        attHeaderRow.heightInPoints = 20f
        listOf("No", "NIS", "Nama Siswa", "Hadir", "Izin", "Sakit", "Alfa").forEachIndexed { col, h ->
            val cell = attHeaderRow.createCell(col)
            cell.setCellValue(h)
            cell.cellStyle = headerCellFmt
        }

        attendance.forEachIndexed { i, a ->
            val r = presensi.createRow(rowIdx++)
            r.createCell(0).apply { setCellValue((i + 1).toDouble()); cellStyle = centerCellFmt }
            r.createCell(1).apply { setCellValue(a.nis) }
            r.createCell(2).apply { setCellValue(a.nama) }
            r.createCell(3).apply { setCellValue(a.hadir.toDouble()); cellStyle = centerCellFmt }
            r.createCell(4).apply { setCellValue(a.izin.toDouble()); cellStyle = centerCellFmt }
            r.createCell(5).apply { setCellValue(a.sakit.toDouble()); cellStyle = centerCellFmt }
            r.createCell(6).apply { setCellValue(a.alfa.toDouble()); cellStyle = centerCellFmt }
        }

        // Auto-size columns for both sheets
        for (sheet in listOf(rapor, presensi)) {
            for (i in 0 until 20) sheet.autoSizeColumn(i)
        }

        // Write to output
        val stream = context.contentResolver.openOutputStream(output) ?: return false
        wb.write(stream)
        stream.close()
        wb.close()
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    /**
     * Variant: write to a File (for BatchPrintManager).
     */
    fun generateToFile(
        className: String,
        semester: String,
        subjects: List<String>,
        grades: List<StudentGradeRow>,
        attendance: List<AttendanceSummary>,
        file: java.io.File,
    ): java.io.File = try {
        val wb = XSSFWorkbook()
        val (headerCellFmt, centerCellFmt) = createStyles(wb)

        // Mirror the above logic but write to a File
        val rapor = wb.createSheet("Rapor")
        var rowIdx = 0
        rapor.createRow(rowIdx).createCell(0).apply { setCellValue("Rapor Digital — Kelas $className — Semester $semester"); cellStyle = headerCellFmt }
        rowIdx += 2

        val gradeHeaders = mutableListOf("No", "NIS", "Nama Siswa")
        gradeHeaders.addAll(subjects)
        gradeHeaders.addAll(listOf("Rata-rata", "Ranking"))
        val hdrRow = rapor.createRow(rowIdx++)
        gradeHeaders.forEachIndexed { col, h -> hdrRow.createCell(col).apply { setCellValue(h); cellStyle = headerCellFmt } }

        grades.forEachIndexed { i, g ->
            val r = rapor.createRow(rowIdx++)
            var col = 0
            r.createCell(col++).apply { setCellValue((i + 1).toDouble()); cellStyle = centerCellFmt }
            r.createCell(col++).apply { setCellValue(g.nis) }
            r.createCell(col++).apply { setCellValue(g.nama) }
            subjects.forEach { subj ->
                val score = g.grades[subj]
                r.createCell(col++).apply { if (score != null) { setCellValue(score); cellStyle = centerCellFmt } else { setCellValue("-") } }
            }
            r.createCell(col++).apply { setCellValue(g.average); cellStyle = centerCellFmt }
            r.createCell(col++).apply { setCellValue(g.rank.toDouble()); cellStyle = centerCellFmt }
        }

        val presensi = wb.createSheet("Presensi")
        rowIdx = 0
        presensi.createRow(rowIdx++).createCell(0).apply { setCellValue("Rekapitulasi Presensi — Kelas $className"); cellStyle = headerCellFmt }
        rowIdx++
        val attHdrRow = presensi.createRow(rowIdx++)
        listOf("No", "NIS", "Nama Siswa", "Hadir", "Izin", "Sakit", "Alfa").forEachIndexed { col, h -> attHdrRow.createCell(col).apply { setCellValue(h); cellStyle = headerCellFmt } }
        attendance.forEachIndexed { i, a ->
            val r = presensi.createRow(rowIdx++)
            r.createCell(0).apply { setCellValue((i + 1).toDouble()); cellStyle = centerCellFmt }
            r.createCell(1).apply { setCellValue(a.nis) }
            r.createCell(2).apply { setCellValue(a.nama) }
            r.createCell(3).apply { setCellValue(a.hadir.toDouble()); cellStyle = centerCellFmt }
            r.createCell(4).apply { setCellValue(a.izin.toDouble()); cellStyle = centerCellFmt }
            r.createCell(5).apply { setCellValue(a.sakit.toDouble()); cellStyle = centerCellFmt }
            r.createCell(6).apply { setCellValue(a.alfa.toDouble()); cellStyle = centerCellFmt }
        }

        for (sheet in listOf(rapor, presensi)) for (i in 0 until 20) sheet.autoSizeColumn(i)

        val fos = FileOutputStream(file)
        wb.write(fos)
        fos.close()
        wb.close()
        file
    } catch (e: Exception) {
        e.printStackTrace()
        file
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Style helpers
    // ─────────────────────────────────────────────────────────────────────

    private data class Styles(
        val header: org.apache.poi.ss.usermodel.CellStyle,
        val center: org.apache.poi.ss.usermodel.CellStyle,
    )

    private fun createStyles(wb: XSSFWorkbook): Styles {
        val header = wb.createCellStyle().apply {
            fillForegroundColor = IndexedColors.DARK_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
            val font = wb.createFont().apply { color = IndexedColors.WHITE.index; bold = true; fontHeightInPoints = 11 }
            setFont(font)
        }
        val center = wb.createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
        }
        return Styles(header, center)
    }
}
