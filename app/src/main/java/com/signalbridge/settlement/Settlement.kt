package com.signalbridge.settlement

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * The Seeker compensates the Helper for the airtime they lent. Payment is completely
 * out-of-band (a UPI deep link the user confirms in their own bank/UPI app) — it never
 * touches the voice link and needs no data during the call itself.
 *
 * Rate is deliberately tiny and transparent. Tune to taste.
 */
object Settlement {

    const val PAISE_PER_MINUTE = 100 // ₹1.00 / minute — adjust as you like

    fun amountRupees(seconds: Int): Double {
        val minutes = (seconds + 59) / 60          // round up to whole minutes
        return (minutes * PAISE_PER_MINUTE) / 100.0
    }

    /**
     * Launches the user's UPI app pre-filled to pay the Helper. The user still taps
     * "Pay" themselves inside their bank app — SignalBridge never handles money directly.
     */
    fun launchUpi(context: Context, payeeVpa: String, payeeName: String, seconds: Int) {
        val amount = amountRupees(seconds)
        val uri = Uri.parse(
            "upi://pay?pa=$payeeVpa&pn=${Uri.encode(payeeName)}" +
                "&am=$amount&cu=INR&tn=${Uri.encode("SignalBridge relay")}"
        )
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(Intent.createChooser(intent, "Pay the helper"))
    }
}
