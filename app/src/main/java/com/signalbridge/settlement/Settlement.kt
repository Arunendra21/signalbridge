package com.signalbridge.settlement

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.signalbridge.transport.Handshake

/**
 * The Seeker compensates the Helper for the airtime they lent. Payment is out-of-band: a
 * UPI deep link the user confirms in their own bank/UPI app — SignalBridge never handles
 * money and needs no data for it.
 *
 * The payee UPI id is the Helper's REAL id, delivered over the consent handshake
 * ([Handshake]) — no more hard-coded placeholder. If the Helper never set one, the UI asks
 * the Seeker to type it before paying.
 */
object Settlement {

    const val PAISE_PER_MINUTE = 100 // ₹1.00 / minute — adjust as you like

    fun amountRupees(seconds: Int): Double {
        val minutes = (seconds + 59) / 60          // round up to whole minutes
        return (minutes * PAISE_PER_MINUTE) / 100.0
    }

    fun isValidPayee(vpa: String?): Boolean = Handshake.looksLikeUpi(vpa)

    /**
     * Launches the user's UPI app pre-filled to pay the Helper's real id. Returns false if
     * the payee id is missing/invalid so the caller can prompt for it instead of guessing.
     */
    fun launchUpi(context: Context, payeeVpa: String?, payeeName: String, seconds: Int): Boolean {
        if (!isValidPayee(payeeVpa)) return false
        val amount = amountRupees(seconds)
        val uri = Uri.parse(
            "upi://pay?pa=$payeeVpa&pn=${Uri.encode(payeeName)}" +
                "&am=$amount&cu=INR&tn=${Uri.encode("SignalBridge relay")}"
        )
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(Intent.createChooser(intent, "Pay the helper"))
        return true
    }
}
