package com.guardian.client

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Minimal foreground service whose only job is the permanent, non-dismissable
 * "Monitoring active" notification - the visible, honest indicator that this
 * phone is monitored. The actual data collection and reporting run in
 * WorkManager (see Scheduler), which survives this service being killed.
 */
class MonitorService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channelId = "guardian"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(
                    NotificationChannel(channelId, "Guardian",
                        NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Shows that family monitoring is active"
                    })
        }
        val n = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Monitoring active")
            .setContentText("This phone sends activity reports to a parent.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
        startForeground(1, n)
        Scheduler.scheduleAll(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
}
