package com.signalbridge.hfp

/**
 * Pure logic for the Hands-Free Profile (HFP) Service Level Connection (SLC) handshake,
 * HF side. This is the "control channel" a Bluetooth headset speaks to a phone (the Audio
 * Gateway / AG) before any call audio flows. It is plain AT-command text over RFCOMM.
 *
 * Why this matters to SignalBridge: to route a Seeker's voice out over the Helper's real
 * cellular call with NO mobile data, the Seeker's phone must act like a headset (HF) to the
 * Helper's phone (AG). Step one of being a headset is completing this handshake. This file
 * implements and unit-tests that handshake as a pure state machine — no Android, no radio —
 * so the logic is verifiable in CI. The remaining blocker (SCO audio access) lives in the
 * radio-facing class, not here. See HfpHandsFreeUnit.kt and bridge/CellularBridge.kt.
 *
 * Reference: Bluetooth HFP 1.7, section 4.2 "Service Level Connection Initialization".
 */
object HfpAtCommands {

    /** HF feature bitmap we advertise. Kept minimal & honest (no codec/3-way yet). */
    const val HF_FEATURES = 0

    /** Steps of the SLC handshake, in order. */
    enum class Step { BRSF, TEST_CIND, READ_CIND, ENABLE_CMER, DONE, FAILED }

    /** What the state machine wants next: a line to send, or a terminal state. */
    data class Action(val step: Step, val send: String?)

    /**
     * A tiny state machine. Feed it each line the AG sends back; it returns the next
     * command to transmit (or DONE/FAILED). Start by calling [start].
     */
    class Slc {
        var step: Step = Step.BRSF
            private set

        /** First command to send once the RFCOMM socket is open. */
        fun start(): Action {
            step = Step.BRSF
            return Action(step, "AT+BRSF=$HF_FEATURES")
        }

        /**
         * Advance given one response line from the AG. The AG sends solicited results
         * (e.g. "+BRSF: 871") followed by a final "OK" or "ERROR". We advance only on
         * the final result; intermediate "+XXX:" lines are informational.
         */
        fun onResponse(line: String): Action {
            val l = line.trim()
            if (l.isEmpty() || l.startsWith("+")) {
                // Intermediate/unsolicited line — stay on the same step, send nothing.
                return Action(step, null)
            }
            if (!l.equals("OK", ignoreCase = true)) {
                step = Step.FAILED
                return Action(step, null)
            }
            // Got the terminating OK for the current step — move to the next.
            step = when (step) {
                Step.BRSF -> Step.TEST_CIND
                Step.TEST_CIND -> Step.READ_CIND
                Step.READ_CIND -> Step.ENABLE_CMER
                Step.ENABLE_CMER -> Step.DONE
                Step.DONE, Step.FAILED -> step
            }
            val next = when (step) {
                Step.TEST_CIND -> "AT+CIND=?"
                Step.READ_CIND -> "AT+CIND?"
                Step.ENABLE_CMER -> "AT+CMER=3,0,0,1"
                else -> null
            }
            return Action(step, next)
        }
    }

    /** Parse a "+CIND: (\"service\",(0,1)),(\"call\",(0,1))..." test response into indicator names. */
    fun parseCindTest(line: String): List<String> {
        val body = line.substringAfter("+CIND:", "").trim()
        return Regex("\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
    }

    /** Parse a "+CIND: 1,0,1,..." status response into integer values. */
    fun parseCindStatus(line: String): List<Int> {
        val body = line.substringAfter("+CIND:", "").trim()
        if (body.isEmpty()) return emptyList()
        return body.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    /** Parse the AG feature bitmap from "+BRSF: 871". */
    fun parseBrsf(line: String): Int? =
        line.substringAfter("+BRSF:", "").trim().toIntOrNull()
}
