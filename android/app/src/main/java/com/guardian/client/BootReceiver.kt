package com.guardian.client

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Restart the visible service and reschedule jobs after a reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && Config(context).configured) {
            ContextCompat.startForegroundService(
                context, Intent(context, MonitorService::class.java))
            Scheduler.scheduleAll(context)
        }
    }
}
