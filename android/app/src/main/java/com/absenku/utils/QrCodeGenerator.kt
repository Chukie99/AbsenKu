package com.absenku.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * QR code generator for student identity (NIS-based).
 * Uses ZXing library to generate QR codes as Bitmap.
 */
object QrCodeGenerator {

    /**
     * Generate a QR code bitmap for the given data string.
     * @param data The data to encode (e.g. student NIS or JSON)
     * @param size Width and height in pixels
     * @return Bitmap of the QR code
     */
    fun generate(data: String, size: Int = 400): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    /**
     * Generate student QR data string — compact JSON-like format.
     */
    fun studentQrData(nis: String, nama: String, kelasId: Long): String {
        return "ABS:$nis:$nama:$kelasId"
    }
}
