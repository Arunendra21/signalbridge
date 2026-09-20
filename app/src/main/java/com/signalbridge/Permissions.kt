package com.signalbridge

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The runtime permissions SignalBridge needs differ by Android version — this is the #1
 * reason BLE scanning silently returns nothing if you get it wrong:
 *   - Android 12+ (API 31+): the new BLUETOOTH_SCAN / ADVERTISE / CONNECT runtime perms.
 *   - Android 10-11 (API 29-30): ACCESS_FINE_LOCATION is REQUIRED for scans to work.
 * RECORD_AUDIO is always needed for voice.
 */
object Permissions {

    fun required(): Array<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // API 29-30: scanning is gated on fine location.
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
