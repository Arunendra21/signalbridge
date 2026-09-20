package com.signalbridge.session

import android.content.Context
import android.os.Build
import com.signalbridge.audio.VoiceStreamer
import com.signalbridge.ble.BleAdvertiser
import com.signalbridge.ble.BleScanner
import com.signalbridge.bt.BluetoothVoiceLink
import com.signalbridge.transport.Handshake
import com.signalbridge.transport.VoiceChannel
import com.signalbridge.wifi.WifiDirectVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Role { IDLE, HELPER, SEEKER }
enum class LinkState { IDLE, ADVERTISING, SCANNING, CONNECTING, CONSENT_PENDING, WAITING_APPROVAL, LIVE, ENDED }

/** Which radio carries the voice. Bluetooth = short range, proven; Wi-Fi = long range, beta. */
enum class RangeMode { BLUETOOTH, WIFI }

/**
 * The brain. Ties discovery -> transport -> voice together for both radios and exposes one
 * observable state to the UI. Also carries the identity handshake so the Seeker learns the
 * Helper's real UPI id (fixing the "pays a random id" bug).
 */
class SessionManager(private val appContext: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _mode = MutableStateFlow(RangeMode.BLUETOOTH)
    val mode: StateFlow<RangeMode> = _mode.asStateFlow()

    private val _role = MutableStateFlow(Role.IDLE)
    val role: StateFlow<Role> = _role.asStateFlow()

    private val _state = MutableStateFlow(LinkState.IDLE)
    val state: StateFlow<LinkState> = _state.asStateFlow()

    private val _helpers = MutableStateFlow<List<Helper>>(emptyList())
    val helpers: StateFlow<List<Helper>> = _helpers.asStateFlow()

    private val _seconds = MutableStateFlow(0)
    val seconds: StateFlow<Int> = _seconds.asStateFlow()

    /** The Helper's real UPI id, learned via the approval handshake. Null = pay manually. */
    private val _payeeUpi = MutableStateFlow<String?>(null)
    val payeeUpi: StateFlow<String?> = _payeeUpi.asStateFlow()

    private val advertiser by lazy { BleAdvertiser(appContext) }
    private val bleScanner by lazy { BleScanner(appContext) }
    private val wifi by lazy { WifiDirectVoice(appContext) }
    private var bt: BluetoothVoiceLink? = null

    private var channel: VoiceChannel? = null
    private var streamer: VoiceStreamer? = null

    private var myUpi: String? = null
    private val myName: String get() = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    fun setMode(m: RangeMode) { _mode.value = m }

    // ---------------- Helper ----------------

    fun becomeHelper(upi: String?) {
        myUpi = upi?.takeIf { Handshake.looksLikeUpi(it) }
        _role.value = Role.HELPER
        _state.value = LinkState.ADVERTISING
        when (_mode.value) {
            RangeMode.BLUETOOTH -> {
                val link = BluetoothVoiceLink(appContext).also { bt = it }
                val psm = link.hostAndListen()
                if (psm == null) { _state.value = LinkState.ENDED; return }
                advertiser.start(psm, myName)
                scope.launch {
                    val ch = link.accept()
                    advertiser.stop()
                    if (ch != null) onHelperConnected(ch) else _state.value = LinkState.ENDED
                }
            }
            RangeMode.WIFI -> wifi.startHelper(
                myName, myUpi,
                onConnected = { ch -> onHelperConnected(ch) },
                onError = { _state.value = LinkState.ENDED },
            )
        }
    }

    private fun onHelperConnected(ch: VoiceChannel) {
        channel = ch
        _state.value = LinkState.CONSENT_PENDING   // wait for the user to approve
    }

    fun helperApprove() {
        val ch = channel ?: run { _state.value = LinkState.ENDED; return }
        scope.launch {
            if (ch.writeLine(Handshake.encodeApproval(myName, myUpi))) goLive() else _state.value = LinkState.ENDED
        }
    }

    fun helperDecline() = end()

    // ---------------- Seeker ----------------

    fun becomeSeeker() {
        _role.value = Role.SEEKER
        _helpers.value = emptyList()
        _state.value = LinkState.SCANNING
        val found = LinkedHashMap<String, Helper>()
        fun add(h: Helper) {
            found[h.id] = h
            _helpers.value = found.values.sortedByDescending { it.signalDbm ?: Int.MIN_VALUE }
        }
        when (_mode.value) {
            RangeMode.BLUETOOTH -> bleScanner.start { nh ->
                add(Helper(nh.device.address, nh.name, nh.rssi, null, bt = nh.device, psm = nh.psm))
            }
            RangeMode.WIFI -> wifi.startDiscovery { h -> add(h) }
        }
    }

    fun connectToHelper(helper: Helper) {
        stopDiscovery()
        _state.value = LinkState.CONNECTING
        _payeeUpi.value = helper.payeeUpi        // provisional (Wi-Fi advertises it); confirmed on approval
        when (_mode.value) {
            RangeMode.BLUETOOTH -> {
                val dev = helper.bt ?: run { _state.value = LinkState.ENDED; return }
                val link = BluetoothVoiceLink(appContext).also { bt = it }
                scope.launch {
                    val ch = link.connectTo(dev, helper.psm)
                    if (ch != null) onSeekerConnected(ch) else _state.value = LinkState.ENDED
                }
            }
            RangeMode.WIFI -> {
                val dev = helper.wifi ?: run { _state.value = LinkState.ENDED; return }
                wifi.connect(dev,
                    onConnected = { ch -> onSeekerConnected(ch) },
                    onError = { _state.value = LinkState.ENDED })
            }
        }
    }

    private fun onSeekerConnected(ch: VoiceChannel) {
        channel = ch
        _state.value = LinkState.WAITING_APPROVAL
        scope.launch {
            val approval = Handshake.parseApproval(ch.readLine())
            if (approval == null) { _state.value = LinkState.ENDED; return@launch }
            approval.upi?.let { _payeeUpi.value = it }   // authoritative payee from the Helper
            goLive()
        }
    }

    // ---------------- Shared ----------------

    private fun goLive() {
        val ch = channel ?: run { _state.value = LinkState.ENDED; return }
        streamer = VoiceStreamer(ch.input, ch.output).also { it.start() }
        _state.value = LinkState.LIVE
        scope.launch {
            while (_state.value == LinkState.LIVE) { delay(1000); _seconds.value += 1 }
        }
    }

    private fun stopDiscovery() {
        bleScanner.stop()
        // Wi-Fi discovery is stopped as part of wifi.stop() on end/reset.
    }

    fun end() {
        advertiser.stop(); bleScanner.stop(); runCatching { wifi.stop() }
        streamer?.stop(); streamer = null
        channel?.close(); channel = null
        bt?.close(); bt = null
        _state.value = LinkState.ENDED
    }

    fun reset() {
        _role.value = Role.IDLE
        _state.value = LinkState.IDLE
        _helpers.value = emptyList()
        _seconds.value = 0
        _payeeUpi.value = null
    }

    companion object {
        @Volatile private var instance: SessionManager? = null
        fun get(context: Context): SessionManager =
            instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also { instance = it }
            }
    }
}
