package com.signalbridge.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.signalbridge.transport.VoiceChannel
import java.io.IOException

/**
 * Short-range transport: a Bluetooth LE L2CAP connection-oriented channel. Everything
 * stays on LE, so the device the Seeker scanned is exactly the one it connects to — no
 * classic-Bluetooth pairing. Produces a transport-neutral [VoiceChannel] on success.
 */
class BluetoothVoiceLink(private val context: Context) {

    private var serverSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null

    private val adapter get() =
        context.getSystemService(BluetoothManager::class.java).adapter

    /** Helper: open an L2CAP server. Returns the PSM to advertise, or null on failure. */
    @SuppressLint("MissingPermission")
    fun hostAndListen(): Int? = try {
        val server = adapter?.listenUsingInsecureL2capChannel()
        serverSocket = server
        server?.psm
    } catch (e: IOException) { Log.e(TAG, "listen failed", e); null }

    /** Helper: block until a Seeker connects, then hand back a channel. Off the main thread. */
    fun accept(): VoiceChannel? = try {
        val s = serverSocket?.accept()
        runCatching { serverSocket?.close() }
        socket = s
        s?.let { VoiceChannel(it.inputStream, it.outputStream, it) }
    } catch (e: IOException) { Log.e(TAG, "accept failed", e); null }

    /** Seeker: connect to a chosen Helper's PSM. Blocks — off the main thread. */
    @SuppressLint("MissingPermission")
    fun connectTo(device: BluetoothDevice, psm: Int): VoiceChannel? = try {
        val s = device.createInsecureL2capChannel(psm)
        socket = s
        s.connect()
        VoiceChannel(s.inputStream, s.outputStream, s)
    } catch (e: IOException) { Log.e(TAG, "connect failed", e); null }

    fun close() {
        runCatching { socket?.close() }
        runCatching { serverSocket?.close() }
    }

    private companion object { const val TAG = "BluetoothVoiceLink" }
}
