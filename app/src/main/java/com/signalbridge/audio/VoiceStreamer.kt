package com.signalbridge.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import com.signalbridge.Protocol
import java.io.InputStream
import java.io.OutputStream
import kotlin.concurrent.thread

/**
 * Turns a byte pipe into a two-way voice call.
 *   - Capture thread: microphone -> PCM -> [out] (to the other phone)
 *   - Playback thread: [in] (from the other phone) -> speaker
 *
 * This is a full-duplex local intercom. It works with zero mobile data because the
 * transport ([in]/[out]) is a Bluetooth RFCOMM socket, not a network connection.
 */
class VoiceStreamer(
    private val input: InputStream,
    private val output: OutputStream,
) {
    @Volatile private var running = false
    private var capture: Thread? = null
    private var playback: Thread? = null

    @SuppressLint("MissingPermission") // caller ensures RECORD_AUDIO is granted
    fun start() {
        running = true

        val minRec = AudioRecord.getMinBufferSize(
            Protocol.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(Protocol.FRAME_BYTES)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // echo-cancelled path
            Protocol.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minRec
        )

        val minPlay = AudioTrack.getMinBufferSize(
            Protocol.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(Protocol.FRAME_BYTES)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(Protocol.SAMPLE_RATE_HZ)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minPlay)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        capture = thread(name = "sb-capture") {
            val buf = ByteArray(Protocol.FRAME_BYTES)
            try {
                recorder.startRecording()
                while (running) {
                    val n = recorder.read(buf, 0, buf.size)
                    if (n > 0) output.write(buf, 0, n)
                }
            } catch (e: Exception) {
                Log.w(TAG, "capture ended: ${e.message}")
            } finally {
                runCatching { recorder.stop(); recorder.release() }
            }
        }

        playback = thread(name = "sb-playback") {
            val buf = ByteArray(Protocol.FRAME_BYTES)
            try {
                track.play()
                while (running) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n > 0) track.write(buf, 0, n)
                }
            } catch (e: Exception) {
                Log.w(TAG, "playback ended: ${e.message}")
            } finally {
                runCatching { track.stop(); track.release() }
            }
        }
    }

    fun stop() {
        running = false
        capture?.interrupt(); playback?.interrupt()
        capture = null; playback = null
    }

    private companion object { const val TAG = "VoiceStreamer" }
}
