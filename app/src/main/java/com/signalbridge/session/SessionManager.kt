package com.signalbridge.session

import android.content.Context
import android.os.Build
import com.signalbridge.audio.VoiceStreamer
import com.signalbridge.ble.BleAdvertiser
import com.signalbridge.ble.BleScanner
import com.signalbridge.bt.BluetoothVoiceLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Which side of the exchange the user is playing right now. */
enum class Role { IDLE, HELPER, SEEKER }

/** High-level state the UI renders. */
enum class LinkState { IDLE, ADVERTISING, SCANNING, CONNECTING, CONSENT_PENDING, WAITING_APPROVAL, LIVE, ENDED }

/**
 * The brain. Ties discovery (BLE) -> transport (L2CAP) -> voice (audio) together and
 * exposes a single observable state to the UI. A plain singleton so the Activity and the
 * foreground service share one source of truth.
 */
class SessionManager(private val appContext: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _role = MutableStateFlow(Role.IDLE)
    val role: StateFlow<Role> = _role.asStateFlow()

    private val _state = MutableStateFlow(LinkState.IDLE)
    val state: StateFlow<LinkState> = _state.asStateFlow()

    private val _helpers = MutableStateFlow<List<BleScanner.NearbyHelper>>(emptyList())
    val helpers: StateFlow<List<BleScanner.NearbyHelper>> = _helpers.asStateFlow()

    private val _seconds = MutableStateFlow(0)
    val seconds: StateFlow<Int> = _seconds.asStateFlow()

    private val advertiser by lazy { BleAdvertiser(appContext) }
    private val scanner by lazy { BleScanner(appContext) }
    private var link: BluetoothVoiceLink? = null
    private var streamer: VoiceStreamer? = null

    private val myName: String get() = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    // ---------------- Helper flow ----------------

    /** Helper taps "I can help": open an L2CAP server, advertise its PSM, wait for a Seeker. */
    fun becomeHelper() {
        _role.value = Role.HELPER
        val l = BluetoothVoiceLink(appContext).also { link = it }
        val psm = l.hostAndListen()
        if (psm == null) { _state.value = LinkState.ENDED; return }
        advertiser.start(psm, myName)
        _state.value = LinkState.ADVERTISING
        scope.launch {
            if (l.accept()) {
                advertiser.stop()                 // stop broadcasting once someone connects
                _state.value = LinkState.CONSENT_PENDING   // wait for the user to approve
            } else {
                _state.value = LinkState.ENDED
            }
        }
    }

    /** Helper approves: tell the Seeker to start, then both go live. */
    fun helperApprove() {
        scope.launch {
            if (link?.sendGo() == true) goLive() else _state.value = LinkState.ENDED
        }
    }

    /** Helper declines: close the link (Seeker sees the disconnect and ends). */
    fun helperDecline() = end()

    // ---------------- Seeker flow ----------------

    /** Seeker taps "I need to call": scan for nearby helpers. */
    fun becomeSeeker() {
        _role.value = Role.SEEKER
        _helpers.value = emptyList()
        _state.value = LinkState.SCANNING
        val found = LinkedHashMap<String, BleScanner.NearbyHelper>()
        scanner.start { h ->
            found[h.device.address] = h                       // refresh RSSI per device
            _helpers.value = found.values.sortedByDescending { it.rssi }
        }
    }

    /** Seeker picks a helper and connects, then waits for the helper to approve. */
    fun connectToHelper(helper: BleScanner.NearbyHelper) {
        scanner.stop()
        _state.value = LinkState.CONNECTING
        val l = BluetoothVoiceLink(appContext).also { link = it }
        scope.launch {
            if (!l.connectTo(helper.device, helper.psm)) { _state.value = LinkState.ENDED; return@launch }
            _state.value = LinkState.WAITING_APPROVAL
            if (l.awaitGo()) goLive() else _state.value = LinkState.ENDED
        }
    }

    // ---------------- Shared ----------------

    private fun goLive() {
        val l = link ?: run { _state.value = LinkState.ENDED; return }
        val i = l.input; val o = l.output
        if (i == null || o == null) { _state.value = LinkState.ENDED; return }
        streamer = VoiceStreamer(i, o).also { it.start() }
        _state.value = LinkState.LIVE
        scope.launch {
            while (_state.value == LinkState.LIVE) {
                delay(1000)
                _seconds.value += 1
            }
        }
    }

    fun end() {
        advertiser.stop(); scanner.stop()
        streamer?.stop(); streamer = null
        link?.close(); link = null
        _state.value = LinkState.ENDED
    }

    fun reset() {
        _role.value = Role.IDLE
        _state.value = LinkState.IDLE
        _helpers.value = emptyList()
        _seconds.value = 0
    }

    companion object {
        @Volatile private var instance: SessionManager? = null
        fun get(context: Context): SessionManager =
            instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also { instance = it }
            }
    }
}
