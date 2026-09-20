package com.signalbridge.hfp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID

/**
 * EXPERIMENTAL. Attempts to make THIS phone behave as a Bluetooth Hands-Free unit (a
 * "headset") connected to a nearby phone's Audio Gateway (AG). If it works, the AG's
 * cellular call audio would route to this phone over Bluetooth — the data-free bridge.
 *
 * What this class does today, honestly:
 *   1. Opens an insecure RFCOMM socket to the remote phone's HFP-AG service record.
 *   2. Runs the full Service Level Connection AT-command handshake ([HfpAtCommands.Slc]).
 *   3. Reports whether the SLC reached DONE.
 *
 * What it does NOT do yet (the real blocker):
 *   - Establish the SCO/eSCO audio link and read/write the call PCM. Android does not
 *     expose HF-side SCO audio to apps, so this remains open. See CellularBridge.kt.
 *
 * Also note: a stock Android phone runs its own HFP-AG for headsets and may refuse an
 * incoming HF connection from another phone, or require prior pairing. Treat success as
 * device-dependent — which is exactly why this is behind a feature flag.
 */
class HfpHandsFreeUnit {

    /** Standard 16-bit Bluetooth base + HFP Audio Gateway service class (0x111F). */
    private val hfpAgUuid: UUID = UUID.fromString("0000111F-0000-1000-8000-00805F9B34FB")

    private var socket: BluetoothSocket? = null

    /**
     * Blocking. Call off the main thread. Returns true if the SLC handshake completed.
     * The [log] callback surfaces each AT line exchanged so testers can see what the
     * particular AG did.
     */
    @SuppressLint("MissingPermission") // caller ensures BLUETOOTH_CONNECT is granted
    fun connectAndHandshake(device: BluetoothDevice, log: (String) -> Unit = {}): Boolean {
        return try {
            val s = device.createInsecureRfcommSocketToServiceRecord(hfpAgUuid)
            socket = s
            s.connect()
            val reader = BufferedReader(InputStreamReader(s.inputStream))
            val out: OutputStream = s.outputStream

            val slc = HfpAtCommands.Slc()
            fun send(cmd: String) {
                log(">> $cmd")
                out.write("$cmd\r".toByteArray())
                out.flush()
            }

            send(slc.start().send!!)
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                log("<< $line")
                val action = slc.onResponse(line)
                when (action.step) {
                    HfpAtCommands.Step.DONE -> { log("SLC established"); return true }
                    HfpAtCommands.Step.FAILED -> { log("SLC failed"); return false }
                    else -> action.send?.let { send(it) }
                }
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "HFP handshake failed", e)
            log("error: ${e.message}")
            false
        }
    }

    fun close() { runCatching { socket?.close() }; socket = null }

    private companion object { const val TAG = "HfpHandsFreeUnit" }
}
