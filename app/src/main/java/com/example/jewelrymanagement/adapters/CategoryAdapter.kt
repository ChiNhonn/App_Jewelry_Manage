package com.example.jewelrymanagement.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.jewelrymanagement.databinding.ItemCategoryBinding
import com.example.jewelrymanagement.models.Category

class CategoryAdapter(
    private var categories: List<Category>,
    private val isAdminMode: Boolean = true,
    private val onItemClick: (Category) -> Unit,
    private val onEditClick: ((Category) -> Unit)? = null,
    private val onDeleteClick: ((Category) -> Unit)? = null
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    fun updateData(newCategories: List<Category>) {
        categories = newCategories
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount(): Int = categories.size

    inner class CategoryViewHolder(private val binding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(category: Category) {
            binding.tvCategoryTitle.text = category.name
            binding.tvCategoryDesc.text = if (category.description.isNotBlank()) category.description else "Danh mục trang sức chính hãng"

            if (isAdminMode) {
                binding.layoutAdminCatActions.visibility = View.VISIBLE
                binding.btnEditCategory.setOnClickListener { onEditClick?.invoke(category) }
                binding.btnDeleteCategory.setOnClickListener { onDeleteClick?.invoke(category) }
            } else {
                binding.layoutAdminCatActions.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onItemClick(category)
            }
        }
    }
}
