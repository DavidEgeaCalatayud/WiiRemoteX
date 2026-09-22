package io.github.davidegeacalatayud.wiiremotex

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder

class WiiRemoteForegroundService : Service() {

    private val runtime: WiiRemoteRuntime
        get() = (application as WiiRemoteXApplication).runtime

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START_HID) {
            ACTION_STOP_HID -> {
                runtime.stopHid()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                runtime.startHid()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        runtime.stopHid()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "WiiRemoteX controller service",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps WiiRemoteX Bluetooth HID emulation active"
            },
        )
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, WiiRemoteForegroundService::class.java).apply {
            action = ACTION_STOP_HID
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wiiremote_notification)
            .setContentTitle("WiiRemoteX active")
            .setContentText("Bluetooth HID emulation is running")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Stop",
                    stopPendingIntent,
                ).build(),
            )
            .build()
    }

    companion object {
        const val ACTION_START_HID = "io.github.davidegeacalatayud.wiiremotex.START_HID"
        const val ACTION_STOP_HID = "io.github.davidegeacalatayud.wiiremotex.STOP_HID"

        private const val CHANNEL_ID = "wiiremote_hid"
        private const val NOTIFICATION_ID = 1001
    }
}
