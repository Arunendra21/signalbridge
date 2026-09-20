package com.signalbridge.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.signalbridge.Protocol

/**
 * Helper side of discovery. Broadcasts a BLE advertisement that says "a SignalBridge
 * Helper is here", carries the L2CAP PSM the Helper is listening on (in manufacturer
 * data), and puts a human-readable name in the scan response so the Seeker's list shows
 * something meaningful instead of a randomised MAC. Costs no data.
 */
class BleAdvertiser(private val context: Context) {

    private val advertiser by lazy {
        context.getSystemService(BluetoothManager::class.java).adapter?.bluetoothLeAdvertiser
    }

    private val callback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) = Log.w(TAG, "advertise failed: $errorCode")
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) = Log.i(TAG, "advertising")
    }

    /**
     * @param psm  the L2CAP PSM a Seeker should connect to (from the server socket).
     * @param name short label to show in the Seeker's list (e.g. the phone model).
     */
    @SuppressLint("MissingPermission") // caller ensures BLUETOOTH_ADVERTISE granted
    fun start(psm: Int, name: String) {
        val adv = advertiser ?: run { Log.w(TAG, "no LE advertiser"); return }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        // Primary advertisement: service UUID (for filtering) + PSM (2 bytes, big-endian).
        val psmBytes = byteArrayOf((psm shr 8).toByte(), psm.toByte())
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(Protocol.BLE_SERVICE_UUID))
            .addManufacturerData(Protocol.MANUFACTURER_ID, psmBytes)
            .build()

        // Scan response: the readable name (kept short to fit the 31-byte budget).
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addManufacturerData(
                Protocol.MANUFACTURER_ID + 1,
                name.take(20).toByteArray(Charsets.UTF_8)
            )
            .build()

        adv.startAdvertising(settings, data, scanResponse, callback)
    }

    @SuppressLint("MissingPermission")
    fun stop() { advertiser?.stopAdvertising(callback) }

    private companion object { const val TAG = "BleAdvertiser" }
}
