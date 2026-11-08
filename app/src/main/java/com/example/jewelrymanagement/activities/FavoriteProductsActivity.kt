package com.example.jewelrymanagement.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.jewelrymanagement.adapters.FavoriteAdapter
import com.example.jewelrymanagement.database.CartDao
import com.example.jewelrymanagement.database.FavoriteDao
import com.example.jewelrymanagement.databinding.ActivityFavoriteProductsBinding
import com.example.jewelrymanagement.repositories.PricingRepository
import com.example.jewelrymanagement.utils.SessionManager

class FavoriteProductsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoriteProductsBinding
    private lateinit var favoriteDao: FavoriteDao
    private lateinit var cartDao: CartDao
    private lateinit var pricing: PricingRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: FavoriteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoriteProductsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        favoriteDao = FavoriteDao(this)
        cartDao = CartDao(this)
        pricing = PricingRepository(this)
        sessionManager = SessionManager(this)

        binding.toolbar.setNavigationOnClickListener { finish() }
        setupRecyclerView()
        binding.swipeRefresh.setOnRefreshListener { loadFavorites() }
    }

    override fun onResume() {
        super.onResume()
        loadFavorites()
    }

    private fun setupRecyclerView() {
        adapter = FavoriteAdapter(
            context = this,
            products = emptyList(),
            onProductClick = { product ->
                startActivity(Intent(this, ProductDetailActivity::class.java).putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, product.id))
            },
            onRemoveFavorite = { product ->
                if (favoriteDao.removeFavorite(sessionManager.getUserId(), product.id)) {
                    Toast.makeText(this, "Đã xóa khỏi danh sách yêu thích", Toast.LENGTH_SHORT).show()
                    loadFavorites()
                }
            },
            onAddToCart = { product ->
                val userId = sessionManager.getUserId()
                val (success, msg) = cartDao.addToCart(userId, product.id, 1)
                if (success) {
                    favoriteDao.removeFavorite(userId, product.id)
                    Toast.makeText(this, "Đã chuyển '${product.name}' vào giỏ hàng.", Toast.LENGTH_SHORT).show()
                    loadFavorites()
                } else {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )
        binding.rvFavorites.layoutManager = LinearLayoutManager(this)
        binding.rvFavorites.adapter = adapter
    }

    private fun loadFavorites() {
        val list = favoriteDao.getFavoriteProducts(sessionManager.getUserId())
        adapter.updateData(list, pricing.pricesFor(list))
        binding.layoutEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.swipeRefresh.isRefreshing = false
    }
}
