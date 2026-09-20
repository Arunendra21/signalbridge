package com.signalbridge.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.util.Log
import com.signalbridge.Protocol
import com.signalbridge.session.Helper
import com.signalbridge.transport.VoiceChannel
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Long-range transport (beta): Wi-Fi Direct. Range ~100–200 m, clear low-latency voice,
 * and — like the whole app — ZERO mobile data: all traffic stays inside the phone-to-phone
 * Wi-Fi Direct group and never touches the internet. (It does need the INTERNET permission,
 * which is only Android's name for "may open a socket".)
 *
 * Discovery uses Wi-Fi P2P DNS-SD service records, so the Helper's name and UPI id are
 * advertised up front. Once a group forms, whichever phone is the group owner hosts a TCP
 * server on [Protocol.WIFI_TCP_PORT] and the other connects; both wrap it in a [VoiceChannel].
 *
 * Behaviour varies by phone vendor — this needs testing on real hardware across models.
 */
class WifiDirectVoice(private val context: Context) {

    private val manager by lazy { context.getSystemService(WifiP2pManager::class.java) }
    private val channel by lazy { manager.initialize(context, context.mainLooper, null) }

    private var receiver: BroadcastReceiver? = null
    private var onConnected: ((VoiceChannel) -> Unit)? = null
    private var onError: (() -> Unit)? = null
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null

    private val actionFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
    }

    // ---------------- Helper (advertise + wait) ----------------

    @SuppressLint("MissingPermission") // caller ensures nearby-wifi / location perms
    fun startHelper(name: String, upi: String?, onConnected: (VoiceChannel) -> Unit, onError: () -> Unit) {
        this.onConnected = onConnected
        this.onError = onError
        registerReceiver()

        val record = mapOf("name" to name.take(20), "upi" to (upi ?: ""))
        val service = WifiP2pDnsSdServiceInfo.newInstance(
            Protocol.WIFI_SERVICE_INSTANCE, Protocol.WIFI_SERVICE_TYPE, record
        )
        manager.addLocalService(channel, service, actionLog("addLocalService"))
        // Being in active discovery keeps us connectable to a Seeker that initiates.
        manager.discoverPeers(channel, actionLog("discoverPeers(helper)"))
    }

    // ---------------- Seeker (discover + connect) ----------------

    @SuppressLint("MissingPermission")
    fun startDiscovery(onHelper: (Helper) -> Unit) {
        registerReceiver()
        manager.setDnsSdResponseListeners(channel, { _, _, _ -> }) { _, txt, device ->
            val name = txt["name"]?.takeIf { it.isNotBlank() } ?: Protocol.DEFAULT_HELPER_NAME
            val upi = txt["upi"]?.takeIf { it.isNotBlank() }
            onHelper(Helper(id = device.deviceAddress, name = name, signalDbm = null, payeeUpi = upi, wifi = device))
        }
        manager.addServiceRequest(channel, WifiP2pDnsSdServiceRequest.newInstance(), actionLog("addServiceRequest"))
        manager.discoverServices(channel, actionLog("discoverServices"))
    }

    @SuppressLint("MissingPermission")
    fun connect(device: WifiP2pDevice, onConnected: (VoiceChannel) -> Unit, onError: () -> Unit) {
        this.onConnected = onConnected
        this.onError = onError
        registerReceiver()
        val config = WifiP2pConfig().apply { deviceAddress = device.deviceAddress }
        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.i(TAG, "connect() invited") }
            override fun onFailure(reason: Int) { Log.w(TAG, "connect failed: $reason"); onError() }
        })
    }

    // ---------------- Connection formed -> TCP channel ----------------

    private fun registerReceiver() {
        if (receiver != null) return
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action != WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) return
                manager.requestConnectionInfo(channel) { info ->
                    if (info.groupFormed) establishChannel(info.isGroupOwner, info.groupOwnerAddress?.hostAddress)
                }
            }
        }
        context.registerReceiver(receiver, actionFilter)
    }

    private fun establishChannel(isGroupOwner: Boolean, groupOwnerIp: String?) {
        // Guard against the receiver firing twice for one group.
        if (socket != null || serverSocket != null) return
        thread(name = "wifi-p2p-socket") {
            try {
                val s: Socket = if (isGroupOwner) {
                    val server = ServerSocket().apply {
                        reuseAddress = true
                        bind(InetSocketAddress(Protocol.WIFI_TCP_PORT))
                    }
                    serverSocket = server
                    server.accept()
                } else {
                    val client = Socket()
                    client.connect(InetSocketAddress(groupOwnerIp ?: return@thread, Protocol.WIFI_TCP_PORT), 10_000)
                    client
                }
                s.tcpNoDelay = true              // low latency for voice
                socket = s
                onConnected?.invoke(VoiceChannel(s.inputStream, s.outputStream, s))
            } catch (e: Exception) {
                Log.e(TAG, "wifi channel failed", e)
                onError?.invoke()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        runCatching { manager.clearLocalServices(channel, null) }
        runCatching { manager.clearServiceRequests(channel, null) }
        runCatching { manager.removeGroup(channel, null) }
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        runCatching { socket?.close() }
        runCatching { serverSocket?.close() }
        socket = null; serverSocket = null
    }

    private fun actionLog(what: String) = object : WifiP2pManager.ActionListener {
        override fun onSuccess() { Log.i(TAG, "$what ok") }
        override fun onFailure(reason: Int) { Log.w(TAG, "$what failed: $reason") }
    }

    private companion object { const val TAG = "WifiDirectVoice" }
}
