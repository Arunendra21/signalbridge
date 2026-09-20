package com.signalbridge.transport

/**
 * The tiny text protocol the Helper sends to the Seeker at the moment of approval. It
 * carries the Helper's display name and — crucially — the Helper's real UPI ID, so the
 * Seeker pays the correct person instead of a hard-coded placeholder. Pure logic, unit
 * tested; no Android here.
 *
 * Wire format (one line, '|'-separated, newline-terminated):
 *   SB1|<name>|<upi>\n
 * where <upi> may be empty if the Helper chose not to set one.
 */
object Handshake {

    private const val PREFIX = "SB1"

    data class Approval(val name: String, val upi: String?)

    /** Helper -> Seeker approval line. Fields are sanitised so '|' / newlines can't break parsing. */
    fun encodeApproval(name: String, upi: String?): String {
        val n = name.clean().ifEmpty { "Helper" }
        val u = (upi ?: "").clean()
        return "$PREFIX|$n|$u\n"
    }

    /** Parse an approval line. Returns null if it isn't a valid SB1 approval. */
    fun parseApproval(line: String?): Approval? {
        if (line == null) return null
        val parts = line.trim().split("|")
        if (parts.size < 3 || parts[0] != PREFIX) return null
        val name = parts[1].ifBlank { "Helper" }
        val upi = parts[2].takeIf { it.isNotBlank() && it.contains("@") }
        return Approval(name, upi)
    }

    /** Basic UPI VPA sanity check (name@handle). Not exhaustive — just guards obvious junk. */
    fun looksLikeUpi(vpa: String?): Boolean {
        if (vpa.isNullOrBlank()) return false
        val m = Regex("^[a-zA-Z0-9._-]{2,}@[a-zA-Z]{2,}$")
        return m.matches(vpa.trim())
    }

    private fun String.clean() = replace("|", "/").replace("\n", " ").replace("\r", " ").trim()
}
