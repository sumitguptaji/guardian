package com.guardian.client

import android.content.Context
import androidx.work.*
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Registers the periodic jobs. WorkManager persists these across reboots and
 * app restarts, and enforces the network constraint + backoff that give us
 * reliable offline catch-up.
 */
object Scheduler {

    private val onNetwork = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun scheduleAll(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)

        // Collection every 15 min (WorkManager minimum), no network needed.
        wm.enqueueUniquePeriodicWork(
            "collect", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CollectWorker>(15, TimeUnit.MINUTES).build()
        )

        // 4-hourly report.
        wm.enqueueUniquePeriodicWork(
            "report_4h", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ReportWorker>(4, TimeUnit.HOURS)
                .setConstraints(onNetwork)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .setInputData(workDataOf("type" to "4h"))
                .build()
        )

        // Daily report, first run aimed at the configured hour (IST).
        wm.enqueueUniquePeriodicWork(
            "report_daily", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ReportWorker>(24, TimeUnit.HOURS)
                .setConstraints(onNetwork)
                .setInitialDelay(delayToHour(Config(ctx).dailyHour), TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .setInputData(workDataOf("type" to "daily"))
                .build()
        )
    }

    /** Minutes from now until the next occurrence of [hour]:00 in IST. */
    private fun delayToHour(hour: Int): Long {
        val tz = TimeZone.getTimeZone("Asia/Kolkata")
        val now = Calendar.getInstance(tz)
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        return (next.timeInMillis - now.timeInMillis) / 60000
    }
}
