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
 * Helper side of discovery. Broadcasts a tiny BLE advertisement that says
 * "a SignalBridge Helper is here and willing to relay a call." Costs no data —
 * it's a beacon on the 2.4GHz band, a few bytes every ~100ms.
 */
class BleAdvertiser(private val context: Context) {

    private val advertiser by lazy {
        val mgr = context.getSystemService(BluetoothManager::class.java)
        mgr.adapter?.bluetoothLeAdvertiser
    }

    private val callback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "BLE advertise failed: $errorCode")
        }
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(TAG, "Advertising as Helper")
        }
    }

    @SuppressLint("MissingPermission") // caller ensures BLUETOOTH_ADVERTISE is granted
    fun start() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(Protocol.BLE_SERVICE_UUID))
            .build()
        advertiser?.startAdvertising(settings, data, callback)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        advertiser?.stopAdvertising(callback)
    }

    private companion object { const val TAG = "BleAdvertiser" }
}
