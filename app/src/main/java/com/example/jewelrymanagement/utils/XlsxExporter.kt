package com.example.jewelrymanagement.utils

import com.example.jewelrymanagement.repositories.Report
import com.example.jewelrymanagement.repositories.ReportCell
import com.example.jewelrymanagement.repositories.ReportTable
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal but real .xlsx (Office Open XML) writer built on ZipOutputStream, so no external
 * library is needed. Money is stored as numbers with a "#,##0 ₫" format, dates as Excel
 * serial dates, so the file can be summed/sorted/filtered in Excel or Google Sheets.
 */
object XlsxExporter {

    private const val STYLE_HEADER = 1
    private const val STYLE_TEXT = 2
    private const val STYLE_MONEY = 3
    private const val STYLE_INT = 4
    private const val STYLE_DATETIME = 5
    private const val STYLE_DATE = 6
    private const val STYLE_DECIMAL = 7
    private const val STYLE_TOTAL_TEXT = 8
    private const val STYLE_TOTAL_MONEY = 9
    private const val STYLE_TOTAL_INT = 10
    private const val STYLE_TITLE = 11
    private const val STYLE_TOTAL_DECIMAL = 12
    private const val STYLE_LABEL = 13

    private const val HEADER_ROW = 4

    private class SheetSpec(val name: String, val table: ReportTable?)

    fun write(report: Report, out: OutputStream) {
        val specs = mutableListOf<SheetSpec>()
        val usedNames = mutableSetOf<String>()
        specs.add(SheetSpec(uniqueName("TongQuan", usedNames), null))
        report.tables.forEach { specs.add(SheetSpec(uniqueName(it.sheetName, usedNames), it)) }

        val zip = ZipOutputStream(out)
        val writer = BufferedWriter(OutputStreamWriter(zip, Charsets.UTF_8))
        fun entry(name: String, body: (BufferedWriter) -> Unit) {
            zip.putNextEntry(ZipEntry(name))
            body(writer)
            writer.flush()
            zip.closeEntry()
        }

        entry("[Content_Types].xml") { it.write(contentTypes(specs.size)) }
        entry("_rels/.rels") { it.write(ROOT_RELS) }
        entry("xl/workbook.xml") { it.write(workbook(specs)) }
        entry("xl/_rels/workbook.xml.rels") { it.write(workbookRels(specs.size)) }
        entry("xl/styles.xml") { it.write(STYLES) }

        val strings = SharedStrings()
        specs.forEachIndexed { i, spec ->
            entry("xl/worksheets/sheet${i + 1}.xml") { w ->
                if (spec.table == null) writeSummarySheet(w, report, strings) else writeTableSheet(w, report, spec.table, strings)
            }
        }
        entry("xl/sharedStrings.xml") { strings.writeTo(it) }
        zip.finish()
        zip.flush()
    }

    private fun writeSummarySheet(w: BufferedWriter, report: Report, strings: SharedStrings) {
        w.write(SHEET_START)
        w.write("""<cols><col min="1" max="1" width="42" customWidth="1"/><col min="2" max="2" width="40" customWidth="1"/></cols><sheetData>""")
        var r = 1
        fun textRow(label: String, value: String?, labelStyle: Int, valueStyle: Int = 0) {
            w.write("""<row r="$r">""")
            w.write(stringCell("A$r", label, labelStyle, strings))
            if (value != null) w.write(stringCell("B$r", value, valueStyle, strings))
            w.write("</row>")
            r++
        }
        textRow("JewelryManagement - ${report.title}", null, STYLE_TITLE)
        textRow("Kỳ báo cáo", "${DateUtils.dayToDisplay(report.startDay)} - ${DateUtils.dayToDisplay(report.endDay)}", STYLE_LABEL)
        textRow("Trạng thái đơn hàng", report.statusLabel, STYLE_LABEL)
        textRow("Ngày lập", DateUtils.toDisplay(report.generatedAt), STYLE_LABEL)
        textRow("Đơn vị tiền tệ", "VNĐ", STYLE_LABEL)
        r++
        if (report.summary.isEmpty() && report.isEmpty) textRow("Không có dữ liệu trong khoảng thời gian đã chọn.", null, 0)
        report.summary.forEach { (label, value) -> textRow(label.trim(), value, STYLE_LABEL) }
        w.write("</sheetData></worksheet>")
    }

