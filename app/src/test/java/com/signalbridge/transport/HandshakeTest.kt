package com.signalbridge.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandshakeTest {

    @Test fun `round-trips name and upi`() {
        val line = Handshake.encodeApproval("Pixel 8", "arun@okaxis")
        val a = Handshake.parseApproval(line)!!
        assertEquals("Pixel 8", a.name)
        assertEquals("arun@okaxis", a.upi)
    }

    @Test fun `empty upi parses as null`() {
        val a = Handshake.parseApproval(Handshake.encodeApproval("Helper", null))!!
        assertEquals("Helper", a.name)
        assertNull(a.upi)
    }

    @Test fun `strips separators so they cannot break the wire format`() {
        val a = Handshake.parseApproval(Handshake.encodeApproval("a|b\nc", "x@y"))!!
        assertFalse(a.name.contains("|"))
        assertFalse(a.name.contains("\n"))
    }

    @Test fun `rejects non-SB1 lines`() {
        assertNull(Handshake.parseApproval("HELLO|x|y"))
        assertNull(Handshake.parseApproval(null))
        assertNull(Handshake.parseApproval(""))
    }

    @Test fun `upi validation accepts sane ids and rejects junk`() {
        assertTrue(Handshake.looksLikeUpi("arun@okaxis"))
        assertTrue(Handshake.looksLikeUpi("a.b-1_2@ybl"))
        assertFalse(Handshake.looksLikeUpi("noatsign"))
        assertFalse(Handshake.looksLikeUpi("@bank"))
        assertFalse(Handshake.looksLikeUpi("a@"))
        assertFalse(Handshake.looksLikeUpi(null))
    }
}
