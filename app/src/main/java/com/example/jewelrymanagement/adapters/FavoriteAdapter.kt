package com.example.jewelrymanagement.adapters

import android.content.Context
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.jewelrymanagement.R
import com.example.jewelrymanagement.databinding.ItemFavoriteBinding
import com.example.jewelrymanagement.models.Product
import com.example.jewelrymanagement.repositories.PriceInfo
import com.example.jewelrymanagement.utils.CurrencyUtils
import com.example.jewelrymanagement.utils.ProductImageUtils

class FavoriteAdapter(
    private val context: Context,
    private var products: List<Product>,
    private val onProductClick: (Product) -> Unit,
    private val onRemoveFavorite: (Product) -> Unit,
    private val onAddToCart: (Product) -> Unit
) : RecyclerView.Adapter<FavoriteAdapter.FavoriteViewHolder>() {

    private var prices: Map<Long, PriceInfo> = emptyMap()

    fun updateData(newList: List<Product>, newPrices: Map<Long, PriceInfo>) {
        products = newList
        prices = newPrices
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder =
        FavoriteViewHolder(ItemFavoriteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) = holder.bind(products[position])

    override fun getItemCount(): Int = products.size

    inner class FavoriteViewHolder(private val binding: ItemFavoriteBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product) {
            binding.tvFavProdName.text = product.name

            ProductImageUtils.load(binding.imgFavProduct, product.imageName)

            val price = prices[product.id]
            if (price != null && price.hasDiscount) {
                binding.tvFavCurrentPrice.text = "${CurrencyUtils.formatVnd(price.finalPrice)} (-${price.discountPercent}%)"
                binding.tvFavOriginalPrice.text = CurrencyUtils.formatVnd(price.originalPrice)
                binding.tvFavOriginalPrice.paintFlags = binding.tvFavOriginalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                binding.tvFavOriginalPrice.visibility = View.VISIBLE
            } else {
                binding.tvFavCurrentPrice.text = CurrencyUtils.formatVnd(product.price)
                binding.tvFavOriginalPrice.visibility = View.GONE
            }

            val canBuy = product.quantity > 0 && !product.isDiscontinued
            binding.tvFavStockStatus.text = when {
                product.isDiscontinued -> "Đã ngừng kinh doanh"
                product.quantity > 0 -> "Còn hàng (${product.quantity})"
                else -> "Tạm hết hàng"
            }
            binding.tvFavStockStatus.setTextColor(ContextCompat.getColor(context, if (canBuy) R.color.stock_in else R.color.stock_out))
            binding.btnAddToCart.isEnabled = canBuy
            binding.btnAddToCart.alpha = if (canBuy) 1f else 0.5f

            binding.root.setOnClickListener { onProductClick(product) }
            binding.btnRemoveFavorite.setOnClickListener { onRemoveFavorite(product) }
            binding.btnAddToCart.setOnClickListener { onAddToCart(product) }
        }
    }
}
