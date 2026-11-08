package com.example.jewelrymanagement.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.jewelrymanagement.R
import com.example.jewelrymanagement.databinding.ItemCartBinding
import com.example.jewelrymanagement.models.CartItem
import com.example.jewelrymanagement.utils.CurrencyUtils
import com.example.jewelrymanagement.utils.ProductImageUtils

class CartAdapter(
    private var cartItems: List<CartItem>,
    private val onIncreaseClick: (CartItem) -> Unit,
    private val onDecreaseClick: (CartItem) -> Unit,
    private val onRemoveClick: (CartItem) -> Unit
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    fun updateData(newItems: List<CartItem>) {
        cartItems = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        holder.bind(cartItems[position])
    }

    override fun getItemCount(): Int = cartItems.size

    inner class CartViewHolder(private val binding: ItemCartBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CartItem) {
            val context = binding.root.context
            binding.tvCartProductName.text = item.productName
            binding.tvCartProductPrice.text = CurrencyUtils.formatVnd(item.productPrice)
            binding.tvCartQuantity.text = item.quantity.toString()
            binding.tvCartSubtotal.text = CurrencyUtils.formatVnd(item.subtotal)

            ProductImageUtils.load(binding.imgCartProduct, item.productImage)

            binding.btnCartIncrease.setOnClickListener { onIncreaseClick(item) }
            binding.btnCartDecrease.setOnClickListener { onDecreaseClick(item) }
            binding.btnCartRemove.setOnClickListener { onRemoveClick(item) }
        }
    }
}
