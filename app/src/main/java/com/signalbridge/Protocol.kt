package com.signalbridge

import java.util.UUID

/**
 * Shared constants that both phones agree on so they can find and talk to each other
 * over local Bluetooth radio — no mobile data, no server in the middle.
 */
object Protocol {

    /** BLE service UUID a Helper advertises so a Seeker can recognise "this phone can help". */
    val BLE_SERVICE_UUID: UUID = UUID.fromString("7b3e5f00-9a11-4c22-8d33-signalbridge".fixUuid())

    /** RFCOMM channel UUID used for the actual voice byte-stream once two phones pair up. */
    val VOICE_RFCOMM_UUID: UUID = UUID.fromString("7b3e5f01-9a11-4c22-8d33-signalbridge".fixUuid())

    const val RFCOMM_NAME = "SignalBridgeVoice"

    // ---- Audio format (must match on both ends) ----
    const val SAMPLE_RATE_HZ = 16_000        // wideband voice, cheap on radio
    const val CHANNELS_MONO = 1
    const val FRAME_BYTES = 640              // 20ms @ 16kHz, 16-bit mono

    /** UUIDs must be 32 hex chars; helper turns our readable suffix into valid hex. */
    private fun String.fixUuid(): String {
        val hex = this.replace("-", "").map { c ->
            if (c.isDigit() || c in 'a'..'f') c else ('a' + (c.code % 6))
        }.joinToString("").padEnd(32, '0').substring(0, 32)
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
            "${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }
}
