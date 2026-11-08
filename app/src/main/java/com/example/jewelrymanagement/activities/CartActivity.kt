package com.example.jewelrymanagement.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.jewelrymanagement.adapters.CartAdapter
import com.example.jewelrymanagement.database.CartDao
import com.example.jewelrymanagement.databinding.ActivityCartBinding
import com.example.jewelrymanagement.models.CartItem
import com.example.jewelrymanagement.repositories.PricingRepository
import com.example.jewelrymanagement.utils.CurrencyUtils
import com.example.jewelrymanagement.utils.SessionManager

class CartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCartBinding
    private lateinit var cartDao: CartDao
    private lateinit var pricing: PricingRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: CartAdapter

    private var cartItems = listOf<CartItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cartDao = CartDao(this)
        pricing = PricingRepository(this)
        sessionManager = SessionManager(this)

        setupRecyclerView()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        loadCart()
    }

    private fun setupRecyclerView() {
        adapter = CartAdapter(
            cartItems = emptyList(),
            onIncreaseClick = { item -> handleQuantityChange(item, item.quantity + 1) },
            onDecreaseClick = { item -> handleQuantityChange(item, item.quantity - 1) },
            onRemoveClick = { item -> handleRemoveItem(item) }
        )
        binding.rvCartItems.layoutManager = LinearLayoutManager(this)
        binding.rvCartItems.adapter = adapter
    }

    private fun loadCart() {
        val userId = sessionManager.getShopperId()
        val rawItems = cartDao.getCartItems(userId)
        val prices = pricing.quote(rawItems.map { it.productId to it.quantity }, null).lines.associateBy({ it.productId }, { it.price })
        cartItems = rawItems.map { item ->
            val price = prices[item.productId]
            if (price != null && price.hasDiscount) {
                item.copy(productName = "${item.productName} (-${price.discountPercent}%)", productPrice = price.finalPrice)
            } else item
        }
        adapter.updateData(cartItems)

        val total = cartItems.sumOf { it.subtotal }
        binding.tvCartTotalPrice.text = CurrencyUtils.formatVnd(total)

        if (cartItems.isEmpty()) {
            binding.layoutEmptyCart.visibility = View.VISIBLE
            binding.rvCartItems.visibility = View.GONE
            binding.cardCartBottomSummary.visibility = View.GONE
            binding.btnClearCart.visibility = View.GONE
        } else {
            binding.layoutEmptyCart.visibility = View.GONE
            binding.rvCartItems.visibility = View.VISIBLE
            binding.cardCartBottomSummary.visibility = View.VISIBLE
            binding.btnClearCart.visibility = View.VISIBLE
        }
    }

    private fun handleQuantityChange(item: CartItem, newQty: Int) {
        val result = cartDao.updateQuantity(item.id, newQty)
        if (!result.first) {
            Toast.makeText(this, result.second, Toast.LENGTH_SHORT).show()
        }
        loadCart()
    }

    private fun handleRemoveItem(item: CartItem) {
        AlertDialog.Builder(this)
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn có muốn xóa '${item.productName}' khỏi giỏ hàng?")
            .setPositiveButton("Xóa") { _, _ ->
                cartDao.removeFromCart(item.id)
                loadCart()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun setupListeners() {
        binding.btnBackCart.setOnClickListener { finish() }

        binding.btnContinueShopping.setOnClickListener { finish() }

        binding.btnClearCart.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Xóa giỏ hàng")
                .setMessage("Bạn có chắc chắn muốn làm trống toàn bộ giỏ hàng?")
                .setPositiveButton("Làm trống") { _, _ ->
                    cartDao.clearCart(sessionManager.getShopperId())
                    loadCart()
                }
                .setNegativeButton("Hủy", null)
                .show()
        }

        binding.btnProceedCheckout.setOnClickListener {
            if (cartItems.isEmpty()) {
                Toast.makeText(this, "Giỏ hàng rỗng, không thể đặt hàng!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, CheckoutActivity::class.java))
        }
    }
}
