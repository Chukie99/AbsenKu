package com.absenku.utils

import android.content.Context
import android.net.Uri
import com.absenku.data.model.Absensi
import com.absenku.data.model.Nilai
import com.absenku.data.model.Siswa
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.OutputStreamWriter

/**
 * CsvExporter — writes Siswa/Absensi/Nilai lists to CSV using Apache Commons CSV.
 * All exports write through the Storage Access Framework (create-document) URI stream,
 * so no WRITE_EXTERNAL_STORAGE permission is needed.
 */
object CsvExporter {

    /** Write a list of siswa to [uri] as CSV. Returns true on success. */
    fun exportSiswa(context: Context, uri: Uri, items: List<Siswa>): Boolean = try {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            OutputStreamWriter(out).use { writer ->
                CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("NIS", "Nama", "Kelas ID", "Alamat", "No HP Ortu", "Tanggal Lahir")).use { p ->
                    items.forEach { s ->
                        p.printRecord(s.nis, s.nama, s.kelasId, s.alamat ?: "", s.noHpOrtu ?: "", s.tanggalLahir ?: "")
                    }
                }
            }
        }
        true
    } catch (e: Exception) { e.printStackTrace(); false }

    /** Write a list of absensi to [uri] as CSV. */
    fun exportAbsensi(context: Context, uri: Uri, items: List<Absensi>): Boolean = try {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            OutputStreamWriter(out).use { writer ->
                CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("Tanggal", "Waktu Masuk", "Waktu Keluar", "Status", "Siswa ID", "Mapel ID")).use { p ->
                    items.forEach { a ->
                        p.printRecord(a.tanggal, a.waktuMasuk ?: "", a.waktuKeluar ?: "", a.status, a.siswaId, a.mapelId)
                    }
                }
            }
        }
        true
    } catch (e: Exception) { e.printStackTrace(); false }

    /** Write a list of nilai to [uri] as CSV. */
    fun exportNilai(context: Context, uri: Uri, items: List<Nilai>): Boolean = try {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            OutputStreamWriter(out).use { writer ->
                CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("Siswa ID", "Mapel ID", "Nilai", "Semester", "Tahun Ajaran")).use { p ->
                    items.forEach { n ->
                        p.printRecord(n.siswaId, n.mapelId, n.nilai, n.semester, n.tahunAjaran)
                    }
                }
            }
        }
        true
    } catch (e: Exception) { e.printStackTrace(); false }
}
