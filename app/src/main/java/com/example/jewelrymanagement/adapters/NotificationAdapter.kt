package com.example.jewelrymanagement.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.jewelrymanagement.R
import com.example.jewelrymanagement.databinding.ItemNotificationBinding
import com.example.jewelrymanagement.models.NotificationItem
import com.example.jewelrymanagement.utils.DateUtils

class NotificationAdapter(
    private val context: Context,
    private var notifications: List<NotificationItem>,
    private val onNotificationClick: (NotificationItem) -> Unit,
    private val onMarkReadClick: (NotificationItem) -> Unit,
    private val onReviewNowClick: (NotificationItem) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    fun updateData(newList: List<NotificationItem>) {
        notifications = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(notifications[position])
    }

    override fun getItemCount(): Int = notifications.size

    inner class NotificationViewHolder(private val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NotificationItem) {
            binding.tvNotifTitle.text = item.title
            binding.tvNotifContent.text = item.content
            binding.tvNotifDate.text = DateUtils.toDisplay(item.createdAt)

            if (item.isRead) {
                binding.viewUnreadDot.visibility = View.GONE
                binding.btnMarkRead.visibility = View.GONE
                binding.cardNotification.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_card))
            } else {
                binding.viewUnreadDot.visibility = View.VISIBLE
                binding.btnMarkRead.visibility = View.VISIBLE
                binding.cardNotification.setCardBackgroundColor(ContextCompat.getColor(context, R.color.gold_pale))
            }

            if (item.actionType == NotificationItem.ACTION_REVIEW_ORDER && item.orderId != null) {
                binding.btnReviewNow.visibility = View.VISIBLE
                binding.btnReviewNow.setOnClickListener { onReviewNowClick(item) }
            } else {
                binding.btnReviewNow.visibility = View.GONE
            }

            binding.btnMarkRead.setOnClickListener { onMarkReadClick(item) }
            binding.root.setOnClickListener { onNotificationClick(item) }
        }
    }
}
