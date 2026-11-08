package com.example.jewelrymanagement.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.jewelrymanagement.R
import com.example.jewelrymanagement.databinding.ItemCouponBinding
import com.example.jewelrymanagement.models.Coupon
import com.example.jewelrymanagement.utils.CurrencyUtils
import com.example.jewelrymanagement.utils.DateUtils

class CouponAdapter(
    private val context: Context,
    private var coupons: List<Coupon>,
    private val onEditClick: (Coupon) -> Unit,
    private val onDeleteClick: (Coupon) -> Unit,
    private val onToggleActive: (Coupon, Boolean) -> Unit
) : RecyclerView.Adapter<CouponAdapter.CouponViewHolder>() {

    fun updateData(newList: List<Coupon>) {
        coupons = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CouponViewHolder {
        val binding = ItemCouponBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CouponViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CouponViewHolder, position: Int) {
        holder.bind(coupons[position])
    }

    override fun getItemCount(): Int = coupons.size

    inner class CouponViewHolder(private val binding: ItemCouponBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(coupon: Coupon) {
            binding.tvCouponCode.text = coupon.code
            binding.tvCouponTitle.text = coupon.title

            val status = coupon.getStatus()
            val (bg, fg) = when (status) {
                "Đang hoạt động" -> R.color.status_delivered_bg to R.color.status_delivered_text
                "Hết hạn" -> R.color.status_cancelled_bg to R.color.status_cancelled_text
                else -> R.color.status_pending_bg to R.color.status_pending_text
            }
            binding.tvCouponStatus.text = status
            binding.tvCouponStatus.setBackgroundColor(ContextCompat.getColor(context, bg))
            binding.tvCouponStatus.setTextColor(ContextCompat.getColor(context, fg))

            binding.tvCouponDiscount.text = coupon.discountLabel()
            binding.tvCouponMinOrder.text = "• Đơn tối thiểu: ${CurrencyUtils.formatVnd(coupon.minOrder)}"
            binding.tvCouponUsage.text = "Đã dùng: ${coupon.usedCount}/${coupon.usageLimit} lượt (còn ${coupon.remainingUses})"
            binding.tvCouponDates.text =
                "Hiệu lực: ${DateUtils.toDisplay(coupon.startDate)} đến ${DateUtils.toDisplay(coupon.endDate)}"

            binding.switchActive.setOnCheckedChangeListener(null)
            binding.switchActive.isChecked = coupon.isActive
            binding.switchActive.setOnCheckedChangeListener { _, isChecked ->
                onToggleActive(coupon, isChecked)
            }

            binding.btnEdit.setOnClickListener { onEditClick(coupon) }
            binding.btnDelete.setOnClickListener { onDeleteClick(coupon) }
        }
    }
}
