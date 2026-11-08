package com.example.jewelrymanagement.activities

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.jewelrymanagement.database.CategoryDao
import com.example.jewelrymanagement.database.ProductDao
import com.example.jewelrymanagement.database.PromotionDao
import com.example.jewelrymanagement.databinding.ActivityAddEditPromotionBinding
import com.example.jewelrymanagement.models.Category
import com.example.jewelrymanagement.models.Product
import com.example.jewelrymanagement.models.Promotion
import com.example.jewelrymanagement.utils.DateUtils
import java.util.Calendar
import java.util.Date

class AddEditPromotionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROMO_ID = "EXTRA_PROMO_ID"
    }

    private lateinit var binding: ActivityAddEditPromotionBinding
    private lateinit var promotionDao: PromotionDao

    private var promoId: Long = 0
    private var startDateStr = ""
    private var endDateStr = ""

    private var categories: List<Category> = emptyList()
    private var products: List<Product> = emptyList()
    private val selectedCategoryIds = linkedSetOf<Long>()
    private val selectedProductIds = linkedSetOf<Long>()

    private val targetTypes = listOf(Promotion.TARGET_ALL, Promotion.TARGET_CATEGORY, Promotion.TARGET_PRODUCT)
    private val targetLabels = listOf("Toàn bộ cửa hàng", "Theo danh mục", "Theo sản phẩm cụ thể")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditPromotionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        promotionDao = PromotionDao(this)
        categories = CategoryDao(this).getAllCategories()
        promoId = intent.getLongExtra(EXTRA_PROMO_ID, 0)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.spTargetType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, targetLabels)

        if (promoId > 0) {
            val promo = promotionDao.getPromotionById(promoId)
            if (promo == null) {
                Toast.makeText(this, "Không tìm thấy sự kiện khuyến mãi!", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            binding.toolbar.title = "Chỉnh sửa sự kiện"
            bindPromotion(promo)
        } else {
            val cal = Calendar.getInstance()
            startDateStr = DateUtils.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, 30)
            endDateStr = DateUtils.format(cal.time)
        }
        products = ProductDao(this).getAllProducts().filter { !it.isDiscontinued || it.id in selectedProductIds }

        updateDateButtons()
        binding.spTargetType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = updateTargetSection()
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        updateTargetSection()
        binding.btnPickTargets.setOnClickListener { pickTargets() }
        binding.btnPickStartDate.setOnClickListener { pickDateTime(startDateStr) { startDateStr = it; updateDateButtons() } }
        binding.btnPickEndDate.setOnClickListener { pickDateTime(endDateStr) { endDateStr = it; updateDateButtons() } }
        binding.btnSavePromotion.setOnClickListener { save() }
    }

    private fun bindPromotion(promo: Promotion) {
        binding.etPromoCode.setText(promo.code)
        binding.etPromoName.setText(promo.name)
        binding.etPromoDesc.setText(promo.description)
        if (promo.discountType == "PERCENTAGE") binding.rbPercentage.isChecked = true else binding.rbFixedAmount.isChecked = true
        val v = promo.discountValue
        binding.etDiscountValue.setText(if (v % 1.0 == 0.0) v.toLong().toString() else v.toString())
        startDateStr = promo.startDate
        endDateStr = promo.endDate
        binding.switchActive.isChecked = promo.isActive
        selectedCategoryIds.addAll(promo.targetCategoryIds)
        selectedProductIds.addAll(promo.targetProductIds)
        binding.spTargetType.setSelection(targetTypes.indexOf(promo.targetType).coerceAtLeast(0), false)
    }

    private fun currentTargetType(): String = targetTypes.getOrElse(binding.spTargetType.selectedItemPosition) { Promotion.TARGET_ALL }

    /** Switching type only changes what is shown; the selections of each type are kept. */
    private fun updateTargetSection() {
        when (currentTargetType()) {
            Promotion.TARGET_CATEGORY -> {
                binding.layoutTargetItem.visibility = View.VISIBLE
                binding.tvTargetItemLabel.text = "Danh mục áp dụng:"
                binding.btnPickTargets.text = "Chọn danh mục"
                binding.tvSelectedTargets.text = categories.filter { it.id in selectedCategoryIds }
                    .joinToString(", ") { it.name }.ifBlank { "Chưa chọn danh mục nào" }
            }
            Promotion.TARGET_PRODUCT -> {
                binding.layoutTargetItem.visibility = View.VISIBLE
                binding.tvTargetItemLabel.text = "Sản phẩm áp dụng:"
                binding.btnPickTargets.text = "Chọn sản phẩm"
                binding.tvSelectedTargets.text = products.filter { it.id in selectedProductIds }
                    .joinToString(", ") { it.name }.ifBlank { "Chưa chọn sản phẩm nào" }
            }
            else -> binding.layoutTargetItem.visibility = View.GONE
        }
    }

    private fun pickTargets() {
        val isCategory = currentTargetType() == Promotion.TARGET_CATEGORY
        val ids = if (isCategory) categories.map { it.id } else products.map { it.id }
        val names = if (isCategory) categories.map { it.name } else products.map { it.name }
        if (ids.isEmpty()) {
            Toast.makeText(this, if (isCategory) "Chưa có danh mục nào." else "Chưa có sản phẩm nào.", Toast.LENGTH_SHORT).show()
            return
        }
        val selected = if (isCategory) selectedCategoryIds else selectedProductIds
        val checked = BooleanArray(ids.size) { ids[it] in selected }
        AlertDialog.Builder(this)
            .setTitle(if (isCategory) "Chọn danh mục áp dụng" else "Chọn sản phẩm áp dụng")
            .setMultiChoiceItems(names.toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Xong") { _, _ ->
                selected.clear()
                ids.forEachIndexed { i, id -> if (checked[i]) selected.add(id) }
                updateTargetSection()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun updateDateButtons() {
        binding.btnPickStartDate.text = "Từ: ${DateUtils.toDisplay(startDateStr)}"
        binding.btnPickEndDate.text = "Đến: ${DateUtils.toDisplay(endDateStr)}"
    }

    private fun pickDateTime(current: String, onPicked: (String) -> Unit) {
        val cal = Calendar.getInstance()
        DateUtils.parse(current)?.let { cal.time = it }
        DatePickerDialog(this, { _, year, month, day ->
            cal.set(year, month, day)
            TimePickerDialog(this, { _, hour, minute ->
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                onPicked(DateUtils.format(cal.time))
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun save() {
        binding.tilPromoCode.error = null
        binding.tilPromoName.error = null
        binding.tilDiscountValue.error = null

        val code = binding.etPromoCode.text.toString().trim().uppercase()
        val name = binding.etPromoName.text.toString().trim()
        val desc = binding.etPromoDesc.text.toString().trim()
        val discountVal = binding.etDiscountValue.text.toString().trim().replace(",", ".").toDoubleOrNull()
        val discountType = if (binding.rbPercentage.isChecked) "PERCENTAGE" else "FIXED_AMOUNT"

        when {
            code.isBlank() -> { binding.tilPromoCode.error = "Vui lòng nhập mã sự kiện"; return }
            !code.matches(Regex("^[A-Z0-9_-]{3,30}$")) -> { binding.tilPromoCode.error = "Mã gồm 3–30 ký tự chữ, số, '_' hoặc '-'"; return }
            promotionDao.isCodeExists(code, promoId) -> { binding.tilPromoCode.error = "Mã sự kiện này đã tồn tại!"; return }
            name.isBlank() -> { binding.tilPromoName.error = "Vui lòng nhập tên chương trình"; return }
            discountVal == null || discountVal <= 0 -> { binding.tilDiscountValue.error = "Giá trị giảm phải lớn hơn 0"; return }
            discountType == "PERCENTAGE" && discountVal > 100 -> { binding.tilDiscountValue.error = "Giảm theo phần trăm không được vượt quá 100%"; return }
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
        if (promoId == 0L && end.before(Date())) {
            Toast.makeText(this, "Thời gian kết thúc đã qua, sự kiện mới sẽ không có hiệu lực!", Toast.LENGTH_LONG).show()
            return
        }

        val targetType = currentTargetType()
        if (targetType == Promotion.TARGET_CATEGORY && selectedCategoryIds.isEmpty()) {
            Toast.makeText(this, "Vui lòng chọn ít nhất một danh mục!", Toast.LENGTH_SHORT).show()
            return
        }
        if (targetType == Promotion.TARGET_PRODUCT && selectedProductIds.isEmpty()) {
            Toast.makeText(this, "Vui lòng chọn ít nhất một sản phẩm!", Toast.LENGTH_SHORT).show()
            return
        }

        val promo = Promotion(
            id = promoId,
            code = code,
            name = name,
            description = desc,
            discountType = discountType,
            discountValue = discountVal!!,
            startDate = startDateStr,
            endDate = endDateStr,
            targetType = targetType,
            targetCategoryIds = if (targetType == Promotion.TARGET_CATEGORY) selectedCategoryIds.toList() else emptyList(),
            targetProductIds = if (targetType == Promotion.TARGET_PRODUCT) selectedProductIds.toList() else emptyList(),
            isActive = binding.switchActive.isChecked
        )

        val ok = if (promoId > 0) promotionDao.update(promo) else promotionDao.insert(promo) > 0
        if (ok) {
            Toast.makeText(this, if (promoId > 0) "Cập nhật sự kiện thành công!" else "Thêm sự kiện thành công!", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, "Không thể lưu sự kiện, vui lòng thử lại!", Toast.LENGTH_SHORT).show()
        }
    }
}
