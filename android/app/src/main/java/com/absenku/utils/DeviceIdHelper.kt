package com.absenku.utils

import android.provider.Settings
import android.content.Context
import java.security.MessageDigest

/**
 * DeviceIdHelper — generates a deterministic 8-char device fingerprint.
 *
 * Algorithm: SHA256(ANDROID_ID + "AbsenKuSalt2025"), take first 8 hex chars,
 * uppercased. This matches the desktop-side Python generator exactly.
 *
 * Note: salt is hardcoded (see spec) — sufficient for the sekolah market.
 * For stronger tamper-resistance later, move the salt into an NDK/JNI layer
 * so it isn't trivially extractable via decompilation.
 */
object DeviceIdHelper {
    private const val SALT = "AbsenKuSalt2025"

    fun getDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val raw = (androidId ?: "unknown") + SALT
        val hash = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        val hex = hash.joinToString("") { "%02x".format(it) }
        return hex.substring(0, 8).uppercase()
    }
}
