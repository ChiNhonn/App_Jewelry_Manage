package com.example.jewelrymanagement.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.jewelrymanagement.R
import com.example.jewelrymanagement.adapters.OrderAdapter
import com.example.jewelrymanagement.database.OrderDao
import com.example.jewelrymanagement.databinding.ActivityManageOrderBinding

class ManageOrderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageOrderBinding
    private lateinit var orderDao: OrderDao
    private lateinit var adapter: OrderAdapter

    private val statusOptions = listOf(
        "Tất cả trạng thái",
        "Chờ xác nhận",
        "Đã xác nhận",
        "Đang giao hàng",
        "Đã giao hàng",
        "Đã hủy"
    )
    private var selectedFilter = "ALL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        orderDao = OrderDao(this)

        setupRecyclerView()
        setupSpinner()
        setupSwipeRefresh()

        binding.btnBackManageOrder.setOnClickListener { finish() }
        binding.btnResetOrderFilter.setOnClickListener { resetOrderFilter() }
        binding.btnResetOrdersEmpty.setOnClickListener { resetOrderFilter() }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshOrders.setColorSchemeColors(
            androidx.core.content.ContextCompat.getColor(this, R.color.gold_primary),
            androidx.core.content.ContextCompat.getColor(this, R.color.gold_primary_dark)
        )
        binding.swipeRefreshOrders.setOnRefreshListener {
            loadOrders()
            binding.swipeRefreshOrders.isRefreshing = false
            android.widget.Toast.makeText(this, "Đã làm mới danh sách đơn hàng!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetOrderFilter() {
        binding.spOrderStatusFilter.setSelection(0)
        selectedFilter = "ALL"
        loadOrders()
        android.widget.Toast.makeText(this, "Đã đặt lại bộ lọc đơn hàng", android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        loadOrders()
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
        binding.rvManageOrders.layoutManager = LinearLayoutManager(this)
        binding.rvManageOrders.adapter = adapter
    }

    private fun setupSpinner() {
        val spinnerAdapter = ArrayAdapter(this, R.layout.item_spinner_compact, statusOptions)
        spinnerAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        binding.spOrderStatusFilter.adapter = spinnerAdapter

        binding.spOrderStatusFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedFilter = if (position == 0) "ALL" else statusOptions[position]
                loadOrders()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadOrders() {
        val orders = orderDao.getAllOrders(selectedFilter)
        adapter.updateData(orders)
        binding.tvOrderCountSummary.text = "Hiển thị ${orders.size} đơn hàng"

        if (orders.isEmpty()) {
            binding.layoutEmptyOrders.visibility = View.VISIBLE
            binding.rvManageOrders.visibility = View.GONE
        } else {
            binding.layoutEmptyOrders.visibility = View.GONE
            binding.rvManageOrders.visibility = View.VISIBLE
        }
    }
}
