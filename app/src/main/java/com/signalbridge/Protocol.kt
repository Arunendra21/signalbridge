package com.signalbridge

import java.util.UUID

/**
 * Shared constants that both phones agree on so they can find and talk to each other
 * over local Bluetooth radio — no mobile data, no server in the middle.
 *
 * Transport model: discovery over Bluetooth LE advertising; voice over a Bluetooth LE
 * L2CAP connection-oriented channel (CoC). Everything stays on LE, so the device the
 * Seeker scans is exactly the device it connects to — no classic-Bluetooth pairing.
 */
object Protocol {

    /** BLE service UUID a Helper advertises so a Seeker can recognise "this phone can help". */
    val BLE_SERVICE_UUID: UUID = UUID.fromString("7b3e5f00-9a11-4c22-8d33-0000signalbr".fixUuid())

    /**
     * Manufacturer id used in the advertisement to carry the L2CAP PSM (2 bytes) the
     * Helper is listening on. 0xFFFF is the reserved "for testing" id — fine for a
     * local app that isn't a registered Bluetooth SIG member.
     */
    const val MANUFACTURER_ID = 0xFFFF

    /** Fallback name shown for a helper if it didn't advertise one. */
    const val DEFAULT_HELPER_NAME = "Nearby helper"

    // ---- Consent handshake bytes sent over the voice channel before audio ----
    const val CTRL_GO: Byte = 'G'.code.toByte()   // Helper approved — start talking

    // ---- Audio format (must match on both ends) ----
    const val SAMPLE_RATE_HZ = 16_000        // wideband voice, cheap on radio
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