    private fun writeTableSheet(w: BufferedWriter, report: Report, table: ReportTable, strings: SharedStrings) {
        val colCount = table.columns.size
        w.write(SHEET_START)
        w.write("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="$HEADER_ROW" topLeftCell="A${HEADER_ROW + 1}" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        w.write("<cols>")
        for (col in 0 until colCount) {
            val longest = (listOf(table.columns[col].header) + table.rows.take(500).map { it.getOrNull(col)?.display().orEmpty() })
                .maxOf { it.length }
            val width = (longest + 3).coerceIn(10, 60)
            w.write("""<col min="${col + 1}" max="${col + 1}" width="$width" customWidth="1"/>""")
        }
        w.write("</cols><sheetData>")

        w.write("""<row r="1">${stringCell("A1", "JewelryManagement - ${table.title}", STYLE_TITLE, strings)}</row>""")
        val meta = "Kỳ: ${DateUtils.dayToDisplay(report.startDay)} - ${DateUtils.dayToDisplay(report.endDay)} | Trạng thái: ${report.statusLabel} | Lập lúc: ${DateUtils.toDisplay(report.generatedAt)}"
        w.write("""<row r="2">${stringCell("A2", meta, 0, strings)}</row>""")

        w.write("""<row r="$HEADER_ROW">""")
        table.columns.forEachIndexed { c, column -> w.write(stringCell("${col(c)}$HEADER_ROW", column.header, STYLE_HEADER, strings)) }
        w.write("</row>")

        var r = HEADER_ROW + 1
        if (table.rows.isEmpty()) {
            w.write("""<row r="$r">${stringCell("A$r", "Không có dữ liệu", STYLE_TEXT, strings)}</row>""")
            r++
        }
        for (row in table.rows) {
            w.write("""<row r="$r">""")
            row.forEachIndexed { c, cell -> w.write(cell("${col(c)}$r", cell, total = false, strings = strings)) }
            w.write("</row>")
            r++
        }
        table.totals?.let { totals ->
            w.write("""<row r="$r">""")
            totals.forEachIndexed { c, cell -> w.write(cell("${col(c)}$r", cell, total = true, strings = strings)) }
            w.write("</row>")
            r++
        }
        table.note?.let {
            r++
            w.write("""<row r="$r">${stringCell("A$r", "Ghi chú: $it", 0, strings)}</row>""")
        }
        w.write("</sheetData>")
        if (table.rows.isNotEmpty()) {
            w.write("""<autoFilter ref="A$HEADER_ROW:${col(colCount - 1)}${HEADER_ROW + table.rows.size}"/>""")
        }
        w.write("</worksheet>")
    }

    private fun cell(ref: String, cell: ReportCell, total: Boolean, strings: SharedStrings): String = when (cell) {
        is ReportCell.Text -> stringCell(ref, cell.value, if (total) STYLE_TOTAL_TEXT else STYLE_TEXT, strings)
        is ReportCell.Money -> numberCell(ref, plain(cell.value, 0), if (total) STYLE_TOTAL_MONEY else STYLE_MONEY)
        is ReportCell.Count -> numberCell(ref, cell.value.toString(), if (total) STYLE_TOTAL_INT else STYLE_INT)
        is ReportCell.Decimal -> numberCell(ref, plain(cell.value, 2), if (total) STYLE_TOTAL_DECIMAL else STYLE_DECIMAL)
        is ReportCell.DateValue -> {
            val serial = excelSerial(cell.raw)
            when {
                serial == null -> stringCell(ref, cell.raw, if (total) STYLE_TOTAL_TEXT else STYLE_TEXT, strings)
                cell.raw.length <= 10 -> numberCell(ref, plain(serial, 0), STYLE_DATE)
                else -> numberCell(ref, plain(serial, 6), STYLE_DATETIME)
            }
        }
    }

    private fun stringCell(ref: String, value: String, style: Int, strings: SharedStrings): String {
        val s = if (style != 0) """ s="$style"""" else ""
        return """<c r="$ref" t="s"$s><v>${strings.index(value)}</v></c>"""
    }

    private fun numberCell(ref: String, value: String, style: Int) = """<c r="$ref" s="$style"><v>$value</v></c>"""

    /** Non-scientific decimal text, e.g. 1.5E7 -> "15000000". */
    private fun plain(value: Double, scale: Int): String =
        if (value.isNaN() || value.isInfinite()) "0"
        else BigDecimal(value).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    /** Local date/time -> Excel serial (days since 1899-12-30). */
    private fun excelSerial(raw: String): Double? {
        val pattern = if (raw.length <= 10) "yyyy-MM-dd" else DateUtils.DB_PATTERN
        val date = try {
            SimpleDateFormat(pattern, Locale.US).parse(raw)
        } catch (e: Exception) {
            null
        } ?: return null
        val localMillis = date.time + TimeZone.getDefault().getOffset(date.time)
        return localMillis / 86_400_000.0 + 25569.0
    }

    private fun col(index: Int): String {
        var n = index
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + (n % 26)))
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    private fun uniqueName(raw: String, used: MutableSet<String>): String {
        val base = raw.replace(Regex("[\\[\\]:*?/\\\\]"), "_").take(28).ifBlank { "Sheet" }
        var name = base
        var i = 2
        while (!used.add(name.lowercase())) name = "${base}_${i++}"
        return name
    }

