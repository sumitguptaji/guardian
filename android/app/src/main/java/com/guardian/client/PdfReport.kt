package com.guardian.client

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/** A heading followed by text lines. */
data class Section(val heading: String, val lines: List<String>)

/**
 * Renders report sections to a paginated A4 PDF using Android's built-in
 * PdfDocument - no third-party PDF library, works fine on a background thread.
 */
object PdfReport {

    private const val W = 595   // A4 @ 72dpi
    private const val H = 842
    private const val MARGIN = 36f
    private const val LINE = 14f

    fun build(ctx: Context, title: String, subtitle: String, sections: List<Section>): File {
        val doc = PdfDocument()
        val body = Paint().apply { textSize = 9f }
        val head = Paint().apply { textSize = 12f; isFakeBoldText = true }
        val titleP = Paint().apply { textSize = 16f; isFakeBoldText = true }

        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
        var canvas = page.canvas
        var y = MARGIN
        var pageNo = 1

        fun newPage() {
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            canvas = page.canvas
            y = MARGIN
        }
        fun ensure(space: Float) { if (y + space > H - MARGIN) newPage() }

        canvas.drawText(title, MARGIN, y, titleP); y += 20f
        canvas.drawText(subtitle, MARGIN, y, body); y += 18f

        val maxChars = ((W - 2 * MARGIN) / 5f).toInt()  // rough wrap width
        for (sec in sections) {
            ensure(LINE * 2)
            y += 6f
            canvas.drawText(sec.heading, MARGIN, y, head); y += LINE + 2f
            if (sec.lines.isEmpty()) {
                ensure(LINE); canvas.drawText("  (none)", MARGIN, y, body); y += LINE
                continue
            }
            for (raw in sec.lines) {
                for (line in wrap(raw, maxChars)) {
                    ensure(LINE)
                    canvas.drawText(line, MARGIN, y, body)
                    y += LINE
                }
            }
        }
        doc.finishPage(page)

        val out = File(ctx.cacheDir, "guardian_${stamp()}.pdf")
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private fun wrap(s: String, max: Int): List<String> {
        if (s.length <= max) return listOf(s)
        val out = ArrayList<String>()
        var rest = s
        while (rest.length > max) {
            out.add(rest.substring(0, max))
            rest = "    " + rest.substring(max)
        }
        if (rest.isNotBlank()) out.add(rest)
        return out
    }

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }
        .format(Date())
}
