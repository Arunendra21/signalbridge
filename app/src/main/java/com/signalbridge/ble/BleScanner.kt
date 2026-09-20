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
import android.util.Log
import com.signalbridge.Protocol

/**
 * Seeker side of discovery. Scans for nearby Helpers advertising our service UUID and
 * reports each one with the L2CAP PSM to connect to, a readable name, and signal
 * strength — so the list shows real, distinct entries (not one blank/duplicate MAC).
 */
class BleScanner(private val context: Context) {

    data class NearbyHelper(
        val device: BluetoothDevice,
        val rssi: Int,
        val name: String,
        val psm: Int,
    )

    private val scanner by lazy {
        context.getSystemService(BluetoothManager::class.java).adapter?.bluetoothLeScanner
    }
    private var cb: ScanCallback? = null

    @SuppressLint("MissingPermission") // caller ensures scan permission granted
    fun start(onFound: (NearbyHelper) -> Unit) {
        val s = scanner ?: run { Log.w(TAG, "no LE scanner"); return }
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(Protocol.BLE_SERVICE_UUID)).build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord ?: return
                val psmBytes = record.getManufacturerSpecificData(Protocol.MANUFACTURER_ID)
                if (psmBytes == null || psmBytes.size < 2) return   // not fully advertised yet
                val psm = ((psmBytes[0].toInt() and 0xFF) shl 8) or (psmBytes[1].toInt() and 0xFF)

                val nameBytes = record.getManufacturerSpecificData(Protocol.MANUFACTURER_ID + 1)
                val name = nameBytes?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() }
                    ?: Protocol.DEFAULT_HELPER_NAME

                onFound(NearbyHelper(result.device, result.rssi, name, psm))
            }

            override fun onScanFailed(errorCode: Int) { Log.w(TAG, "scan failed: $errorCode") }
        }
        s.startScan(filters, settings, cb)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        cb?.let { runCatching { scanner?.stopScan(it) } }
        cb = null
    }

    private companion object { const val TAG = "BleScanner" }
}
