package com.example.jewelrymanagement.activities

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.jewelrymanagement.database.DatabaseHelper
import com.example.jewelrymanagement.databinding.ActivityExportReportBinding
import com.example.jewelrymanagement.repositories.Report
import com.example.jewelrymanagement.repositories.ReportRepository
import com.example.jewelrymanagement.repositories.ReportType
import com.example.jewelrymanagement.utils.DateUtils
import com.example.jewelrymanagement.utils.PdfReportWriter
import com.example.jewelrymanagement.utils.SessionManager
import com.example.jewelrymanagement.utils.XlsxExporter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ExportReportActivity : AppCompatActivity() {

    private companion object {
        const val MIME_PDF = "application/pdf"
        const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        const val STATE_START = "start"
        const val STATE_END = "end"
        const val STATE_FORMAT = "format"
    }

    private lateinit var binding: ActivityExportReportBinding
    private lateinit var reportRepository: ReportRepository
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private val reportTypes = ReportType.entries.toList()
    private val statusOptions = listOf(ReportRepository.STATUS_ALL) + DatabaseHelper.ORDER_STATUSES

    private var startDay = ""
    private var endDay = ""
    private var isPdf = true
    private var pendingReport: Report? = null

    private val createPdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument(MIME_PDF)) { onDocumentCreated(it) }
    private val createXlsxLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument(MIME_XLSX)) { onDocumentCreated(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExportReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!SessionManager(this).isAdmin()) {
            Toast.makeText(this, "Chỉ quản trị viên mới được xuất báo cáo!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        reportRepository = ReportRepository(this)

        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        endDay = savedInstanceState?.getString(STATE_END) ?: dayFormat.format(cal.time)
        cal.add(Calendar.MONTH, -1)
        startDay = savedInstanceState?.getString(STATE_START) ?: dayFormat.format(cal.time)
        isPdf = savedInstanceState?.getBoolean(STATE_FORMAT, true) ?: true

        binding.btnBackExport.setOnClickListener { finish() }
        binding.swipeRefreshExport.setOnRefreshListener { binding.swipeRefreshExport.isRefreshing = false }

        binding.spinnerReportType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, reportTypes.map { it.label })
        binding.spinnerOrderStatus.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            statusOptions.map { if (it == ReportRepository.STATUS_ALL) "Tất cả trạng thái" else it }
        )
        binding.spinnerReportType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val type = reportTypes[position]
                val usesStatus = type == ReportType.ORDERS || type == ReportType.PRODUCTS || type == ReportType.ALL
                binding.spinnerOrderStatus.isEnabled = usesStatus
                binding.spinnerOrderStatus.alpha = if (usesStatus) 1f else 0.5f
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.btnFormatPdf.setOnClickListener { isPdf = true; updateFormatButtons() }
        binding.btnFormatXlsx.setOnClickListener { isPdf = false; updateFormatButtons() }
        binding.btnPickStartDate.setOnClickListener { pickDate(true) }
        binding.btnPickEndDate.setOnClickListener { pickDate(false) }
        binding.btnExport.setOnClickListener { prepareReport() }

        updateDateLabels()
        updateFormatButtons()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_START, startDay)
        outState.putString(STATE_END, endDay)
        outState.putBoolean(STATE_FORMAT, isPdf)
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun updateFormatButtons() {
        binding.btnFormatPdf.isSelected = isPdf
        binding.btnFormatXlsx.isSelected = !isPdf
        binding.btnFormatPdf.alpha = if (isPdf) 1f else 0.55f
        binding.btnFormatXlsx.alpha = if (isPdf) 0.55f else 1f
    }

    private fun updateDateLabels() {
        binding.tvStartDate.text = DateUtils.dayToDisplay(startDay)
        binding.tvEndDate.text = DateUtils.dayToDisplay(endDay)
    }

    private fun pickDate(isStart: Boolean) {
        val cal = Calendar.getInstance()
        val current = if (isStart) startDay else endDay
        try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(current)?.let { cal.time = it }
        } catch (_: Exception) {
        }
        DatePickerDialog(this, { _, y, m, d ->
            val picked = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
            if (isStart) startDay = picked else endDay = picked
            updateDateLabels()
            if (startDay > endDay) Toast.makeText(this, "Ngày bắt đầu đang sau ngày kết thúc!", Toast.LENGTH_SHORT).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun setBusy(busy: Boolean) {
        binding.progressExport.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnExport.isEnabled = !busy
    }

    /** Step 1: query SQLite in the background, warn when there is no data, then ask where to save. */
    private fun prepareReport() {
        if (startDay > endDay) {
            Toast.makeText(this, "Ngày bắt đầu không được lớn hơn ngày kết thúc!", Toast.LENGTH_LONG).show()
            return
        }
        val type = reportTypes[binding.spinnerReportType.selectedItemPosition]
        val status = statusOptions[binding.spinnerOrderStatus.selectedItemPosition]
        setBusy(true)
        executor.execute {
            val result = runCatching { reportRepository.build(type, startDay, endDay, status) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setBusy(false)
                result.onFailure {
                    Toast.makeText(this, "Không thể lấy dữ liệu báo cáo: ${it.message}", Toast.LENGTH_LONG).show()
                }
                result.onSuccess { report ->
                    if (report.isEmpty) {
                        AlertDialog.Builder(this)
                            .setTitle("Không có dữ liệu")
                            .setMessage("Không có dữ liệu cho \"${type.label}\" từ ${DateUtils.dayToDisplay(startDay)} đến ${DateUtils.dayToDisplay(endDay)}. Bạn vẫn muốn xuất báo cáo trống?")
                            .setPositiveButton("Vẫn xuất") { _, _ -> askSaveLocation(report) }
                            .setNegativeButton("Hủy", null)
                            .show()
                    } else {
                        askSaveLocation(report)
                    }
                }
            }
        }
    }

    private fun askSaveLocation(report: Report) {
        pendingReport = report
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val fileName = "BaoCao_${report.type.fileSlug}_${report.startDay}_${report.endDay}_$stamp"
        try {
            if (isPdf) createPdfLauncher.launch("$fileName.pdf") else createXlsxLauncher.launch("$fileName.xlsx")
        } catch (e: ActivityNotFoundException) {
            pendingReport = null
            Toast.makeText(this, "Thiết bị không có ứng dụng quản lý tệp để lưu báo cáo.", Toast.LENGTH_LONG).show()
        }
    }

    /** Step 2: write the file chosen through the Storage Access Framework (no storage permission needed). */
    private fun onDocumentCreated(uri: Uri?) {
        if (uri == null) {
            pendingReport = null
            Toast.makeText(this, "Đã hủy lưu báo cáo.", Toast.LENGTH_SHORT).show()
            return
        }
        val report = pendingReport
        pendingReport = null
        if (report == null) {
            Toast.makeText(this, "Phiên xuất đã hết hạn, vui lòng bấm Xuất báo cáo lại.", Toast.LENGTH_LONG).show()
            deleteQuietly(uri)
            return
        }
        val pdf = isPdf
        setBusy(true)
        executor.execute {
            val result = runCatching {
                val stream = contentResolver.openOutputStream(uri, "wt") ?: error("Không mở được tệp để ghi")
                stream.use { if (pdf) PdfReportWriter.write(report, it) else XlsxExporter.write(report, it) }
            }
            if (result.isFailure) deleteQuietly(uri)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setBusy(false)
                result.onSuccess { showDoneDialog(uri, if (pdf) MIME_PDF else MIME_XLSX) }
                result.onFailure {
                    AlertDialog.Builder(this)
                        .setTitle("Xuất báo cáo thất bại")
                        .setMessage("Không thể ghi tệp báo cáo: ${it.message ?: it.javaClass.simpleName}")
                        .setPositiveButton("Đóng", null)
                        .show()
                }
            }
        }
    }

    private fun deleteQuietly(uri: Uri) {
        try {
            DocumentsContract.deleteDocument(contentResolver, uri)
        } catch (_: Exception) {
        }
    }

    private fun showDoneDialog(uri: Uri, mime: String) {
        AlertDialog.Builder(this)
            .setTitle("Xuất báo cáo thành công")
            .setMessage("Tệp báo cáo đã được lưu vào vị trí bạn chọn.")
            .setPositiveButton("Mở tệp") { _, _ ->
                val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    startActivity(view)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, "Không có ứng dụng nào để mở tệp ${if (mime == MIME_PDF) "PDF" else "Excel"}. Bạn có thể chia sẻ tệp sang ứng dụng khác.", Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton("Chia sẻ") { _, _ ->
                val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    startActivity(Intent.createChooser(send, "Chia sẻ báo cáo"))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, "Không có ứng dụng nào để chia sẻ tệp.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Đóng", null)
            .show()
    }
}
