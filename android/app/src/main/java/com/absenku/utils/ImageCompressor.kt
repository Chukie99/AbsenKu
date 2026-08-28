package com.absenku.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * ImageCompressor — crops-to-3:4, resizes to max 800x1067, JPEG q80.
 *
 * Storage path: <files-dir>/foto/<tahunAjaran>/<kelasId>/<nis>.jpg
 * Old file deleted on re-capture (no orphans).
 */
object ImageCompressor {

    private const val MAX_W = 800   // portrait: width 800 → height ~1067 (3:4)
    private const val QUALITY = 80

    /**
     * Crop [src] to 3:4 aspect (center crop) and resize+compress.
     * Returns absolute path of the saved file.
     */
    fun saveStudentPhoto(context: Context, src: Bitmap, tahunAjaran: String, kelasId: Long, nis: String): String {
        val cropped = centerCrop34(src)
        val scaled = scaleToFit(cropped)
        val dir = File(context.filesDir, "foto/$tahunAjaran/$kelasId").apply { mkdirs() }
        val file = File(dir, "$nis.jpg")
        if (file.exists()) file.delete()
        FileOutputStream(file).use {
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, it)
        }
        return file.absolutePath
    }

    /** Center-crop a bitmap to a 3:4 aspect ratio portrait. */
    private fun centerCrop34(bmp: Bitmap): Bitmap {
        val w = bmp.width
        val targetH = (w * 4.0 / 3.0).toInt()
        val targetW = w
        val srcH = bmp.height
        val srcW = bmp.width
        val scale = maxOf(targetW.toFloat() / srcW, targetH.toFloat() / srcH)
        val scaledW = (srcW * scale).toInt()
        val scaledH = (srcH * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(bmp, scaledW, scaledH, true)
        val dx = (scaledW - targetW) / 2
        val dy = (scaledH - targetH) / 2
        return Bitmap.createBitmap(scaled, dx.coerceAtLeast(0), dy.coerceAtLeast(0), targetW, targetH)
    }

    /** Resize keeping aspect, max width 800 px. */
    private fun scaleToFit(bmp: Bitmap): Bitmap {
        val w = bmp.width
        val h = bmp.height
        if (w <= MAX_W) return bmp
        val ratio = MAX_W.toFloat() / w
        val newW = MAX_W
        val newH = (h * ratio).toInt()
        val matrix = Matrix().apply { setScale(ratio, ratio) }
        return Bitmap.createBitmap(bmp, 0, 0, w, h, matrix, true)
    }

    /** Load a saved student photo (may return null if not found). */
    fun load(context: Context, tahunAjaran: String, kelasId: Long, nis: String): Bitmap? {
        val path = File(context.filesDir, "foto/$tahunAjaran/$kelasId/$nis.jpg")
        return if (path.exists()) BitmapFactory.decodeFile(path.absolutePath) else null
    }

    /** Uri-based picker result → Bitmap (for gallery/cropped pick). */
    fun uriToBitmap(context: Context, uri: Uri): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { e.printStackTrace(); null }
}
