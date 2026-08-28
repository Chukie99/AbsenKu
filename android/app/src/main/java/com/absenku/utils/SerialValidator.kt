package com.absenku.utils

import java.security.MessageDigest

/**
 * SerialValidator — verifies a user-entered serial number against the
 * device's Device ID. The serial MUST equal the first 8 hex chars of
 * SHA256(deviceId + "AbsenKuSalt2025").
 */
object SerialValidator {
    private const val SALT = "AbsenKuSalt2025"

    /** @return true if [serial] matches the expected hash for [deviceId]. */
    fun isValid(deviceId: String, serial: String): Boolean {
        if (deviceId.isBlank() || serial.isBlank()) return false
        val input = deviceId.uppercase() + SALT
        val hash = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val hex = hash.joinToString("") { "%02x".format(it) }
        val expected = hex.substring(0, 8).uppercase()
        return serial.trim().uppercase() == expected
    }
}
