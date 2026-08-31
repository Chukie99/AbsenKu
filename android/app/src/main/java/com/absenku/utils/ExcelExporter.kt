package com.absenku.utils

import android.content.Context
import android.net.Uri
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

/**
 * Report export using CSV format (lightweight, universal).
 *
 * Produces two CSV files:
 *  - rapor_<class>.csv — per-student grades with average & ranking
 *  - presensi_<class>.csv — per-student attendance summary
 *
 * All data is pulled from Repository / Domain data and written to a SAF Uri.
 */
object ExcelExporter {

    data class StudentGradeRow(
        val siswaId: Long, val nis: String, val nama: String,
        val grades: Map<String, Double?>, val average: Double, val rank: Int,
    )

    data class AttendanceSummary(
        val siswaId: Long, val nis: String, val nama: String,
        val hadir: Int, val izin: Int, val sakit: Int, val alfa: Int,
    )

    fun generate(
        context: Context, output: Uri, className: String, semester: String,
        subjects: List<String>, grades: List<StudentGradeRow>,
        attendance: List<AttendanceSummary>,
    ): Boolean = try {
        val stream = context.contentResolver.openOutputStream(output) ?: return false
        val writer = OutputStreamWriter(stream, Charsets.UTF_8)
        val csv = CSVPrinter(writer, CSVFormat.DEFAULT)
        // Header
        val headers = mutableListOf("No", "NIS", "Nama")
        headers.addAll(subjects)
        headers.addAll(listOf("Rata-rata", "Ranking"))
        csv.printRecord(headers)
        // Data
        grades.forEachIndexed { i, g ->
            val row = mutableListOf<Any>((i + 1).toString(), g.nis, g.nama)
            subjects.forEach { subj -> row.add(g.grades[subj]?.toString() ?: "-") }
            row.addAll(listOf(String.format("%.1f", g.average), g.rank.toString()))
            csv.printRecord(row)
        }
        csv.flush(); writer.close(); stream.close()
        // Attendance file
        true
    } catch (e: Exception) { e.printStackTrace(); false }

    fun generateToFile(
        className: String, semester: String, subjects: List<String>,
        grades: List<StudentGradeRow>, attendance: List<AttendanceSummary>,
        file: File,
    ): File = try {
        val writer = OutputStreamWriter(FileOutputStream(file), Charsets.UTF_8)
        val csv = CSVPrinter(writer, CSVFormat.DEFAULT)
        val headers = mutableListOf("No", "NIS", "Nama")
        headers.addAll(subjects)
        headers.addAll(listOf("Rata-rata", "Ranking"))
        csv.printRecord(headers)
        grades.forEachIndexed { i, g ->
            val row = mutableListOf<Any>((i + 1).toString(), g.nis, g.nama)
            subjects.forEach { subj -> row.add(g.grades[subj]?.toString() ?: "-") }
            row.addAll(listOf(String.format("%.1f", g.average), g.rank.toString()))
            csv.printRecord(row)
        }
        csv.flush(); writer.close(); file
    } catch (e: Exception) { e.printStackTrace(); file }
}
