package com.signalbridge.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.signalbridge.Protocol
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * The pipe that carries voice bytes phone-to-phone over Bluetooth LE (an L2CAP
 * connection-oriented channel). This is a raw socket we control — NOT mobile data.
 *
 * Because it's LE, the device the Seeker scanned is exactly the device it connects to:
 * no classic-Bluetooth pairing, no MAC-address mismatch. Whatever we write on one phone
 * comes out of the InputStream on the other.
 *
 * Flow:
 *   Helper: [hostAndListen] -> advertise the returned PSM -> [accept] (blocks) -> approve
 *           with [sendGo] once the user consents.
 *   Seeker: [connectTo] the chosen PSM -> [awaitGo] (blocks until the Helper approves or
 *           hangs up).
 */
class BluetoothVoiceLink(private val context: Context) {

    private var serverSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null

    var input: InputStream? = null
        private set
    var output: OutputStream? = null
        private set

    private val adapter get() =
        context.getSystemService(BluetoothManager::class.java).adapter

    /** Helper: open an L2CAP server. Returns the PSM to advertise, or null on failure. */
    @SuppressLint("MissingPermission")
    fun hostAndListen(): Int? = try {
        val server = adapter?.listenUsingInsecureL2capChannel()
        serverSocket = server
        server?.psm
    } catch (e: IOException) {
        Log.e(TAG, "listen failed", e); null
    }

    /** Helper: block until a Seeker connects. Call off the main thread. */
    fun accept(): Boolean = try {
        socket = serverSocket?.accept()
        runCatching { serverSocket?.close() }   // one connection is enough
        bindStreams()
    } catch (e: IOException) {
        Log.e(TAG, "accept failed", e); false
    }

    /** Seeker: connect to a chosen Helper's PSM. Blocks — call off the main thread. */
    @SuppressLint("MissingPermission")
    fun connectTo(device: BluetoothDevice, psm: Int): Boolean = try {
        val s = device.createInsecureL2capChannel(psm)
        socket = s
        s.connect()
        bindStreams()
    } catch (e: IOException) {
        Log.e(TAG, "connect failed", e); false
    }

    /** Helper: tell the Seeker "approved, start talking". */
    fun sendGo(): Boolean = try {
        output?.write(byteArrayOf(Protocol.CTRL_GO)); output?.flush(); true
    } catch (e: IOException) { Log.e(TAG, "sendGo failed", e); false }

    /**
     * Seeker: block until the Helper approves (sends CTRL_GO) or hangs up (stream closes).
     * @return true if approved, false if the Helper declined/disconnected.
     */
    fun awaitGo(): Boolean = try {
        val b = input?.read() ?: -1
        b == Protocol.CTRL_GO.toInt()
    } catch (e: IOException) { false }

    private fun bindStreams(): Boolean {
        val s = socket ?: return false
        input = s.inputStream
        output = s.outputStream
        return input != null && output != null
    }

    fun close() {
        runCatching { socket?.close() }
        runCatching { serverSocket?.close() }
        input = null; output = null
    }

    private companion object { const val TAG = "BluetoothVoiceLink" }
}
