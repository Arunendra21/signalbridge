package com.signalbridge.session

import android.bluetooth.BluetoothDevice
import android.net.wifi.p2p.WifiP2pDevice

/**
 * A nearby helper as shown in the Seeker's list, independent of which radio found it.
 * Bluetooth fills [bt] + [psm]; Wi-Fi Direct fills [wifi] (and often [payeeUpi], since
 * Wi-Fi advertises the UPI id up front). [id] is a stable key for de-duping the list.
 */
data class Helper(
    val id: String,
    val name: String,
    val signalDbm: Int?,          // BLE RSSI; null for Wi-Fi Direct
    val payeeUpi: String?,        // known at discovery for Wi-Fi; arrives on approval for BLE
    val bt: BluetoothDevice? = null,
    val psm: Int = 0,
    val wifi: WifiP2pDevice? = null,
)
