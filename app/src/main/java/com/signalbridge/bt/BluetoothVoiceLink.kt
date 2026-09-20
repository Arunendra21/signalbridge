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
 * The pipe that carries voice bytes phone-to-phone over classic Bluetooth (RFCOMM).
 * This is a raw socket we control — NOT mobile data, NOT the internet. Whatever we
 * write on one phone comes out of the InputStream on the other.
 *
 * Helper hosts the socket (listen); Seeker connects to it. Either way you end up with
 * an [in]/[out] stream pair that [com.signalbridge.audio.VoiceStreamer] pumps audio over.
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

    /** Helper: wait for the Seeker to connect. Blocks until connected — call off the main thread. */
    @SuppressLint("MissingPermission")
    fun hostAndAccept(): Boolean = try {
        serverSocket = adapter?.listenUsingInsecureRfcommWithServiceRecord(
            Protocol.RFCOMM_NAME, Protocol.VOICE_RFCOMM_UUID
        )
        socket = serverSocket?.accept()          // blocking
        serverSocket?.close()                    // only need one connection
        bindStreams()
    } catch (e: IOException) {
        Log.e(TAG, "host failed", e); false
    }

    /** Seeker: connect to a chosen Helper device. Blocks — call off the main thread. */
    @SuppressLint("MissingPermission")
    fun connectTo(device: BluetoothDevice): Boolean = try {
        adapter?.cancelDiscovery()
        socket = device.createInsecureRfcommSocketToServiceRecord(Protocol.VOICE_RFCOMM_UUID)
        socket?.connect()                        // blocking
        bindStreams()
    } catch (e: IOException) {
        Log.e(TAG, "connect failed", e); false
    }

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
