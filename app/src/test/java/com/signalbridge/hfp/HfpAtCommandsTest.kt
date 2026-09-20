package com.signalbridge.hfp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the HFP Service Level Connection handshake logic end-to-end by replaying a
 * realistic Audio Gateway conversation — no Bluetooth hardware required.
 */
class HfpAtCommandsTest {

    @Test fun `handshake walks BRSF - CIND test - CIND read - CMER then DONE`() {
        val slc = HfpAtCommands.Slc()

        // HF opens with AT+BRSF
        assertEquals("AT+BRSF=0", slc.start().send)

        // AG answers with its features, then OK -> HF should ask CIND=?
        assertNull(slc.onResponse("+BRSF: 871").send)          // intermediate line
        assertEquals("AT+CIND=?", slc.onResponse("OK").send)

        // AG returns indicator layout, then OK -> HF should read CIND?
        assertNull(slc.onResponse("+CIND: (\"service\",(0,1)),(\"call\",(0,1))").send)
        assertEquals("AT+CIND?", slc.onResponse("OK").send)

        // AG returns current values, then OK -> HF enables event reporting
        assertNull(slc.onResponse("+CIND: 1,0").send)
        assertEquals("AT+CMER=3,0,0,1", slc.onResponse("OK").send)

        // Final OK completes the SLC
        val done = slc.onResponse("OK")
        assertEquals(HfpAtCommands.Step.DONE, done.step)
        assertNull(done.send)
    }

    @Test fun `ERROR from AG fails the handshake`() {
        val slc = HfpAtCommands.Slc()
        slc.start()
        val action = slc.onResponse("ERROR")
        assertEquals(HfpAtCommands.Step.FAILED, action.step)
    }

    @Test fun `parses CIND test indicator names`() {
        val names = HfpAtCommands.parseCindTest(
            "+CIND: (\"service\",(0,1)),(\"call\",(0,1)),(\"callsetup\",(0,3))"
        )
        assertEquals(listOf("service", "call", "callsetup"), names)
    }

    @Test fun `parses CIND status values`() {
        assertEquals(listOf(1, 0, 2), HfpAtCommands.parseCindStatus("+CIND: 1,0,2"))
    }

    @Test fun `parses AG BRSF bitmap`() {
        assertEquals(871, HfpAtCommands.parseBrsf("+BRSF: 871"))
        assertNull(HfpAtCommands.parseBrsf("+BRSF: nope"))
    }

    @Test fun `blank lines do not advance the handshake`() {
        val slc = HfpAtCommands.Slc()
        slc.start()
        assertNull(slc.onResponse("").send)
        assertTrue(slc.step == HfpAtCommands.Step.BRSF)
    }
}
