package com.absenku.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.VerticalAlignment
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * PDF generation utilities using iText 7.
 *
 * Provides three document types mirroring the KelasFun features:
 *  - generateStudentCards → ID-card sized PDF with QR codes
 *  - generateBiodata → A4 biodata form
 *  - generateReportCard → A4 academic report card
 *
 * All output is written to a content-resolver Uri (SAF) or returned as a File.
 */
object PdfGenerator {

    private val headerColor = DeviceRgb(0x2D, 0x37, 0x48)
    private val accentColor = DeviceRgb(0x4F, 0xD1, 0xC5)
    private val lightBg = DeviceRgb(0xF7, 0xFA, 0xFC)
    private val borderColor = DeviceRgb(0xCB, 0xD5, 0xE0)
    private val grey700 = DeviceRgb(0x4A, 0x55, 0x68)
    private val grey500 = DeviceRgb(0x71, 0x80, 0x9A)
    private val grey200 = DeviceRgb(0xED, 0xF2, 0xF7)

    // ID Card dimensions in points (1mm = 2.83465pt) — 54mm × 86mm
    private const val ID_W_MM = 54f
    private const val ID_H_MM = 86f
    private const val MM_TO_PT = 2.83465f
    private val ID_CARD_SIZE = Rectangle(ID_W_MM * MM_TO_PT, ID_H_MM * MM_TO_PT)

