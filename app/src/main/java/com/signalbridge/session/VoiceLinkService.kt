package com.signalbridge.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Keeps the voice link alive when the screen is off. Android requires a foreground
 * service (with a visible notification) for a long-running connected-device session.
 */
class VoiceLinkService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "signalbridge_voice"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Voice link", NotificationManager.IMPORTANCE_LOW)
        )
        val notif: Notification = Notification.Builder(this, channelId)
            .setContentTitle("SignalBridge")
            .setContentText("Voice link active over Bluetooth")
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setOngoing(true)
            .build()
        startForeground(1, notif)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, VoiceLinkService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, VoiceLinkService::class.java))
    }
}
