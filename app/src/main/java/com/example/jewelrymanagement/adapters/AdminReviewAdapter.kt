package com.example.jewelrymanagement.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.jewelrymanagement.R
import com.example.jewelrymanagement.databinding.ItemAdminReviewBinding
import com.example.jewelrymanagement.models.ProductReview
import com.example.jewelrymanagement.utils.DateUtils

class AdminReviewAdapter(
    private val context: Context,
    private var reviews: List<ProductReview>,
    private val onToggleVisibility: (ProductReview, Boolean) -> Unit
) : RecyclerView.Adapter<AdminReviewAdapter.AdminReviewViewHolder>() {

    fun updateData(newList: List<ProductReview>) {
        reviews = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminReviewViewHolder {
        val binding = ItemAdminReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AdminReviewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AdminReviewViewHolder, position: Int) {
        holder.bind(reviews[position])
    }

    override fun getItemCount(): Int = reviews.size

    inner class AdminReviewViewHolder(private val binding: ItemAdminReviewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(review: ProductReview) {
            binding.tvReviewerName.text = review.userName
            binding.tvReviewDate.text = DateUtils.toDisplay(review.createdAt)
            val productLabel = review.productName.ifBlank { "Sản phẩm #${review.productId}" }
            binding.tvReviewProductOrder.text = "$productLabel • Đơn hàng #${review.orderId}"
            binding.rbAdminReview.rating = review.rating.toFloat()

            if (review.comment.isNotBlank()) {
                binding.tvReviewComment.text = review.comment
                binding.tvReviewComment.visibility = ViewGroup.VISIBLE
            } else {
                binding.tvReviewComment.text = "(Không có nhận xét viết kèm)"
                binding.tvReviewComment.visibility = ViewGroup.VISIBLE
            }

            if (review.isVisible) {
                binding.tvVisibilityStatus.text = "Trạng thái: Đang hiển thị"
                binding.tvVisibilityStatus.setTextColor(ContextCompat.getColor(context, R.color.stock_in))
            } else {
                binding.tvVisibilityStatus.text = "Trạng thái: Đã bị ẩn"
                binding.tvVisibilityStatus.setTextColor(ContextCompat.getColor(context, R.color.stock_out))
            }

            binding.switchVisibility.setOnCheckedChangeListener(null)
            binding.switchVisibility.isChecked = review.isVisible
            binding.switchVisibility.setOnCheckedChangeListener { _, isChecked ->
                onToggleVisibility(review, isChecked)
            }
        }
    }
}
