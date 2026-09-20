package com.signalbridge.transport

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

/**
 * A transport-neutral duplex byte pipe between the two phones. Both the Bluetooth (LE
 * L2CAP) and the Wi-Fi Direct (TCP) transports produce one of these, so everything above
 * — the consent handshake and the voice streamer — is written once and works over either.
 *
 * Note: [readLine] reads one byte at a time up to '\n' on purpose. A BufferedReader would
 * greedily pull bytes past the handshake line and swallow the start of the audio stream;
 * byte-at-a-time keeps the raw [input] intact for [com.signalbridge.audio.VoiceStreamer].
 */
class VoiceChannel(
    val input: InputStream,
    val output: OutputStream,
    private val closer: Closeable,
) {
    /** Send a single control line (must include its own trailing '\n'). */
    @Synchronized
    fun writeLine(line: String): Boolean = try {
        output.write(line.toByteArray(Charsets.UTF_8)); output.flush(); true
    } catch (e: Exception) { false }

    /** Read one control line (without the newline), or null if the peer hung up. */
    fun readLine(): String? {
        val sb = StringBuilder()
        while (true) {
            val b = try { input.read() } catch (e: Exception) { return null }
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString()
            if (b != '\r'.code) sb.append(b.toChar())
        }
    }

    fun close() { runCatching { closer.close() } }
}
