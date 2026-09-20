package com.signalbridge.bridge

/**
 * ============================ EXPERIMENTAL — READ THIS ============================
 *
 * This is the ambitious layer: taking the voice that arrives over Bluetooth from the
 * Seeker and pushing it OUT over the Helper's real cellular call, so the Seeker can
 * reach any phone number using the Helper's tower — with ZERO mobile data (the call
 * is a circuit-switched / VoLTE *voice* call, billed as the Helper's minutes).
 *
 * WHY IT IS NOT "DONE":
 * Stock Android does not let a third-party app replace the microphone stream that
 * feeds a live cellular (CS/VoLTE) call. The in-call audio path is owned by the modem
 * and the platform. So you cannot simply "inject" the Bluetooth audio into the call.
 *
 * THE REAL PATH (documented so a contributor can push it forward):
 *
 *  Option A — Bluetooth Hands-Free (HFP) emulation:
 *    Make the Seeker's phone present itself to the Helper's phone as a Bluetooth
 *    hands-free headset (HFP "HF" role). When the Helper's phone places a normal
 *    voice call, the OS routes the call's mic+speaker audio to the "headset" — i.e.
 *    the Seeker's phone. This is legitimate Bluetooth profile behaviour and needs no
 *    data. The hard part: Android exposes the *Audio Gateway* (AG) role, not the HF
 *    role, to apps — so the HF side must be implemented by hand over RFCOMM + SCO,
 *    which is device-dependent and not guaranteed on every phone.
 *
 *  Option B — Accessibility/Telecom self-managed call (app-to-app only):
 *    Use TelecomManager + a self-managed ConnectionService so SignalBridge owns the
 *    call object. This works cleanly for SignalBridge-to-SignalBridge calls but does
 *    NOT terminate onto the public phone network without a licensed carrier gateway.
 *
 *  Option C — 3GPP ProSe UE-to-Network Relay (sidelink / PC5):
 *    The "correct" standards answer and literally this project's vision, but it needs
 *    carrier provisioning + licensed spectrum and is not reachable from an app on
 *    consumer hardware today. Tracked as a research goal, not an implementation task.
 *
 * Until Option A is proven on real hardware, this class is intentionally inert: it
 * documents the design and exposes a feature flag, but performs no call injection.
 */
object CellularBridge {

    /** Off by default. Flipping this on does nothing yet except surface the warning UI. */
    const val ENABLED = false

    enum class Approach { HFP_EMULATION, SELF_MANAGED_TELECOM, PROSE_SIDELINK }

    data class Feasibility(val approach: Approach, val worksToday: Boolean, val note: String)

    val roadmap = listOf(
        Feasibility(Approach.HFP_EMULATION, worksToday = false,
            "IN PROGRESS: the HF-side Service Level Connection handshake is implemented and " +
            "unit-tested (see hfp/HfpAtCommands.kt + HfpHandsFreeUnit.kt). Remaining blocker: " +
            "HF-side SCO audio is not exposed to apps on stock Android."),
        Feasibility(Approach.SELF_MANAGED_TELECOM, worksToday = false,
            "Clean for app-to-app; cannot reach real phone numbers without a licensed carrier gateway."),
        Feasibility(Approach.PROSE_SIDELINK, worksToday = false,
            "Matches the original vision exactly, but carrier + spectrum only — not app-reachable."),
    )
}
