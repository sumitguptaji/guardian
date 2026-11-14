package com.guardian.client

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reschedule the background jobs after a reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && Config(context).configured) {
            Scheduler.scheduleAll(context)
        }
    }
}
