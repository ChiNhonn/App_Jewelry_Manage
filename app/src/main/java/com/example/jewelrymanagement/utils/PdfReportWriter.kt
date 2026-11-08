package com.example.jewelrymanagement.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.jewelrymanagement.repositories.Report
import com.example.jewelrymanagement.repositories.ReportCell
import com.example.jewelrymanagement.repositories.ReportTable
import java.io.OutputStream

/**
 * Renders a [Report] to PDF with android.graphics.pdf.PdfDocument (no external library).
 * Layout runs twice: a dry run counts pages so every page can print "Trang x/y".
 */
object PdfReportWriter {

    private const val STORE_NAME = "JewelryManagement"
    private const val MARGIN = 36f
    private const val CELL_PAD = 4f
    private const val MAX_CELL_LINES = 4

    private val gold = Color.rgb(124, 90, 4)
    private val headerFill = Color.rgb(245, 234, 203)
    private val zebraFill = Color.rgb(251, 248, 238)
    private val totalsFill = Color.rgb(235, 222, 180)
    private val gridColor = Color.rgb(210, 195, 160)

    fun write(report: Report, out: OutputStream) {
        val landscape = report.tables.any { it.columns.size > 6 }
        val width = if (landscape) 842 else 595
        val height = if (landscape) 595 else 842

        val totalPages = Layout(report, width, height, null, 0).run()
        val pdf = PdfDocument()
        try {
            Layout(report, width, height, pdf, totalPages).run()
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
    }

    private class Layout(
        private val report: Report,
        private val width: Int,
        private val height: Int,
        private val pdf: PdfDocument?,
        private val totalPages: Int
    ) {
        private val titlePaint = paint(18f, bold = true, color = gold)
        private val subtitlePaint = paint(13f, bold = true, color = Color.BLACK)
        private val bodyPaint = paint(10f, color = Color.DKGRAY)
        private val smallPaint = paint(8f, color = Color.GRAY)
        private val sectionPaint = paint(11.5f, bold = true, color = gold)
        private val cellPaint = paint(8.5f, color = Color.BLACK)
        private val cellBoldPaint = paint(8.5f, bold = true, color = Color.BLACK)
        private val fillPaint = Paint().apply { style = Paint.Style.FILL }
        private val gridPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.6f; color = gridColor }

        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = 0f
        private val contentWidth = width - 2 * MARGIN
        private val bottomLimit get() = height - MARGIN - 16f

        /** Returns the number of pages produced. */
        fun run(): Int {
            newPage()
            drawCoverHeader()
            if (report.summary.isNotEmpty()) drawSummary()
            if (report.isEmpty) {
                ensureSpace(30f)
                y += 10f
                canvas?.drawText("Không có dữ liệu trong khoảng thời gian và bộ lọc đã chọn.", MARGIN, y, bodyPaint)
                y += 16f
            }
            report.tables.forEach { drawTable(it) }
            finishPage()
            return pageNumber
        }

        private fun newPage() {
            finishPage()
            pageNumber++
            if (pdf != null) {
                val p = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
                page = p
                canvas = p.canvas
            }
            y = MARGIN
            if (pageNumber > 1) {
                canvas?.drawText("$STORE_NAME • ${report.title}", MARGIN, y + 8f, smallPaint)
                y += 20f
            }
        }

        private fun finishPage() {
            val p = page ?: return
            val footer = "Trang $pageNumber/$totalPages"
            val c = p.canvas
            c.drawLine(MARGIN, height - MARGIN - 8f, width - MARGIN, height - MARGIN - 8f, gridPaint)
            c.drawText("Lập lúc ${DateUtils.toDisplay(report.generatedAt)}", MARGIN, height - MARGIN + 4f, smallPaint)
            c.drawText(footer, width - MARGIN - smallPaint.measureText(footer), height - MARGIN + 4f, smallPaint)
            pdf?.finishPage(p)
            page = null
            canvas = null
        }

        private fun ensureSpace(needed: Float): Boolean {
            if (y + needed > bottomLimit) {
                newPage()
                return true
            }
            return false
        }

        private fun drawCoverHeader() {
            y += 14f
            canvas?.drawText(STORE_NAME, MARGIN, y, titlePaint)
            y += 20f
            canvas?.drawText(report.title.uppercase(java.util.Locale.forLanguageTag("vi-VN")), MARGIN, y, subtitlePaint)
            y += 16f
            canvas?.drawText(
                "Kỳ báo cáo: ${DateUtils.dayToDisplay(report.startDay)} – ${DateUtils.dayToDisplay(report.endDay)}   •   Trạng thái đơn: ${report.statusLabel}",
                MARGIN, y, bodyPaint
            )
            y += 14f
            canvas?.drawText("Ngày lập: ${DateUtils.toDisplay(report.generatedAt)}   •   Đơn vị tiền: VNĐ", MARGIN, y, bodyPaint)
            y += 10f
            canvas?.drawLine(MARGIN, y, width - MARGIN, y, gridPaint)
            y += 14f
        }

        private fun drawSummary() {
            ensureSpace(24f)
            canvas?.drawText("TỔNG QUAN", MARGIN, y, sectionPaint)
            y += 14f
            val labelWidth = contentWidth * 0.45f
            for ((label, value) in report.summary) {
                val lines = wrap(value, bodyPaint, contentWidth - labelWidth, 2)
                ensureSpace(13f * lines.size)
                canvas?.drawText(ellipsize(label, bodyPaint, labelWidth - 6f), MARGIN, y, bodyPaint)
                lines.forEachIndexed { i, line -> canvas?.drawText(line, MARGIN + labelWidth, y + i * 13f, cellBoldPaint) }
                y += 13f * lines.size
            }
            y += 8f
        }

        private fun drawTable(table: ReportTable) {
            ensureSpace(60f)
            y += 6f
            canvas?.drawText(table.title, MARGIN, y, sectionPaint)
            y += 8f

            val totalWeight = table.columns.sumOf { it.weight.toDouble() }.toFloat()
            val widths = table.columns.map { contentWidth * it.weight / totalWeight }
            val numericCols = table.columns.indices.map { col -> table.rows.any { it.getOrNull(col)?.isNumeric == true } }

            drawRow(table.columns.map { it.header }, widths, List(widths.size) { false }, cellBoldPaint, headerFill)
            if (table.rows.isEmpty()) {
                val h = 18f
                ensureSpace(h)
                canvas?.drawRect(MARGIN, y, MARGIN + contentWidth, y + h, gridPaint)
                canvas?.drawText("Không có dữ liệu", MARGIN + CELL_PAD, y + 12f, cellPaint)
                y += h
            }
            table.rows.forEachIndexed { index, row ->
                val texts = row.map { it.display() }
                val rowHeight = rowHeight(texts, widths, cellPaint)
                if (y + rowHeight > bottomLimit) {
                    newPage()
                    drawRow(table.columns.map { it.header }, widths, List(widths.size) { false }, cellBoldPaint, headerFill)
                }
                drawRow(texts, widths, numericCols, cellPaint, if (index % 2 == 1) zebraFill else null)
            }
            table.totals?.let { totals ->
                val texts = totals.map { it.display() }
                if (y + rowHeight(texts, widths, cellBoldPaint) > bottomLimit) newPage()
                drawRow(texts, widths, totals.map { it.isNumeric }, cellBoldPaint, totalsFill)
            }
            table.note?.let { note ->
                y += 4f
                wrap("Ghi chú: $note", smallPaint, contentWidth, 3).forEach { line ->
                    ensureSpace(11f)
                    y += 10f
                    canvas?.drawText(line, MARGIN, y, smallPaint)
                }
            }
            y += 14f
        }

        private fun rowHeight(texts: List<String>, widths: List<Float>, p: Paint): Float {
            val lineHeight = p.textSize + 2.5f
            val maxLines = texts.indices.maxOfOrNull { wrap(texts[it], p, widths[it] - 2 * CELL_PAD, MAX_CELL_LINES).size } ?: 1
            return maxLines.coerceAtLeast(1) * lineHeight + 2 * CELL_PAD + 2f
        }

        private fun drawRow(texts: List<String>, widths: List<Float>, rightAlign: List<Boolean>, p: Paint, fill: Int?) {
            val h = rowHeight(texts, widths, p)
            ensureSpace(h)
            val c = canvas
            var x = MARGIN
            val lineHeight = p.textSize + 2.5f
            texts.forEachIndexed { i, text ->
                val w = widths[i]
                if (c != null) {
                    if (fill != null) {
                        fillPaint.color = fill
                        c.drawRect(x, y, x + w, y + h, fillPaint)
                    }
                    c.drawRect(x, y, x + w, y + h, gridPaint)
                    wrap(text, p, w - 2 * CELL_PAD, MAX_CELL_LINES).forEachIndexed { li, line ->
                        val tx = if (rightAlign.getOrElse(i) { false }) x + w - CELL_PAD - p.measureText(line) else x + CELL_PAD
                        c.drawText(line, tx, y + CELL_PAD + p.textSize + li * lineHeight, p)
                    }
                }
                x += w
            }
            y += h
        }

        /** Word wrap that also splits very long words; the last allowed line is ellipsized. */
        private fun wrap(text: String, p: Paint, maxWidth: Float, maxLines: Int): List<String> {
            if (text.isEmpty()) return listOf("")
            val lines = mutableListOf<String>()
            var current = StringBuilder()
            for (word in text.replace('\n', ' ').split(' ')) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (p.measureText(candidate) <= maxWidth) {
                    current = StringBuilder(candidate)
                    continue
                }
                if (current.isNotEmpty()) {
                    lines.add(current.toString())
                    current = StringBuilder()
                }
                var rest = word
                while (p.measureText(rest) > maxWidth && rest.length > 1) {
                    val n = p.breakText(rest, true, maxWidth, null).coerceAtLeast(1)
                    lines.add(rest.substring(0, n))
                    rest = rest.substring(n)
                }
                current = StringBuilder(rest)
            }
            if (current.isNotEmpty()) lines.add(current.toString())
            if (lines.size <= maxLines) return lines
            val kept = lines.take(maxLines).toMutableList()
            kept[maxLines - 1] = ellipsize(kept[maxLines - 1] + "…", p, maxWidth)
            return kept
        }

        private fun ellipsize(text: String, p: Paint, maxWidth: Float): String {
            if (p.measureText(text) <= maxWidth) return text
            var end = text.length
            while (end > 0 && p.measureText(text.substring(0, end) + "…") > maxWidth) end--
            return text.substring(0, end) + "…"
        }

        private fun paint(size: Float, bold: Boolean = false, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
    }
}
