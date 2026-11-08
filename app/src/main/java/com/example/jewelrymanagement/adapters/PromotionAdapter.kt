package com.example.jewelrymanagement.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.jewelrymanagement.R
import com.example.jewelrymanagement.databinding.ItemPromotionBinding
import com.example.jewelrymanagement.models.Promotion
import com.example.jewelrymanagement.utils.DateUtils

class PromotionAdapter(
    private val context: Context,
    private var promotions: List<Promotion>,
    private val onEditClick: (Promotion) -> Unit,
    private val onDeleteClick: (Promotion) -> Unit,
    private val onToggleActive: (Promotion, Boolean) -> Unit,
    private val onItemClick: ((Promotion) -> Unit)? = null
) : RecyclerView.Adapter<PromotionAdapter.PromotionViewHolder>() {

    fun updateData(newList: List<Promotion>) {
        promotions = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PromotionViewHolder {
        val binding = ItemPromotionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PromotionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PromotionViewHolder, position: Int) {
        holder.bind(promotions[position])
    }

    override fun getItemCount(): Int = promotions.size

    inner class PromotionViewHolder(private val binding: ItemPromotionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(promo: Promotion) {
            binding.tvPromoCode.text = promo.code
            binding.tvPromoName.text = promo.name
            binding.tvPromoDesc.text = promo.description

            // Status badge calculation
            val status = promo.getStatus()
            binding.tvPromoStatus.text = status

            when (status) {
                "Đang diễn ra" -> {
                    binding.tvPromoStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.status_delivered_bg))
                    binding.tvPromoStatus.setTextColor(ContextCompat.getColor(context, R.color.status_delivered_text))
                }
                "Sắp diễn ra" -> {
                    binding.tvPromoStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.status_confirmed_bg))
                    binding.tvPromoStatus.setTextColor(ContextCompat.getColor(context, R.color.status_confirmed_text))
                }
                "Đã kết thúc" -> {
                    binding.tvPromoStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.status_cancelled_bg))
                    binding.tvPromoStatus.setTextColor(ContextCompat.getColor(context, R.color.status_cancelled_text))
                }
                else -> { // Tạm ngừng
                    binding.tvPromoStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.status_pending_bg))
                    binding.tvPromoStatus.setTextColor(ContextCompat.getColor(context, R.color.status_pending_text))
                }
            }

            binding.tvPromoDiscount.text = promo.discountLabel().removePrefix("-").let { "Giảm $it" }

            val names = promo.targetNames.joinToString(", ").ifBlank { "(chưa chọn)" }
            binding.tvPromoTarget.text = when (promo.targetType) {
                Promotion.TARGET_CATEGORY -> "• Áp dụng danh mục: $names"
                Promotion.TARGET_PRODUCT -> "• Áp dụng sản phẩm: $names"
                else -> "• Áp dụng: Toàn bộ sản phẩm"
            }

            binding.tvPromoDates.text = "Thời hạn: ${DateUtils.toDisplay(promo.startDate)} → ${DateUtils.toDisplay(promo.endDate)}"
            binding.root.setOnClickListener { onItemClick?.invoke(promo) }

            // Switch listener without triggering during recycle
            binding.switchActive.setOnCheckedChangeListener(null)
            binding.switchActive.isChecked = promo.isActive
            binding.switchActive.setOnCheckedChangeListener { _, isChecked ->
                onToggleActive(promo, isChecked)
            }

            binding.btnEdit.setOnClickListener { onEditClick(promo) }
            binding.btnDelete.setOnClickListener { onDeleteClick(promo) }
        }
    }
}
