package com.signalbridge.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.signalbridge.Protocol

/**
 * Seeker side of discovery. Scans for nearby Helpers broadcasting our service UUID
 * and reports them (with signal strength, so the nearest/strongest helper floats up).
 */
class BleScanner(private val context: Context) {

    data class NearbyHelper(val device: BluetoothDevice, val rssi: Int)

    private val scanner by lazy {
        context.getSystemService(BluetoothManager::class.java).adapter?.bluetoothLeScanner
    }
    private var cb: ScanCallback? = null

    @SuppressLint("MissingPermission") // caller ensures BLUETOOTH_SCAN is granted
    fun start(onFound: (NearbyHelper) -> Unit) {
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(Protocol.BLE_SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                onFound(NearbyHelper(result.device, result.rssi))
            }
        }
        scanner?.startScan(filters, settings, cb)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        cb?.let { scanner?.stopScan(it) }
        cb = null
    }
}
