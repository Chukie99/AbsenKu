package com.absenku.sync

import android.content.Context
import com.absenku.data.model.PairedDevice
import com.absenku.data.repository.Repository
import com.absenku.utils.DeviceIdHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom

/**
 * PairingManager — orchestrates the first-time pairing between an HP device
 * and the Desktop hub.
 *
 * Flow:
 *  1. Desktop displays a 6-digit PIN (valid 5 min).
 *  2. HP calls [startPairing] with PIN + target Desktop host.
 *  3. HP sends DeviceId + PIN to Desktop /pair/verify endpoint.
 *  4. Desktop returns a long random pairingToken if valid → stored in SharedPreferences.
 *  5. Token is reused for all subsequent syncs (until revoked).
 */
class PairingManager(
    private val context: Context,
    private val repo: Repository,
) {

    private val prefs = context.getSharedPreferences("absenku_sync", Context.MODE_PRIVATE)

    /** Returns the saved pairing token, or null if not paired. */
    fun getPairingToken(): String? = prefs.getString("pair_token", null)

    /** Device name shown on the Desktop side. */
    fun getDeviceName(): String = android.os.Build.MODEL + " (#" + DeviceIdHelper.getDeviceId(context) + ")"

    /**
     * Verify a 6-digit PIN against the Desktop pairing endpoint.
     * @return Pair(isSuccess, message)
     */
    suspend fun verifyPin(host: String, pin: String, deviceName: String = getDeviceName()): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val deviceId = DeviceIdHelper.getDeviceId(context)
        try {
            val url = "http://$host:5000/pair/verify"
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val payload = """{"deviceId":"$deviceId","pin":"$pin","deviceName":"$deviceName"}"""
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val resp = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val gson = com.google.gson.Gson()
            val body = gson.fromJson(resp, PairVerifyResp::class.java)
            if (body.ok && body.token != null) {
                // Save token + device record
                prefs.edit().putString("pair_token", body.token).apply()
                repo.addPairedDevice(PairedDevice(
                    deviceName = deviceName,
                    deviceId = deviceId,
                    pairingToken = body.token,
                    lastSyncAt = System.currentTimeMillis()
                ))
                Pair(true, "Pairing berhasil! Token tersimpan.")
            } else {
                Pair(false, body.error ?: "PIN tidak valid.")
            }
        } catch (e: Exception) {
            Pair(false, "Gagal hubungi Desktop: ${e.message}")
        }
    }

    /** Force-unpair: clear token & mark remote device revoked locally. */
    suspend fun revokePairing() {
        prefs.edit().remove("pair_token").apply()
        repo.getPairedByDeviceId(DeviceIdHelper.getDeviceId(context))?.let { repo.revokePairedDevice(it.id) }
    }

    private data class PairVerifyResp(val ok: Boolean, val token: String?, val error: String?)
}
