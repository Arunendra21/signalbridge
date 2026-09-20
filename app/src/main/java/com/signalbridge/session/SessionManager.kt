package com.signalbridge.session

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.signalbridge.audio.VoiceStreamer
import com.signalbridge.ble.BleAdvertiser
import com.signalbridge.ble.BleScanner
import com.signalbridge.bt.BluetoothVoiceLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Which side of the exchange the user is playing right now. */
enum class Role { IDLE, HELPER, SEEKER }

/** High-level state the UI renders. */
enum class LinkState { IDLE, ADVERTISING, SCANNING, CONNECTING, CONSENT_PENDING, LIVE, ENDED }

/**
 * The brain. Ties discovery (BLE) -> pairing (RFCOMM) -> voice (audio) together and
 * exposes a single observable state to the UI. Deliberately a plain object so both the
 * Activity and the foreground service share one source of truth.
 */
class SessionManager(private val appContext: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _role = MutableStateFlow(Role.IDLE)
    val role: StateFlow<Role> = _role.asStateFlow()

    private val _state = MutableStateFlow(LinkState.IDLE)
    val state: StateFlow<LinkState> = _state.asStateFlow()

    private val _helpers = MutableStateFlow<List<BleScanner.NearbyHelper>>(emptyList())
    val helpers: StateFlow<List<BleScanner.NearbyHelper>> = _helpers.asStateFlow()

    /** Seconds the current live link has been up — the meter that drives settlement. */
    private val _seconds = MutableStateFlow(0)
    val seconds: StateFlow<Int> = _seconds.asStateFlow()

    private val advertiser by lazy { BleAdvertiser(appContext) }
    private val scanner by lazy { BleScanner(appContext) }
    private val link by lazy { BluetoothVoiceLink(appContext) }
    private var streamer: VoiceStreamer? = null

    // ---------------- Helper flow ----------------

    /** Helper taps "I can help": start beaconing and wait for a Seeker to connect. */
    fun becomeHelper() {
        _role.value = Role.HELPER
        _state.value = LinkState.ADVERTISING
        advertiser.start()
        scope.launch {
            if (link.hostAndAccept()) {
                // A Seeker connected. Helper must explicitly approve before audio flows.
                _state.value = LinkState.CONSENT_PENDING
            } else {
                _state.value = LinkState.ENDED
            }
        }
    }

    /** Helper approves the request — only now does voice actually start. */
    fun helperApprove() = goLive()

    // ---------------- Seeker flow ----------------

    /** Seeker taps "I need to call": scan for nearby helpers. */
    fun becomeSeeker() {
        _role.value = Role.SEEKER
        _state.value = LinkState.SCANNING
        val found = mutableMapOf<String, BleScanner.NearbyHelper>()
        scanner.start { h ->
            found[h.device.address] = h
            _helpers.value = found.values.sortedByDescending { it.rssi } // nearest first
        }
    }

    /** Seeker picks a helper from the list and connects. */
    fun connectToHelper(helper: BleScanner.NearbyHelper) {
        scanner.stop()
        _state.value = LinkState.CONNECTING
        scope.launch {
            if (link.connectTo(helper.device)) {
                goLive()
            } else {
                _state.value = LinkState.ENDED
            }
        }
    }

    // ---------------- Shared ----------------

    private fun goLive() {
        val i = link.input; val o = link.output
        if (i == null || o == null) { _state.value = LinkState.ENDED; return }
        streamer = VoiceStreamer(i, o).also { it.start() }
        _state.value = LinkState.LIVE
        scope.launch {
            while (_state.value == LinkState.LIVE) {
                kotlinx.coroutines.delay(1000)
                _seconds.value += 1
            }
        }
    }

    fun end() {
        advertiser.stop(); scanner.stop()
        streamer?.stop(); streamer = null
        link.close()
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
