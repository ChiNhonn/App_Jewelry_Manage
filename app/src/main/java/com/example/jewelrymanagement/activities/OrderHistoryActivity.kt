package com.example.jewelrymanagement.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.jewelrymanagement.adapters.OrderAdapter
import com.example.jewelrymanagement.database.OrderDao
import com.example.jewelrymanagement.databinding.ActivityOrderHistoryBinding
import com.example.jewelrymanagement.utils.SessionManager

class OrderHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderHistoryBinding
    private lateinit var orderDao: OrderDao
    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: OrderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        orderDao = OrderDao(this)
        sessionManager = SessionManager(this)

        setupRecyclerView()
        setupSwipeRefresh()
        binding.btnBackOrderHistory.setOnClickListener { finish() }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshOrderHistory.setColorSchemeColors(
            androidx.core.content.ContextCompat.getColor(this, com.example.jewelrymanagement.R.color.gold_primary),
            androidx.core.content.ContextCompat.getColor(this, com.example.jewelrymanagement.R.color.gold_primary_dark)
        )
        binding.swipeRefreshOrderHistory.setOnRefreshListener {
            loadCustomerOrders()
            binding.swipeRefreshOrderHistory.isRefreshing = false
            android.widget.Toast.makeText(this, "Đã làm mới lịch sử đơn hàng!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadCustomerOrders()
    }

    private fun setupRecyclerView() {
        adapter = OrderAdapter(
            orders = emptyList(),
            onItemClick = { order ->
                val intent = Intent(this, OrderDetailActivity::class.java)
                intent.putExtra("ORDER_ID", order.id)
                startActivity(intent)
            }
        )
        binding.rvOrderHistory.layoutManager = LinearLayoutManager(this)
        binding.rvOrderHistory.adapter = adapter
    }

    private fun loadCustomerOrders() {
        val userId = sessionManager.getUserId()
        val orders = orderDao.getOrdersByUser(userId)
        adapter.updateData(orders)
        binding.tvOrderHistorySummary.text = "Bạn có ${orders.size} đơn hàng đã đặt"

        if (orders.isEmpty()) {
            binding.layoutEmptyOrderHistory.visibility = View.VISIBLE
            binding.rvOrderHistory.visibility = View.GONE
        } else {
            binding.layoutEmptyOrderHistory.visibility = View.GONE
            binding.rvOrderHistory.visibility = View.VISIBLE
        }
    }
}
