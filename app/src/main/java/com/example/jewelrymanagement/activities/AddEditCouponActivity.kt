package com.example.jewelrymanagement.activities

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.jewelrymanagement.database.CouponDao
import com.example.jewelrymanagement.databinding.ActivityAddEditCouponBinding
import com.example.jewelrymanagement.models.Coupon
import com.example.jewelrymanagement.utils.DateUtils
import java.util.Calendar
import java.util.Date

class AddEditCouponActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_COUPON_ID = "EXTRA_COUPON_ID"
        private const val STATE_START = "state_start"
        private const val STATE_END = "state_end"
        private val CODE_REGEX = Regex("^[A-Z0-9_-]{3,30}$")
    }

    private lateinit var binding: ActivityAddEditCouponBinding
    private lateinit var couponDao: CouponDao

    private var couponId: Long = 0
    private var editingCoupon: Coupon? = null

    private var startDateStr = ""
    private var endDateStr = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditCouponBinding.inflate(layoutInflater)
        setContentView(binding.root)

        couponDao = CouponDao(this)
        couponId = intent.getLongExtra(EXTRA_COUPON_ID, 0)

        binding.toolbar.setNavigationOnClickListener { finish() }
        setupDatePickers()
        binding.btnSaveCoupon.setOnClickListener { save() }

        if (couponId > 0) {
            binding.toolbar.title = "Chỉnh sửa mã giảm giá"
            if (!loadCouponData()) {
                Toast.makeText(this, "Không tìm thấy mã giảm giá!", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        } else {
            val cal = Calendar.getInstance()
            cal.set(Calendar.SECOND, 0)
            startDateStr = DateUtils.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, 30)
            endDateStr = DateUtils.format(cal.time)
            binding.etUsageLimit.setText("100")
            binding.etMinOrder.setText("0")
            binding.switchActive.isChecked = true
        }

        savedInstanceState?.let {
            startDateStr = it.getString(STATE_START, startDateStr)
            endDateStr = it.getString(STATE_END, endDateStr)
        }
        renderDates()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_START, startDateStr)
        outState.putString(STATE_END, endDateStr)
    }

    private fun renderDates() {
        binding.btnPickStartDate.text = "Từ: ${DateUtils.toDisplay(startDateStr)}"
        binding.btnPickEndDate.text = "Đến: ${DateUtils.toDisplay(endDateStr)}"
    }

    private fun setupDatePickers() {
        binding.btnPickStartDate.setOnClickListener {
            pickDateTime(startDateStr) {
                startDateStr = it
                renderDates()
            }
        }
        binding.btnPickEndDate.setOnClickListener {
            pickDateTime(endDateStr) {
                endDateStr = it
                renderDates()
            }
        }
    }

    private fun pickDateTime(current: String, onPicked: (String) -> Unit) {
        val cal = Calendar.getInstance()
        DateUtils.parse(current)?.let { cal.time = it }
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                cal.set(year, month, dayOfMonth)
                TimePickerDialog(
                    this,
                    { _, hourOfDay, minute ->
                        cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        cal.set(Calendar.MINUTE, minute)
                        cal.set(Calendar.SECOND, 0)
                        onPicked(DateUtils.format(cal.time))
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadCouponData(): Boolean {
        val coupon = couponDao.getCouponById(couponId) ?: return false
        editingCoupon = coupon

        binding.etCouponCode.setText(coupon.code)
        binding.etCouponTitle.setText(coupon.title)
        if (coupon.discountType == "PERCENTAGE") binding.rbPercentage.isChecked = true
        else binding.rbFixedAmount.isChecked = true

        binding.etDiscountValue.setText(plain(coupon.discountValue))
        binding.etMinOrder.setText(plain(coupon.minOrder))
        coupon.maxDiscount?.let { binding.etMaxDiscount.setText(plain(it)) }
        binding.etUsageLimit.setText(coupon.usageLimit.toString())
        startDateStr = coupon.startDate
        endDateStr = coupon.endDate
        binding.switchActive.isChecked = coupon.isActive
        return true
    }

    private fun plain(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun save() {
        val code = binding.etCouponCode.text.toString().trim().uppercase()
        val title = binding.etCouponTitle.text.toString().trim()
        val discountVal = binding.etDiscountValue.text.toString().trim().toDoubleOrNull()
        val minOrderStr = binding.etMinOrder.text.toString().trim()
        val maxDiscStr = binding.etMaxDiscount.text.toString().trim()
        val limitStr = binding.etUsageLimit.text.toString().trim()
        val discountType = if (binding.rbPercentage.isChecked) "PERCENTAGE" else "FIXED_AMOUNT"

        if (!CODE_REGEX.matches(code)) {
            binding.etCouponCode.error = "Mã gồm 3–30 ký tự: chữ in hoa, số, '_' hoặc '-'"
            return
        }
        val existing = editingCoupon
        if (existing != null && existing.usedCount > 0 && code != existing.code) {
            binding.etCouponCode.error = "Mã đã được dùng trong đơn hàng nên không thể đổi tên"
            return
        }
        if (couponDao.isCodeExists(code, couponId)) {
            binding.etCouponCode.error = "Mã coupon '$code' đã tồn tại!"
            return
        }
        if (title.isBlank()) {
            binding.etCouponTitle.error = "Vui lòng nhập tên chương trình"
            return
        }
        if (discountVal == null || discountVal <= 0) {
            binding.etDiscountValue.error = "Giá trị giảm phải lớn hơn 0"
            return
        }
        if (discountType == "PERCENTAGE" && discountVal > 100) {
            binding.etDiscountValue.error = "Giảm giá phần trăm không được vượt quá 100%"
            return
        }
        val minOrder = if (minOrderStr.isEmpty()) 0.0 else minOrderStr.toDoubleOrNull()
        if (minOrder == null || minOrder < 0) {
            binding.etMinOrder.error = "Đơn tối thiểu không hợp lệ"
            return
        }
        val maxDiscount = if (maxDiscStr.isEmpty()) null else maxDiscStr.toDoubleOrNull()
        if (maxDiscStr.isNotEmpty() && (maxDiscount == null || maxDiscount <= 0)) {
            binding.etMaxDiscount.error = "Mức giảm tối đa phải lớn hơn 0 hoặc để trống"
            return
        }
        val limit = limitStr.toIntOrNull()
        if (limit == null || limit <= 0) {
            binding.etUsageLimit.error = "Số lượt sử dụng phải là số nguyên dương"
            return
        }
        val usedCount = editingCoupon?.usedCount ?: 0
        if (limit < usedCount) {
            binding.etUsageLimit.error = "Đã có $usedCount lượt dùng, giới hạn không được nhỏ hơn"
            return
        }

        val start = DateUtils.parse(startDateStr)
        val end = DateUtils.parse(endDateStr)
        if (start == null || end == null) {
            Toast.makeText(this, "Vui lòng chọn thời gian bắt đầu và kết thúc!", Toast.LENGTH_SHORT).show()
            return
        }
        if (!end.after(start)) {
            Toast.makeText(this, "Thời gian kết thúc phải sau thời gian bắt đầu!", Toast.LENGTH_LONG).show()
            return
        }
        if (couponId == 0L && !end.after(Date())) {
            Toast.makeText(this, "Thời gian kết thúc phải ở tương lai!", Toast.LENGTH_LONG).show()
            return
        }

        val coupon = Coupon(
            id = couponId,
            code = code,
            title = title,
            discountType = discountType,
            discountValue = discountVal,
            minOrder = minOrder,
            maxDiscount = if (discountType == "PERCENTAGE") maxDiscount else null,
            startDate = startDateStr,
            endDate = endDateStr,
            usageLimit = limit,
            usedCount = usedCount,
            isActive = binding.switchActive.isChecked
        )

        val ok = if (couponId > 0) couponDao.update(coupon) else couponDao.insert(coupon) > 0
        if (ok) {
            Toast.makeText(
                this,
                if (couponId > 0) "Cập nhật mã giảm giá thành công!" else "Tạo mã giảm giá thành công!",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        } else {
            Toast.makeText(this, "Không lưu được mã giảm giá, vui lòng thử lại!", Toast.LENGTH_SHORT).show()
        }
    }
}
