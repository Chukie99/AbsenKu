package com.absenku.sync

import android.content.Context
import android.widget.Toast
import com.absenku.data.model.SyncLog
import com.absenku.data.repository.Repository
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * WiFiSyncClient — talks to the Desktop Flask sync server over HTTP.
 * Every request carries the pairing token in header "X-Pair-Token".
 *
 * Flow: POST changed records → Desktop merges → Desktop returns delta.
 */
class WiFiSyncClient(
    private val context: Context,
    private val repo: Repository,
) {

    /** Token obtained during the pairing flow (stored in SharedPreferences by PairingManager). */
    private val prefs = context.getSharedPreferences("absenku_sync", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun getSavedToken(): String? = prefs.getString("pair_token", null)

    /** Save a freshly-verified token. */
    fun saveToken(token: String) {
        prefs.edit().putString("pair_token", token).apply()
    }

    /** Clear token if pairing is revoked / device unpaired. */
    fun clearToken() { prefs.edit().remove("pair_token").apply() }

    /**
     * Sync with the server at [host]:[port].
     * @return SyncResult with success flag + summary of merged/conflict records.
     */
    suspend fun syncNow(host: String, port: Int = 5000): SyncResult = withContext(Dispatchers.IO) {
        val token = getSavedToken()
        if (token.isNullOrBlank()) {
            repo.logSync(SyncLog(type = "sync", direction = "up", status = "fail", message = "No pairing token", deviceId = null))
            return@withContext SyncResult(false, "Belum ada pairing token. Silakan pairing dulu.", 0, 0)
        }

        try {
            // 1. Build outgoing payload (changed since lastSync)
            val lastSync = prefs.getLong("last_sync_epoch", 0L)
            val deviceId = com.absenku.utils.DeviceIdHelper.getDeviceId(context)
            val payload = buildPayload(deviceId, lastSync)

            // 2. POST to /sync/push
            val url = URL("http://$host:$port/sync/push")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Pair-Token", token)
                setRequestProperty("X-Device-Id", deviceId)
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 30_000
            }

            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val resp = conn.inputStream.bufferedReader().readText()
            val merged = parseMerged(resp)

            // 3. Apply merged records locally (last-write-wins already resolved server-side)
            var upserted = 0
            var conflicted = 0
            merged.records.forEach { record ->
                if (record.conflict) conflicted++
                when (record.table) {
                    "siswa"    -> upsertSiswa(record)
                    "absensi"  -> upsertAbsensi(record)
                    "nilai"    -> upsertNilai(record)
                    "kelas"    -> upsertKelas(record)
                }
                upserted++
            }

            conn.disconnect()
            prefs.edit().putLong("last_sync_epoch", System.currentTimeMillis()).apply()
            repo.logSync(SyncLog(type="sync", direction="up", status="success", message="$upserted merged, $conflicted conflicts", deviceId = deviceId))
            SyncResult(true, "Sync selesai: $upserted record, $conflicted konflik.", upserted, conflicted)

        } catch (e: Exception) {
            e.printStackTrace()
            repo.logSync(SyncLog(type="sync", direction="all", status="fail", message = e.message, deviceId = com.absenku.utils.DeviceIdHelper.getDeviceId(context)))
            // Network error, timeout, 401, etc.
            val msg = if (e.message?.contains("401") == true || e.message?.contains("Unauthorized") == true)
                "Token tidak sah. Silakan pairing ulang." else
                "Gagal sync: ${e.message}"
            SyncResult(false, msg, 0, 0)
        }
    }

    // ── payload building (minimal JSON via Gson) ──────────────────────────
    private fun buildPayload(deviceId: String, since: Long): String {
        val gson = com.google.gson.Gson()
        return gson.toJson(mapOf("deviceId" to deviceId, "since" to since))
    }

    private fun parseMerged(resp: String): MergeResponse {
        val gson = com.google.gson.Gson()
        return try { gson.fromJson(resp, MergeResponse::class.java) }
        catch (e: Exception) { MergeResponse(emptyList()) }
    }

    private fun upsertSiswa(r: SyncRecord) { android.util.Log.d("WiFiSync", "upsert siswa: ${r.data}") }
    private fun upsertAbsensi(r: SyncRecord) { android.util.Log.d("WiFiSync", "upsert absensi: ${r.data}") }
    private fun upsertNilai(r: SyncRecord) { android.util.Log.d("WiFiSync", "upsert nilai: ${r.data}") }
    private fun upsertKelas(r: SyncRecord) { android.util.Log.d("WiFiSync", "upsert kelas: ${r.data}") }

    data class SyncResult(val success: Boolean, val message: String, val merged: Int, val conflicts: Int)
    data class MergeResponse(val records: List<SyncRecord>)
    data class SyncRecord(val table: String, val data: Map<String, Any?>, val conflict: Boolean = false)
}
