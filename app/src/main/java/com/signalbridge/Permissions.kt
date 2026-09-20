package com.signalbridge

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The runtime permissions SignalBridge needs differ by Android version — this is the #1
 * reason discovery silently returns nothing if you get it wrong:
 *   - Bluetooth scan: BLUETOOTH_SCAN/ADVERTISE/CONNECT on 12+; FINE_LOCATION on 10-11.
 *   - Wi-Fi Direct discovery: NEARBY_WIFI_DEVICES on 13+; FINE_LOCATION on 10-12.
 * RECORD_AUDIO is always needed for voice.
 */
object Permissions {

    fun required(): Array<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {          // 31+
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {   // 33+
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            // Covers BLE scan (<31) and Wi-Fi Direct discovery (<33).
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }.toTypedArray()

    fun allGranted(context: Context): Boolean = required().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun missing(context: Context): List<String> = required().filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }
}