    // ─────────────────────────────────────────────────────────────────────
    // 1) STUDENT ID CARDS — one page per student
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Generate ID cards (54×86mm) for a list of students.
     *
     * @param students list of (nis, nama, kelasNama, fotoPath?)
     * @param schoolName shown in the header
     * @param output destination Uri from SAF
     * @return true on success
     */
    fun generateStudentCards(
        context: Context,
        students: List<StudentCardData>,
        schoolName: String?,
        output: Uri,
    ): Boolean = try {
        val stream = context.contentResolver.openOutputStream(output) ?: return false
        val pdf = PdfDocument(PdfWriter(stream))
        Document(pdf, ID_CARD_SIZE).use { doc ->
            doc.setMargins(0f, 0f, 0f, 0f)
            students.forEach { s ->
                doc.add(buildIdCard(s, schoolName))
                doc.add(Paragraph().setHeight(0f)) // ensure page break for next
            }
        }
        stream.close()
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    /**
     * Variant: write to a File (for BatchPrintManager).
     */
    fun generateStudentCardsToFile(
        students: List<StudentCardData>,
        schoolName: String?,
        file: File,
    ): File = try {
        val out = FileOutputStream(file)
        val pdf = PdfDocument(PdfWriter(out))
        Document(pdf, ID_CARD_SIZE).use { doc ->
            doc.setMargins(0f, 0f, 0f, 0f)
            students.forEach { s -> doc.add(buildIdCard(s, schoolName)) }
        }
        out.close()
        file
    } catch (e: Exception) {
        e.printStackTrace()
        file
    }

    private fun buildIdCard(s: StudentCardData, schoolName: String?): Table {
        val photoBmp: Bitmap? = s.fotoPath?.let { loadBitmapFromFile(it) }

        // Generate QR as bitmap
        val qrBmp: Bitmap = try {
            QrCodeGenerator.generate("ABS:${s.nis}:${s.nama}:${s.kelasNama}", 200)
        } catch (e: Exception) { null } ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        // Container table — full card area, 1 column
        val card = Table(1).useAllAvailableWidth()
        card.setHeight(ID_CARD_SIZE.height)
        card.setBorder(SolidBorder(borderColor, 0.5f))
        card.setBackgroundColor(ColorConstants.WHITE)

        // ── HEADER ──
        val header = Table(1).useAllAvailableWidth()
        header.setBackgroundColor(headerColor)
        header.addCell(
            Cell().setBorder(Border.NO_BORDER)
                .setPadding(6f)
                .add(
                    Paragraph(schoolName?.takeIf { it.isNotBlank() } ?: "SEKOLAH")
                        .setFontColor(ColorConstants.WHITE)
                        .setFontSize(8f)
                        .setBold()
                        .setTextAlignment(TextAlignment.CENTER)
                )
                .add(
                    Paragraph("KARTU SISWA")
                        .setFontColor(ColorConstants.WHITE)
                        .setFontSize(6f)
                        .setBold()
                        .setTextAlignment(TextAlignment.CENTER)
                        .setPaddingTop(2f)
                )
        )
        card.addHeaderCell(Cell().setBorder(Border.NO_BORDER).setPadding(0f).add(header))

        // ── CONTENT (photo + info) ──
        val content = Table(1).useAllAvailableWidth()
        content.setBackgroundColor(lightBg)
        val contentCell = Cell().setBorder(Border.NO_BORDER).setPadding(6f).setVerticalAlignment(VerticalAlignment.MIDDLE)

        // Photo
        val photoCell = Cell().setBorder(SolidBorder(accentColor, 1.5f)).setWidth(50f).setHeight(60f)
        if (photoBmp != null) {
            val img = Image(ImageDataFactory.create(bitmapToBytes(photoBmp))).setWidth(48f).setHeight(58f)
            photoCell.add(img)
        } else {
            photoCell.setBackgroundColor(grey200)
            photoCell.add(
                Paragraph("FOTO").setFontColor(grey500).setFontSize(6f).setTextAlignment(TextAlignment.CENTER)
            )
        }
        contentCell.add(photoCell.setHorizontalAlignment(HorizontalAlignment.CENTER))

        contentCell.add(Paragraph("NIS   : ${s.nis}").setFontSize(6f).setFontColor(grey700).setTextAlignment(TextAlignment.CENTER).setMarginTop(4f))
        contentCell.add(Paragraph("Nama  : ${s.nama}").setFontSize(7f).setBold().setFontColor(headerColor).setTextAlignment(TextAlignment.CENTER))
        contentCell.add(Paragraph("Kelas : ${s.kelasNama}").setFontSize(6f).setFontColor(grey700).setTextAlignment(TextAlignment.CENTER))

        content.addCell(contentCell)
        card.addCell(Cell().setBorder(Border.NO_BORDER).setPadding(0f).add(content))

        // ── QR FOOTER ──
        val qrCell = Cell().setBorder(Border.NO_BORDER).setPadding(4f)
        qrCell.setHorizontalAlignment(HorizontalAlignment.CENTER)
        val qrImg = Image(ImageDataFactory.create(bitmapToBytes(qrBmp))).setWidth(36f).setHeight(36f)
        qrCell.add(qrImg)
        qrCell.add(Paragraph("SCAN UNTUK VERIFIKASI").setFontSize(5f).setFontColor(grey500).setTextAlignment(TextAlignment.CENTER))
        card.addCell(qrCell)

        return card
    }

    // ─────────────────────────────────────────────────────────────────────
    // 2) BIODATA — A4 form
    // ─────────────────────────────────────────────────────────────────────

    fun generateBiodata(
        context: Context,
        nis: String,
        fullName: String,
        className: String,
        gender: String? = null,
        birthDate: String? = null,
        address: String? = null,
        parentPhone: String? = null,
        schoolName: String? = null,
        output: Uri,
    ): Boolean = try {
        val stream = context.contentResolver.openOutputStream(output) ?: return false
        val pdf = PdfDocument(PdfWriter(stream))
        Document(pdf, PageSize.A4).use { doc ->
            if (!schoolName.isNullOrBlank()) {
                doc.add(
                    Paragraph(schoolName)
                        .setFontSize(16f).setBold()
                        .setTextAlignment(TextAlignment.CENTER)
                )
            }
            doc.add(
                Paragraph("FORM BIODATA SISWA")
                    .setFontSize(14f).setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(4f)
            )
            doc.add(Paragraph(" ").setMarginTop(16f))

            biodataRow("NIS", nis).also { doc.add(it) }
            biodataRow("Nama Lengkap", fullName).also { doc.add(it) }
            biodataRow("Kelas", className).also { doc.add(it) }
            biodataRow("Jenis Kelamin", gender ?: "-").also { doc.add(it) }
            biodataRow("Tanggal Lahir", birthDate ?: "-").also { doc.add(it) }
            biodataRow("Alamat", address ?: "-").also { doc.add(it) }
            biodataRow("No. HP Orang Tua", parentPhone ?: "-").also { doc.add(it) }
        }
        stream.close()
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    private fun biodataRow(label: String, value: String): Table {
        val t = Table(floatArrayOf(150f, 6f, 360f)).useAllAvailableWidth()
        t.addCell(Cell().add(Paragraph(label)).setBorder(Border.NO_BORDER))
        t.addCell(Cell().add(Paragraph(":")).setBorder(Border.NO_BORDER))
        t.addCell(Cell().add(Paragraph(value)).setBorder(Border.NO_BORDER))
        return t
    }

    // ─────────────────────────────────────────────────────────────────────
    // 3) REPORT CARD — A4 with grades table
    // ─────────────────────────────────────────────────────────────────────

    data class ReportGradeRow(
        val subject: String,
        val uts: String,
        val uas: String,
        val tugas: String,
        val average: String,
    )

    fun generateReportCard(
        context: Context,
        studentName: String,
        nis: String,
        className: String,
        semester: String,
        grades: List<ReportGradeRow>,
        totalViolationPoints: Int,
        totalAchievementPoints: Int,
        rank: Int,
        totalStudents: Int,
        schoolName: String?,
        output: Uri,
    ): Boolean = try {
        val stream = context.contentResolver.openOutputStream(output) ?: return false
        val pdf = PdfDocument(PdfWriter(stream))
        Document(pdf, PageSize.A4).use { doc ->
            if (!schoolName.isNullOrBlank()) {
                doc.add(
                    Paragraph(schoolName)
                        .setFontSize(16f).setBold()
                        .setTextAlignment(TextAlignment.CENTER)
                )
            }
            doc.add(
                Paragraph("LAPORAN HASIL BELAJAR")
                    .setFontSize(14f).setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(4f)
            )
            doc.add(
                Paragraph("Semester: $semester")
                    .setFontSize(11f)
                    .setTextAlignment(TextAlignment.CENTER)
            )
            doc.add(Paragraph(" ").setMarginTop(16f))

            biodataRow("Nama", studentName).also { doc.add(it) }
            biodataRow("NIS", nis).also { doc.add(it) }
            biodataRow("Kelas", className).also { doc.add(it) }

            doc.add(Paragraph(" ").setMarginTop(16f))
            doc.add(Paragraph("Daftar Nilai").setBold().setFontSize(12f))

            val table = Table(floatArrayOf(30f, 150f, 70f, 70f, 70f, 80f)).useAllAvailableWidth()
            val headerStyle = TextAlignment.CENTER
            listOf("No", "Mapel", "UTS", "UAS", "Tugas", "Rata-rata").forEach { h ->
                table.addHeaderCell(
                    Cell().add(Paragraph(h).setBold())
                        .setTextAlignment(headerStyle)
                        .setBackgroundColor(DeviceRgb(0xE3, 0xF2, 0xFD))
                )
            }
            grades.forEachIndexed { i, g ->
                table.addCell(Cell().add(Paragraph("${i + 1}")).setTextAlignment(TextAlignment.CENTER))
                table.addCell(Cell().add(Paragraph(g.subject)))
                table.addCell(Cell().add(Paragraph(g.uts)).setTextAlignment(TextAlignment.CENTER))
                table.addCell(Cell().add(Paragraph(g.uas)).setTextAlignment(TextAlignment.CENTER))
                table.addCell(Cell().add(Paragraph(g.tugas)).setTextAlignment(TextAlignment.CENTER))
                table.addCell(Cell().add(Paragraph(g.average)).setTextAlignment(TextAlignment.CENTER))
            }
            doc.add(table)

            doc.add(Paragraph(" ").setMarginTop(16f))
            biodataRow("Poin Prestasi", "+$totalAchievementPoints").also { doc.add(it) }
            biodataRow("Poin Pelanggaran", "-$totalViolationPoints").also { doc.add(it) }
            biodataRow("Peringkat", "$rank dari $totalStudents").also { doc.add(it) }
        }
        stream.close()
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

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
