package com.guardian.client

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/** Assembles the report sections for a time window and renders the PDF. */
object ReportBuilder {

    private fun ist(ms: String) = SimpleDateFormat("dd MMM HH:mm", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }
        .format(Date(ms.toLong()))

    private fun dur(sec: String): String {
        val s = sec.toIntOrNull() ?: 0
        return if (s >= 60) "${s / 60}m ${s % 60}s" else "${s}s"
    }

    fun build(ctx: Context, label: String, from: Long, to: Long): File {
        val store = Store(ctx)
        val calls = store.window("calls", from, to)
        val sms = store.window("sms", from, to)
        val notifs = store.window("notifs", from, to)
        val locs = store.window("locs", from, to)

        val totalTalk = calls.sumOf { (it["duration"] ?: "0").toInt() }
        val summary = Section("Summary", listOf(
            "Calls: ${calls.size}    Total talk: ${dur(totalTalk.toString())}",
            "Messages: ${sms.size}    Notifications: ${notifs.size}",
            "Location points: ${locs.size}"
        ))

        val callLines = calls.map {
            "${ist(it["ts"]!!)}  ${it["type"]?.padEnd(9)}  ${it["number"]}" +
            (it["name"]?.takeIf { n -> n.isNotBlank() }?.let { n -> " ($n)" } ?: "") +
            "  ${dur(it["duration"] ?: "0")}"
        }
        val smsLines = sms.map {
            "${ist(it["ts"]!!)}  ${it["direction"]}  ${it["number"]}\n    ${it["body"]}"
        }.flatMap { it.split("\n") }
        val notifLines = notifs.map {
            "${ist(it["ts"]!!)}  [${it["app"]?.ifBlank { it["pkg"] }}]  ${it["title"]}: ${it["text"]}"
        }
        val locLines = locs.map {
            "${ist(it["ts"]!!)}  ${it["lat"]}, ${it["lon"]}  (+/-${it["acc"]?.toFloatOrNull()?.toInt()}m)" +
            "  https://maps.google.com/?q=${it["lat"]},${it["lon"]}"
        }

        val subtitle = "Window: ${ist(from.toString())} to ${ist(to.toString())} IST  |  $label"
        return PdfReport.build(ctx, "Guardian report", subtitle, listOf(
            summary,
            Section("Calls", callLines),
            Section("Messages (SMS)", smsLines),
            Section("Notifications", notifLines),
            Section("Location", locLines)
        ))
    }
}
