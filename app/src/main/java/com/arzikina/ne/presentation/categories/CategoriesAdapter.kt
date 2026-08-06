package com.arzikina.ne.presentation.categories

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.databinding.ItemCategoryBinding
import com.arzikina.ne.domain.model.Category

/**
 * Liste des catégories. Voir [com.arzikina.ne.presentation.dashboard.RecentTransactionsAdapter]
 * pour le raisonnement (`ListAdapter`/`DiffUtil`).
 */
class CategoriesAdapter(
    private val onClick: (Category) -> Unit,
    private val onDeleteClick: (Category) -> Unit
) : ListAdapter<Category, CategoriesAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClick, onDeleteClick)
    }

    class ViewHolder(private val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(category: Category, onClick: (Category) -> Unit, onDeleteClick: (Category) -> Unit) {
            binding.categoryIcon.setImageResource(CategoryIconMapper.iconFor(category.icon))
            binding.categoryIcon.backgroundTintList = ColorStateList.valueOf(category.colorArgb.toInt())
            binding.categoryName.text = category.name
            binding.root.setOnClickListener { onClick(category) }
            binding.deleteButton.setOnClickListener { onDeleteClick(category) }
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Category>() {
            override fun areItemsTheSame(oldItem: Category, newItem: Category): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Category, newItem: Category): Boolean = oldItem == newItem
        }
    }
}
