package com.example.jewelrymanagement.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.jewelrymanagement.R
import com.example.jewelrymanagement.databinding.ItemOrderBinding
import com.example.jewelrymanagement.models.Order
import com.example.jewelrymanagement.utils.CurrencyUtils

class OrderAdapter(
    private var orders: List<Order>,
    private val onItemClick: (Order) -> Unit
) : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    fun updateData(newOrders: List<Order>) {
        orders = newOrders
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(orders[position])
    }

    override fun getItemCount(): Int = orders.size

    inner class OrderViewHolder(private val binding: ItemOrderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(order: Order) {
            val context = binding.root.context
            binding.tvOrderCode.text = "Đơn hàng #${order.id}"
            binding.tvOrderDate.text = "Ngày đặt: ${order.createdAt}"
            binding.tvOrderReceiver.text = "Người nhận: ${order.receiverName} (${order.phone})"
            binding.tvOrderPaymentMethod.text = "Thanh toán: ${order.paymentMethod} • ${order.paymentStatus}"
            binding.tvOrderTotalAmount.text = CurrencyUtils.formatVnd(order.totalAmount)

            // Status badge styling
            binding.tvOrderStatusBadge.text = order.orderStatus
            when (order.orderStatus) {
                "Chờ xác nhận" -> {
                    binding.tvOrderStatusBadge.setBackgroundResource(R.drawable.bg_status_pending)
                    binding.tvOrderStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_pending_text))
                }
                "Đã xác nhận" -> {
                    binding.tvOrderStatusBadge.setBackgroundResource(R.drawable.bg_status_confirmed)
                    binding.tvOrderStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_confirmed_text))
                }
                "Đang giao hàng" -> {
                    binding.tvOrderStatusBadge.setBackgroundResource(R.drawable.bg_status_shipping)
                    binding.tvOrderStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_shipping_text))
                }
                "Đã giao hàng" -> {
                    binding.tvOrderStatusBadge.setBackgroundResource(R.drawable.bg_status_delivered)
                    binding.tvOrderStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_delivered_text))
                }
                "Đã hủy" -> {
                    binding.tvOrderStatusBadge.setBackgroundResource(R.drawable.bg_status_cancelled)
                    binding.tvOrderStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_cancelled_text))
                }
                else -> {
                    binding.tvOrderStatusBadge.setBackgroundResource(R.drawable.bg_badge_rounded)
                    binding.tvOrderStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                }
            }

            binding.root.setOnClickListener {
                onItemClick(order)
            }
        }
    }
}
