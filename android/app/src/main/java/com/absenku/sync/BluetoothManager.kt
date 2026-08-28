package com.absenku.sync

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager as SystemBluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * BluetoothManager — scan, pair & connect to the Desktop peer over
 * Classic Bluetooth (SPP).
 *
 * Used for file push/pull sync (CSV/JSON). The pairing token itself is
 * managed by PairingManager; this class only transports the bytes.
 */
@SuppressLint("MissingPermission")
class BluetoothManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as SystemBluetoothManager).adapter

    private val executor = Executors.newCachedThreadPool()

    /** SPP UUID used by virtually every serial-bluetooth service. */
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    val requiredPermissions: Array<String>
        get() = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }.toTypedArray()

    fun hasPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun enableBluetooth(activity: Activity, requestCode: Int) {
        if (bluetoothAdapter?.isEnabled == false) {
            activity.startActivityForResult(
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                requestCode
            )
        }
    }

    /** Resolve a BluetoothDevice by MAC without scanning. */
    fun getDevice(mac: String): BluetoothDevice? = try {
        bluetoothAdapter?.getRemoteDevice(mac)
    } catch (e: IllegalArgumentException) { null }

    /** Returns freshly paired devices whose name matches common printer/desktop keywords. */
    fun getPairedDevices(): List<BluetoothDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return adapter.bondedDevices
            .filter { (it.name ?: "").isNotBlank() }
            .sortedBy { it.name }
    }

    /**
     * Connect an RFCOMM socket to [device] and block up to [timeoutMs].
     * Returns null when unreachable. Caller must [disconnect] the socket.
     */
    fun connectSocket(device: BluetoothDevice, timeoutMs: Int = 8000): BluetoothSocket? {
        val socket = try { device.createRfcommSocketToServiceRecord(sppUuid) }
        catch (e: IOException) { return null }

        return try {
            val future = executor.submit { socket.connect() }
            future.get(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            socket
        } catch (e: TimeoutException) { closeQuietly(socket); null }
        catch (e: Exception) { closeQuietly(socket); null }
    }

    /** Send a JSON payload over the socket and optionally read a response. */
    fun sendPayload(socket: BluetoothSocket?, payload: String): Boolean {
        if (socket == null) return false
        return try {
            val out: OutputStream = socket.outputStream
            val data = (payload + "\n").toByteArray(Charsets.UTF_8)
            out.write(data.size.let { intToBytes(it) } + data)   // length-prefixed
            out.flush()
            true
        } catch (e: IOException) { false }
    }

    /** Blocking read of a length-prefixed UTF-8 string. */
    fun readPayload(socket: BluetoothSocket?, timeoutMs: Int = 10_000): String? {
        if (socket == null) return null
        return try {
            val inp: InputStream = socket.inputStream
            val lenBytes = ByteArray(4)
            var read = 0
            val t0 = System.currentTimeMillis()
            while (read < 4 && System.currentTimeMillis() - t0 < timeoutMs) {
                if (inp.available() > 0) read += inp.read(lenBytes, read, 4 - read)
                Thread.sleep(50)
            }
            if (read < 4) return null
            val len = bytesToInt(lenBytes)
            val data = ByteArray(len)
            inp.read(data, 0, len)
            String(data, Charsets.UTF_8)
        } catch (e: IOException) { null }
    }

    fun disconnect(socket: BluetoothSocket?) { closeQuietly(socket) }
    private fun closeQuietly(socket: BluetoothSocket?) {
        try { socket?.close() } catch (e: IOException) { /* ignore */ }
    }

    private fun intToBytes(i: Int): ByteArray = byteArrayOf(
        (i shr 24).toByte(), (i shr 16).toByte(), (i shr 8).toByte(), i.toByte()
    )

    private fun bytesToInt(b: ByteArray): Int =
        ((b[0].toInt() and 0xFF) shl 24) or
        ((b[1].toInt() and 0xFF) shl 16) or
        ((b[2].toInt() and 0xFF) shl 8) or
        (b[3].toInt() and 0xFF)
}
