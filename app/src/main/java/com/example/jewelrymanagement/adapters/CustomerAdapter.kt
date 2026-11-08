package com.example.jewelrymanagement.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.jewelrymanagement.databinding.ItemCustomerBinding
import com.example.jewelrymanagement.models.User

class CustomerAdapter(
    private var customers: List<User>,
    private val onItemClick: (User) -> Unit
) : RecyclerView.Adapter<CustomerAdapter.CustomerViewHolder>() {

    fun updateData(newCustomers: List<User>) {
        customers = newCustomers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomerViewHolder {
        val binding = ItemCustomerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CustomerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CustomerViewHolder, position: Int) {
        holder.bind(customers[position])
    }

    override fun getItemCount(): Int = customers.size

    inner class CustomerViewHolder(private val binding: ItemCustomerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(customer: User) {
            binding.tvCustomerFullName.text = customer.fullName
            binding.tvCustomerUsername.text = "@${customer.username}"
            val phone = if (customer.phone.isNotBlank()) customer.phone else "Chưa có SĐT"
            binding.tvCustomerEmailPhone.text = "${customer.email}  |  $phone"
            binding.tvCustomerAddress.text = if (customer.address.isNotBlank()) "Địa chỉ: ${customer.address}" else "Chưa cập nhật địa chỉ"
            binding.tvCustomerLockBadge.visibility = if (customer.isLocked) View.VISIBLE else View.GONE

            binding.root.setOnClickListener {
                onItemClick(customer)
            }
        }
    }
}
