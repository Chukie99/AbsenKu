package com.absenku.utils

import android.content.Context
import android.net.Uri
import com.absenku.data.model.Siswa
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.InputStreamReader

/**
 * CsvImporter — reads Siswa data from CSV files via the Storage Access Framework.
 * Expected CSV columns: NIS, Nama, Kelas ID, Alamat, No HP Ortu, Tanggal Lahir
 * (matches CsvExporter output format)
 */
object CsvImporter {

    data class ImportResult(
        val siswaList: List<Siswa>,
        val errors: List<String>,
    )

    /**
     * Parse siswa data from a CSV file URI.
     * @return ImportResult with successfully parsed records and error messages.
     */
    fun importSiswa(context: Context, uri: Uri): ImportResult {
        val siswaList = mutableListOf<Siswa>()
        val errors = mutableListOf<String>()

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = InputStreamReader(inputStream)
                val parser = CSVParser(reader, CSVFormat.DEFAULT
                    .withHeader()
                    .withIgnoreHeaderCase()
                    .withTrim())

                var rowNum = 0
                for (record: CSVRecord in parser) {
                    rowNum++
                    try {
                        val nis = record.get("NIS")?.trim() ?: ""
                        val nama = record.get("Nama")?.trim() ?: ""
                        val kelasId = record.get("Kelas ID")?.trim()?.toLongOrNull() ?: 0L
                        val alamat = record.get("Alamat")?.trim()?.ifBlank { null }
                        val noHp = record.get("No HP Ortu")?.trim()?.ifBlank { null }
                        val tglLahir = record.get("Tanggal Lahir")?.trim()?.ifBlank { null }

                        if (nis.isBlank()) {
                            errors.add("Baris $rowNum: NIS kosong, dilewati")
                            continue
                        }
                        if (nama.isBlank()) {
                            errors.add("Baris $rowNum: Nama kosong, dilewati")
                            continue
                        }

                        siswaList.add(Siswa(
                            nis = nis,
                            nama = nama,
                            kelasId = kelasId,
                            alamat = alamat,
                            noHpOrtu = noHp,
                            tanggalLahir = tglLahir,
                        ))
                    } catch (e: Exception) {
                        errors.add("Baris $rowNum: ${e.message ?: "format error"}")
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("Gagal membaca file: ${e.message}")
        }

        return ImportResult(siswaList, errors)
    }
}
