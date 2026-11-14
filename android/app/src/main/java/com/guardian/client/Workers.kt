package com.guardian.client

import android.content.Context
import android.location.LocationManager
import androidx.work.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * CollectWorker - runs often, no network needed. Pulls call log + SMS and grabs
 * last-known location into the Store. This is what keeps data accumulating while
 * the phone is offline.
 */
class CollectWorker(ctx: Context, p: WorkerParameters) : Worker(ctx, p) {
    override fun doWork(): Result {
        runCatching { Collectors.collectAll(applicationContext) }
        runCatching { grabLocation() }
        // retention: keep 30 days on device
        runCatching {
            Store(applicationContext).prune(System.currentTimeMillis() - 30L * 86400_000)
        }
        return Result.success()
    }

    private fun grabLocation() {
        val lm = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = try {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        } catch (_: SecurityException) { null } ?: return
        Store(applicationContext).addLoc(loc.time, loc.latitude, loc.longitude, loc.accuracy)
    }
}

/**
 * ReportWorker - builds the PDF for "everything since the last successful send"
 * and emails it. Network-constrained, so if the phone is offline at the
 * scheduled time the job waits and fires the moment connectivity returns,
 * covering the whole gap. The watermark only advances after a successful send,
 * so a failed attempt loses nothing and is retried with backoff.
 *
 * inputData "type" is "4h" or "daily".
 */
class ReportWorker(ctx: Context, p: WorkerParameters) : Worker(ctx, p) {
    override fun doWork(): Result {
        val type = inputData.getString("type") ?: "4h"
        val cfg = Config(applicationContext)
        if (!cfg.configured) return Result.success()  // nothing to do until set up

        // make sure the latest data is captured before reporting
        runCatching { Collectors.collectAll(applicationContext) }

        val now = System.currentTimeMillis()
        val defaultSpan = if (type == "daily") 86400_000L else 4 * 3600_000L
        val from = cfg.watermark(type, now - defaultSpan)
        if (from >= now) return Result.success()

        // Skip sending when the window has no activity at all; advance the
        // watermark so the next window simply starts here. Nothing is lost.
        val store = Store(applicationContext)
        val total = store.window("calls", from, now).size +
                    store.window("sms", from, now).size +
                    store.window("notifs", from, now).size +
                    store.window("locs", from, now).size
        if (total == 0) { cfg.setWatermark(type, now); return Result.success() }

        val label = if (type == "daily") "Daily report" else "4-hourly report"
        return try {
            val pdf = ReportBuilder.build(applicationContext, label, from, now)
            val subject = "Lockkout - $label - ${range(from, now)}"
            Mailer.send(cfg, subject,
                "Automated $label from Lockkout. Window ${range(from, now)} IST. " +
                "Details attached as PDF.", pdf)
            pdf.delete()
            cfg.setWatermark(type, now)   // advance only on success
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.success()
        }
    }

    private fun range(from: Long, to: Long): String {
        val f = SimpleDateFormat("dd MMM HH:mm", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }
        return "${f.format(Date(from))} - ${f.format(Date(to))}"
    }
}
