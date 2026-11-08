package com.example.jewelrymanagement

import com.example.jewelrymanagement.repositories.Report
import com.example.jewelrymanagement.repositories.ReportCell
import com.example.jewelrymanagement.repositories.ReportColumn
import com.example.jewelrymanagement.repositories.ReportTable
import com.example.jewelrymanagement.repositories.ReportType
import com.example.jewelrymanagement.utils.XlsxExporter
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class XlsxExporterTest {

    private fun sampleReport(rows: Int) = Report(
        type = ReportType.REVENUE,
        startDay = "2026-10-01",
        endDay = "2026-10-31",
        statusLabel = "Đã giao hàng",
        generatedAt = "2026-10-30 12:00:00",
        summary = listOf("Tổng doanh thu" to "1.500.000 ₫", "Ghi chú <&>" to "\"ký tự đặc biệt\""),
        tables = listOf(
            ReportTable(
                title = "Doanh thu theo ngày",
                sheetName = "DoanhThu",
                columns = listOf(ReportColumn("Ngày", 1f), ReportColumn("Số đơn", 1f), ReportColumn("Doanh thu", 1f)),
                rows = (1..rows).map {
                    listOf(ReportCell.DateValue("2026-10-%02d".format((it % 28) + 1)), ReportCell.Count(it.toLong()), ReportCell.Money(1_500_000.0))
                },
                totals = listOf(ReportCell.Text("Tổng"), ReportCell.Count(rows.toLong()), ReportCell.Money(1_500_000.0 * rows))
            )
        )
    )

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                entries[e.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return entries
    }

    @Test
    fun writesWellFormedWorkbookWithNumericMoney() {
        val out = ByteArrayOutputStream()
        XlsxExporter.write(sampleReport(rows = 1200), out)
        val entries = unzip(out.toByteArray())

        listOf(
            "[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels",
            "xl/styles.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml", "xl/sharedStrings.xml"
        ).forEach { assertTrue("missing $it", entries.containsKey(it)) }

        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        entries.forEach { (name, xml) ->
            factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
                ?: error("cannot parse $name")
        }

        val sheet = entries.getValue("xl/worksheets/sheet2.xml")
        assertTrue("money must be a number cell", sheet.contains("<v>1500000</v>"))
        assertTrue("dates must be numeric serials", Regex("""<c r="A5" s="\d+"><v>\d+(\.\d+)?</v></c>""").containsMatchIn(sheet))
        assertTrue(entries.getValue("xl/sharedStrings.xml").contains("Ghi chú &lt;&amp;&gt;"))
    }
}
