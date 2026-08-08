package com.arzikina.ne.presentation.transactions

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.R
import com.arzikina.ne.databinding.ItemCategoryQuickPickBinding
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.presentation.categories.CategoryIconMapper

/** Une case de la grille "Catégorie" : une vraie [Category], ou la case spéciale "Ajouter". */
sealed interface CategoryPickerItem {
    data class Entry(val category: Category) : CategoryPickerItem
    data object AddNew : CategoryPickerItem
}

/**
 * Grille de sélection rapide de catégorie (voir maquette "PERSONNALISATION –
 * AJOUT DE TRANSACTION"), remplace l'ancien menu déroulant du formulaire de
 * transaction. Pas de [androidx.recyclerview.widget.ListAdapter]/DiffUtil ici
 * : la liste est intégralement recalculée à chaque changement de type
 * (Dépense/Revenu) et reste toujours courte — même choix que
 * [com.arzikina.ne.presentation.components.ColorPickerAdapter]/[com.arzikina.ne.presentation.components.IconPickerAdapter].
 */
class CategoryQuickPickAdapter(
    private var items: List<CategoryPickerItem> = emptyList(),
    private var selectedCategoryId: Long = 0L,
    private val onSelect: (Category) -> Unit,
    private val onAddNew: () -> Unit
) : RecyclerView.Adapter<CategoryQuickPickAdapter.ViewHolder>() {

    fun submitItems(newItems: List<CategoryPickerItem>, newSelectedCategoryId: Long) {
        items = newItems
        selectedCategoryId = newSelectedCategoryId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryQuickPickBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], selectedCategoryId, onSelect, onAddNew)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(private val binding: ItemCategoryQuickPickBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: CategoryPickerItem,
            selectedCategoryId: Long,
            onSelect: (Category) -> Unit,
            onAddNew: () -> Unit
        ) {
            val context = binding.root.context
            when (item) {
                is CategoryPickerItem.Entry -> {
                    val category = item.category
                    binding.categoryIcon.setImageResource(CategoryIconMapper.iconFor(category.icon))
                    binding.categoryIcon.backgroundTintList = ColorStateList.valueOf(category.colorArgb.toInt())
                    binding.categoryName.text = category.name
                    binding.iconRing.isSelected = category.id == selectedCategoryId
                    binding.root.setOnClickListener { onSelect(category) }
                }
                CategoryPickerItem.AddNew -> {
                    binding.categoryIcon.setImageResource(R.drawable.ic_add_24)
                    binding.categoryIcon.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.arzikina_outline)
                    )
                    binding.categoryName.text = context.getString(R.string.transaction_form_category_add)
                    binding.iconRing.isSelected = false
                    binding.root.setOnClickListener { onAddNew() }
                }
            }
        }
    }
}