    private class SharedStrings {
        private val map = LinkedHashMap<String, Int>()
        private var count = 0

        fun index(s: String): Int {
            count++
            return map.getOrPut(s) { map.size }
        }

        fun writeTo(w: BufferedWriter) {
            w.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            w.write("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="$count" uniqueCount="${map.size}">""")
            for (s in map.keys) w.write("""<si><t xml:space="preserve">${esc(s)}</t></si>""")
            w.write("</sst>")
        }
    }

    private fun esc(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch.code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private const val SHEET_START =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private fun contentTypes(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        append("""<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>""")
        for (i in 1..sheetCount) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun workbook(specs: List<SheetSpec>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        specs.forEachIndexed { i, s -> append("""<sheet name="${esc(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
        append("</sheets>")
        val filters = specs.withIndex().filter { (_, s) -> s.table != null && s.table.rows.isNotEmpty() }
        if (filters.isNotEmpty()) {
            append("<definedNames>")
            filters.forEach { (i, s) ->
                val t = s.table!!
                append("""<definedName name="_xlnm._FilterDatabase" localSheetId="$i" hidden="1">'${esc(s.name)}'!${'$'}A${'$'}$HEADER_ROW:${'$'}${col(t.columns.size - 1)}${'$'}${HEADER_ROW + t.rows.size}</definedName>""")
            }
            append("</definedNames>")
        }
        append("</workbook>")
    }

    private fun workbookRels(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>""")
        append("""<Relationship Id="rId${sheetCount + 2}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="3"><numFmt numFmtId="164" formatCode="#,##0 &quot;₫&quot;"/><numFmt numFmtId="165" formatCode="dd/mm/yyyy hh:mm"/><numFmt numFmtId="166" formatCode="dd/mm/yyyy"/></numFmts>
<fonts count="3"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="14"/><color rgb="FF7C5A04"/><name val="Calibri"/></font></fonts>
<fills count="4"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFF5EACB"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFEBDEB4"/><bgColor indexed="64"/></patternFill></fill></fills>
<borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style="thin"><color rgb="FFD2C3A0"/></left><right style="thin"><color rgb="FFD2C3A0"/></right><top style="thin"><color rgb="FFD2C3A0"/></top><bottom style="thin"><color rgb="FFD2C3A0"/></bottom><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="14">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>
<xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1"/>
<xf numFmtId="3" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1"/>
<xf numFmtId="165" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1"/>
<xf numFmtId="166" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1"/>
<xf numFmtId="2" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1"/>
<xf numFmtId="0" fontId="1" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>
<xf numFmtId="164" fontId="1" fillId="3" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1"/>
<xf numFmtId="3" fontId="1" fillId="3" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1"/>
<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
<xf numFmtId="2" fontId="1" fillId="3" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1"/>
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
</cellXfs>
</styleSheet>"""
}
