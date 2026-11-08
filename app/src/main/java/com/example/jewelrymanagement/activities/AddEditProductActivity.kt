package com.example.jewelrymanagement.activities

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.jewelrymanagement.R
import com.example.jewelrymanagement.database.CategoryDao
import com.example.jewelrymanagement.database.ProductDao
import com.example.jewelrymanagement.databinding.ActivityAddEditProductBinding
import com.example.jewelrymanagement.models.Category
import com.example.jewelrymanagement.models.Product
import com.example.jewelrymanagement.utils.ProductImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddEditProductActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditProductBinding
    private lateinit var productDao: ProductDao
    private lateinit var categoryDao: CategoryDao

    private var editingProduct: Product? = null
    private var categories = listOf<Category>()

    private val availableImages = listOf(
        Pair("Nhẫn Kim Cương Solitaire", "jw_ring_solitaire"),
        Pair("Nhẫn Đá Ruby", "jw_ring_ruby"),
        Pair("Nhẫn Cặp", "jw_rings_couple"),
        Pair("Dây Chuyền Vàng Ý", "jw_necklace_italy"),
        Pair("Dây Chuyền Mắt Xích", "jw_chain_men"),
        Pair("Mặt Dây Sapphire", "jw_pendant_sapphire"),
        Pair("Lắc Tay Bạc", "jw_bracelet_silver"),
        Pair("Vòng Tay Vàng", "jw_bangle_gold"),
        Pair("Kiềng Vàng", "jw_kieng_gold"),
        Pair("Bông Tai Ngọc Trai", "jw_earrings_pearl"),
        Pair("Bông Tai Nụ", "jw_earrings_stud"),
        Pair("Lắc Chân Bạc", "jw_anklet_silver"),
        Pair("Trang Sức Chung", "img_jewelry_placeholder")
    )

    /** Preset images, preceded by the phone photo (picked now or already saved on the product) if any. */
    private var imageOptions = listOf<Pair<String, String>>()

    /** Photo copied into app storage during this screen but not saved to the product yet. */
    private var pickedImage: String? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) importPickedImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditProductBinding.inflate(layoutInflater)
        setContentView(binding.root)
        pickedImage = savedInstanceState?.getString(STATE_PICKED_IMAGE)

        productDao = ProductDao(this)
        categoryDao = CategoryDao(this)

        val passedProduct = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("PRODUCT_EXTRA", Product::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("PRODUCT_EXTRA") as? Product
        }
        editingProduct = passedProduct?.let { productDao.getById(it.id) ?: it }

        setupSpinners()
        populateDataIfEditing()
        setupSwipeRefresh()

        binding.btnBackAddEdit.setOnClickListener { finish() }
        binding.btnSaveProduct.setOnClickListener { saveProduct() }
        binding.btnResetProductForm.setOnClickListener { resetForm() }
        binding.btnTopResetAddEdit.setOnClickListener { resetForm() }
        binding.btnPickImage.setOnClickListener {
            pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PICKED_IMAGE, pickedImage)
    }

    override fun onDestroy() {
        if (isFinishing) discardPickedImage()
        super.onDestroy()
    }

    private fun importPickedImage(uri: Uri) {
        binding.btnPickImage.isEnabled = false
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) { ProductImageUtils.saveFromUri(applicationContext, uri) }
            binding.btnPickImage.isEnabled = true
            if (saved == null) {
                Toast.makeText(this@AddEditProductActivity, "Không đọc được ảnh, vui lòng chọn ảnh khác!", Toast.LENGTH_SHORT).show()
                return@launch
            }
            discardPickedImage()
            pickedImage = saved
            setupImageOptions()
            binding.spProdImage.setSelection(0)
            ProductImageUtils.load(binding.imgProdPreview, saved)
        }
    }

    private fun discardPickedImage() {
        ProductImageUtils.delete(applicationContext, pickedImage)
        pickedImage = null
    }

    private fun setupImageOptions() {
        val phoneImage = pickedImage ?: editingProduct?.imageName?.takeIf { ProductImageUtils.isLocalFile(it) }
        imageOptions = if (phoneImage != null) listOf(Pair("Ảnh chọn từ điện thoại", phoneImage)) + availableImages else availableImages
        val imgAdapter = ArrayAdapter(this, R.layout.item_spinner, imageOptions.map { it.first })
        imgAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        binding.spProdImage.adapter = imgAdapter
        binding.spProdImage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                ProductImageUtils.load(binding.imgProdPreview, imageOptions[position].second)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshAddEdit.setColorSchemeColors(
            androidx.core.content.ContextCompat.getColor(this, R.color.gold_primary),
            androidx.core.content.ContextCompat.getColor(this, R.color.gold_primary_dark)
        )
        binding.swipeRefreshAddEdit.setOnRefreshListener {
            setupSpinners()
            populateDataIfEditing()
            binding.swipeRefreshAddEdit.isRefreshing = false
            Toast.makeText(this, "Đã làm mới thông tin sản phẩm!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetForm() {
        binding.tilProdName.error = null
        binding.tilProdPrice.error = null
        binding.tilProdQuantity.error = null
        discardPickedImage()
        setupImageOptions()

        if (editingProduct != null) {
            populateDataIfEditing()
            Toast.makeText(this, "Đã khôi phục dữ liệu ban đầu!", Toast.LENGTH_SHORT).show()
        } else {
            binding.etProdName.setText("")
            binding.etProdPrice.setText("")
            binding.etProdQuantity.setText("")
            binding.etProdMaterial.setText("")
            binding.etProdWeight.setText("")
            binding.etProdDescription.setText("")
            binding.etProdGemType.setText("")
            binding.etProdPurity.setText("")
            binding.etProdSize.setText("")
            binding.etProdWarranty.setText("")
            if (categories.isNotEmpty()) {
                binding.spProdCategory.setSelection(0)
            }
            binding.spProdImage.setSelection(0)
            Toast.makeText(this, "Đã làm mới biểu mẫu!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSpinners() {
        // Load categories
        categories = categoryDao.getAll()
        val catAdapter = ArrayAdapter(this, R.layout.item_spinner, categories)
        catAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        binding.spProdCategory.adapter = catAdapter

        setupImageOptions()
        if (pickedImage != null) binding.spProdImage.setSelection(0)
    }

    private fun populateDataIfEditing() {
        val prod = editingProduct ?: return

        binding.tvAddEditTitle.text = "Chỉnh Sửa Sản Phẩm"
        binding.etProdName.setText(prod.name)
        binding.etProdPrice.setText(prod.price.toLong().toString())
        binding.etProdQuantity.setText(prod.quantity.toString())
        binding.etProdMaterial.setText(prod.material)
        binding.etProdWeight.setText(prod.weight)
        binding.etProdDescription.setText(prod.description)
        binding.etProdGemType.setText(prod.gemType)
        binding.etProdPurity.setText(prod.purity)
        binding.etProdSize.setText(prod.size)
        binding.etProdWarranty.setText(prod.warranty)

        // Select category
        val catIndex = categories.indexOfFirst { it.id == prod.categoryId }
        if (catIndex >= 0) {
            binding.spProdCategory.setSelection(catIndex)
        }

        // Select image
        val currentImage = pickedImage ?: prod.imageName
        val imgIndex = imageOptions.indexOfFirst { it.second == currentImage }
        if (imgIndex >= 0) {
            binding.spProdImage.setSelection(imgIndex)
        }
    }

    private fun saveProduct() {
        val name = binding.etProdName.text?.toString()?.trim() ?: ""
        val priceStr = binding.etProdPrice.text?.toString()?.trim() ?: ""
        val quantityStr = binding.etProdQuantity.text?.toString()?.trim() ?: ""
        val material = binding.etProdMaterial.text?.toString()?.trim() ?: ""
        val weight = binding.etProdWeight.text?.toString()?.trim() ?: ""
        val description = binding.etProdDescription.text?.toString()?.trim() ?: ""
        val gemType = binding.etProdGemType.text?.toString()?.trim() ?: ""
        val purity = binding.etProdPurity.text?.toString()?.trim() ?: ""
        val size = binding.etProdSize.text?.toString()?.trim() ?: ""
        val warranty = binding.etProdWarranty.text?.toString()?.trim() ?: ""

        binding.tilProdName.error = null
        binding.tilProdPrice.error = null
        binding.tilProdQuantity.error = null

        var hasError = false

        if (name.isEmpty()) {
            binding.tilProdName.error = "Vui lòng nhập tên sản phẩm"
            hasError = true
        }

        val price = priceStr.toDoubleOrNull()
        if (price == null || price <= 0) {
            binding.tilProdPrice.error = "Giá bán phải lớn hơn 0"
            hasError = true
        }

        val quantity = quantityStr.toIntOrNull()
        if (quantity == null || quantity < 0) {
            binding.tilProdQuantity.error = "Số lượng tồn kho không được âm"
            hasError = true
        }

        if (categories.isEmpty()) {
            Toast.makeText(this, "Chưa có danh mục nào, vui lòng tạo danh mục trước!", Toast.LENGTH_SHORT).show()
            return
        }

        if (hasError) return

        val selectedCategory = categories[binding.spProdCategory.selectedItemPosition]
        val selectedImage = imageOptions[binding.spProdImage.selectedItemPosition].second

        if (editingProduct != null) {
            val updated = editingProduct!!.copy(
                name = name,
                price = price!!,
                quantity = quantity!!,
                categoryId = selectedCategory.id,
                material = material,
                weight = weight,
                description = description,
                imageName = selectedImage,
                gemType = gemType,
                purity = purity,
                size = size,
                warranty = warranty
            )
            val success = productDao.update(updated)
            if (success) {
                val oldImage = editingProduct!!.imageName
                if (oldImage != selectedImage) ProductImageUtils.delete(applicationContext, oldImage)
                commitPickedImage(selectedImage)
                Toast.makeText(this, "Cập nhật sản phẩm thành công!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Cập nhật thất bại!", Toast.LENGTH_SHORT).show()
            }
        } else {
            val newProduct = Product(
                name = name,
                price = price!!,
                quantity = quantity!!,
                categoryId = selectedCategory.id,
                material = material,
                weight = weight,
                description = description,
                imageName = selectedImage,
                gemType = gemType,
                purity = purity,
                size = size,
                warranty = warranty
            )
            val id = productDao.insert(newProduct)
            if (id > 0) {
                commitPickedImage(selectedImage)
                Toast.makeText(this, "Thêm sản phẩm thành công!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Thêm sản phẩm thất bại!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Keeps the picked photo if the product now uses it; otherwise it is unused and gets deleted. */
    private fun commitPickedImage(savedImage: String) {
        if (pickedImage == savedImage) pickedImage = null else discardPickedImage()
    }

    companion object {
        private const val STATE_PICKED_IMAGE = "picked_image"
    }
}
