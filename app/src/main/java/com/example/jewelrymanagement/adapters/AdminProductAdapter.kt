package com.example.jewelrymanagement.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.jewelrymanagement.R
import com.example.jewelrymanagement.databinding.ItemProductAdminBinding
import com.example.jewelrymanagement.models.Product
import com.example.jewelrymanagement.utils.CurrencyUtils
import com.example.jewelrymanagement.utils.ProductImageUtils

class AdminProductAdapter(
    private var products: List<Product>,
    private val onEditClick: (Product) -> Unit,
    private val onDeleteClick: (Product) -> Unit,
    private val onItemClick: (Product) -> Unit
) : RecyclerView.Adapter<AdminProductAdapter.AdminProductViewHolder>() {

    fun updateData(newProducts: List<Product>) {
        products = newProducts
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminProductViewHolder {
        val binding = ItemProductAdminBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AdminProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AdminProductViewHolder, position: Int) {
        holder.bind(products[position])
    }

    override fun getItemCount(): Int = products.size

    inner class AdminProductViewHolder(private val binding: ItemProductAdminBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product) {
            val context = binding.root.context
            binding.tvAdminProductName.text = product.name
            binding.tvAdminCategory.text = if (product.categoryName.isNotBlank()) product.categoryName else "Danh mục #${product.categoryId}"
            binding.tvAdminProductPrice.text = CurrencyUtils.formatVnd(product.price)
            binding.tvAdminMaterial.text = product.material

            when {
                product.quantity <= 0 -> {
                    binding.tvAdminStock.text = "Hết hàng (0)"
                    binding.tvAdminStock.setTextColor(ContextCompat.getColor(context, R.color.stock_out))
                }
                product.quantity <= 5 -> {
                    binding.tvAdminStock.text = "Cảnh báo: Tồn ${product.quantity}"
                    binding.tvAdminStock.setTextColor(ContextCompat.getColor(context, R.color.stock_low))
                }
                else -> {
                    binding.tvAdminStock.text = "Tồn kho: ${product.quantity}"
                    binding.tvAdminStock.setTextColor(ContextCompat.getColor(context, R.color.stock_in))
                }
            }

            ProductImageUtils.load(binding.imgProductAdmin, product.imageName)

            binding.root.setOnClickListener { onItemClick(product) }
            binding.btnAdminEditProduct.setOnClickListener { onEditClick(product) }
            binding.btnAdminDeleteProduct.setOnClickListener { onDeleteClick(product) }
        }
    }
}
