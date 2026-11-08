package com.example.jewelrymanagement.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.jewelrymanagement.R
import com.example.jewelrymanagement.database.OrderDao
import com.example.jewelrymanagement.database.ProductDao
import com.example.jewelrymanagement.databinding.ActivityStatisticsBinding
import com.example.jewelrymanagement.databinding.ItemBestsellerBinding
import com.example.jewelrymanagement.utils.CurrencyUtils

class StatisticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatisticsBinding
    private lateinit var orderDao: OrderDao
    private lateinit var productDao: ProductDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        orderDao = OrderDao(this)
        productDao = ProductDao(this)

        binding.btnBackStatistics.setOnClickListener { finish() }

        loadStatistics()
    }

    private fun loadStatistics() {
        // 1. Revenue & Total Orders
        val revenue = orderDao.getTotalRevenue()
        binding.tvStatRevenue.text = CurrencyUtils.formatVnd(revenue)

        val totalOrders = orderDao.getOrderCount()
        binding.tvStatTotalOrders.text = "Tổng số đơn toàn hệ thống: $totalOrders đơn"

        // 2. Order Breakdown by Status
        binding.tvCountPending.text = "${orderDao.getOrderCountByStatus("Chờ xác nhận")} đơn"
        binding.tvCountConfirmed.text = "${orderDao.getOrderCountByStatus("Đã xác nhận")} đơn"
        binding.tvCountShipping.text = "${orderDao.getOrderCountByStatus("Đang giao hàng")} đơn"
        binding.tvCountDelivered.text = "${orderDao.getOrderCountByStatus("Đã giao hàng")} đơn"
        binding.tvCountCancelled.text = "${orderDao.getOrderCountByStatus("Đã hủy")} đơn"

        // 3. Best Sellers
        binding.layoutBestSellersContainer.removeAllViews()
        val bestSellers = productDao.getBestSellers(5)
        if (bestSellers.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Chưa có dữ liệu bán chạy từ các đơn hàng."
                setTextColor(getColor(R.color.text_secondary))
                textSize = 13f
            }
            binding.layoutBestSellersContainer.addView(emptyTv)
        } else {
            bestSellers.forEachIndexed { index, pair ->
                val product = pair.first
                val totalSold = pair.second
                val itemBinding = ItemBestsellerBinding.inflate(LayoutInflater.from(this), binding.layoutBestSellersContainer, false)
                itemBinding.tvRank.text = (index + 1).toString()
                itemBinding.tvBestSellerName.text = product.name
                itemBinding.tvBestSellerPrice.text = CurrencyUtils.formatVnd(product.price)
                itemBinding.tvSoldQuantity.text = "Đã bán: $totalSold sp"
                binding.layoutBestSellersContainer.addView(itemBinding.root)
            }
        }

        // 4. Low stock inventory warnings
        binding.layoutLowStockContainer.removeAllViews()
        val lowStockProducts = productDao.getLowStockProducts(5)
        if (lowStockProducts.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Tất cả sản phẩm đều đang đủ tồn kho an toàn (> 5 sp)."
                setTextColor(getColor(R.color.stock_in))
                textSize = 13f
            }
            binding.layoutLowStockContainer.addView(emptyTv)
        } else {
            lowStockProducts.forEach { prod ->
                val rowTv = TextView(this).apply {
                    val statusText = if (prod.quantity == 0) "HẾT HÀNG (0)" else "SẮP HẾT (${prod.quantity})"
                    text = "• ${prod.name} — $statusText"
                    setTextColor(if (prod.quantity == 0) getColor(R.color.stock_out) else getColor(R.color.stock_low))
                    textSize = 13f
                    setPadding(0, 6, 0, 6)
                }
                binding.layoutLowStockContainer.addView(rowTv)
            }
        }
    }
}
