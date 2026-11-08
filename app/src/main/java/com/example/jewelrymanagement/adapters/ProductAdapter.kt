package com.example.jewelrymanagement.adapters

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.jewelrymanagement.R
import com.example.jewelrymanagement.databinding.ItemProductBinding
import com.example.jewelrymanagement.models.Product
import com.example.jewelrymanagement.repositories.PriceInfo
import com.example.jewelrymanagement.utils.CurrencyUtils
import com.example.jewelrymanagement.utils.ProductImageUtils

class ProductAdapter(
    private var products: List<Product>,
    private val onItemClick: (Product) -> Unit,
    private val onQuickAddToCart: (Product) -> Unit,
    private val onFavoriteClick: ((Product) -> Unit)? = null
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    private var prices: Map<Long, PriceInfo> = emptyMap()
    private var favoriteIds: Set<Long> = emptySet()

    fun updateData(newProducts: List<Product>, newPrices: Map<Long, PriceInfo> = prices, newFavorites: Set<Long> = favoriteIds) {
        products = newProducts
        prices = newPrices
        favoriteIds = newFavorites
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder =
        ProductViewHolder(ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) = holder.bind(products[position])

    override fun getItemCount(): Int = products.size

    inner class ProductViewHolder(private val binding: ItemProductBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product) {
            val context = binding.root.context
            binding.tvProductName.text = product.name
            binding.tvCategoryName.text = product.categoryName.ifBlank { "Chưa phân loại" }
            binding.tvProductMaterial.text = product.material

            val price = prices[product.id]
            if (price != null && price.hasDiscount) {
                binding.tvProductPrice.text = CurrencyUtils.formatVnd(price.finalPrice)
                binding.tvProductOriginalPrice.text = CurrencyUtils.formatVnd(price.originalPrice)
                binding.tvProductOriginalPrice.paintFlags = binding.tvProductOriginalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                binding.tvProductOriginalPrice.visibility = View.VISIBLE
                binding.tvPromoBadge.text = "-${price.discountPercent}%"
                binding.tvPromoBadge.visibility = View.VISIBLE
            } else {
                binding.tvProductPrice.text = CurrencyUtils.formatVnd(product.price)
                binding.tvProductOriginalPrice.visibility = View.GONE
                binding.tvPromoBadge.visibility = View.GONE
            }

            val (stockText, stockColor, canBuy) = when {
                product.isDiscontinued -> Triple("Ngừng kinh doanh", R.color.stock_out, false)
                product.quantity <= 0 -> Triple("Hết hàng", R.color.stock_out, false)
                product.quantity <= 5 -> Triple("Còn ${product.quantity} sp (Sắp hết)", R.color.stock_low, true)
                else -> Triple("Còn ${product.quantity} sp", R.color.stock_in, true)
            }
            binding.tvStockBadge.text = stockText
            binding.tvStockBadge.setTextColor(ContextCompat.getColor(context, stockColor))
            binding.btnQuickAddCart.isEnabled = canBuy
            binding.btnQuickAddCart.alpha = if (canBuy) 1f else 0.5f

            ProductImageUtils.load(binding.imgProduct, product.imageName)

            if (onFavoriteClick != null) {
                val fav = product.id in favoriteIds
                binding.btnFavorite.visibility = View.VISIBLE
                binding.btnFavorite.setImageResource(if (fav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
                binding.btnFavorite.setColorFilter(ContextCompat.getColor(context, if (fav) R.color.heart_active else R.color.heart_inactive))
                binding.btnFavorite.contentDescription = if (fav) "Bỏ yêu thích" else "Thêm vào yêu thích"
                binding.btnFavorite.setOnClickListener { onFavoriteClick.invoke(product) }
            } else {
                binding.btnFavorite.visibility = View.GONE
            }

            binding.root.setOnClickListener { onItemClick(product) }
            binding.btnQuickAddCart.setOnClickListener { onQuickAddToCart(product) }
        }
    }
}
