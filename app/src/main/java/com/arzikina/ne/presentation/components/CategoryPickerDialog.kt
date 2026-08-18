package com.arzikina.ne.presentation.components

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.R
import com.arzikina.ne.databinding.ItemCategoryPickerBinding
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.presentation.categories.CategoryIconMapper
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Dialogue de sélection d'une catégorie, avec une première ligne "Aucune catégorie" (voir
 * `onSelect(null)`) — utilisé par le formulaire de dépense prévue (Planification), dont la
 * catégorie est OPTIONNELLE (voir [com.arzikina.ne.domain.model.FinancialPlanItem.categoryId]).
 *
 * Nouveau composant plutôt que la réutilisation de `CategoryQuickPickAdapter` (grille intégrée du
 * formulaire de transaction, catégorie TOUJOURS requise là-bas, pas de case "Aucune") : même
 * raisonnement que [AccountPickerDialog] (dialogue générique et sans état propre, voir sa doc),
 * qui reste le composant le plus proche de ce dont ce formulaire a besoin.
 */
object CategoryPickerDialog {
    fun show(
        context: Context,
        categories: List<Category>,
        onSelect: (Category?) -> Unit
    ) {
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.financial_plan_item_form_category_picker_title)
            .setView(recyclerView)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        // Position 0 = "Aucune catégorie" (null), positions suivantes = categories[position - 1].
        recyclerView.adapter = object : RecyclerView.Adapter<CategoryViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
                val binding = ItemCategoryPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return CategoryViewHolder(binding)
            }

            override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
                val category = categories.getOrNull(position - 1)
                holder.bind(category) {
                    onSelect(category)
                    dialog.dismiss()
                }
            }

            override fun getItemCount(): Int = categories.size + 1
        }

        dialog.show()
    }

    private class CategoryViewHolder(private val binding: ItemCategoryPickerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(category: Category?, onClick: () -> Unit) {
            val context = binding.root.context
            if (category != null) {
                binding.categoryIcon.setImageResource(CategoryIconMapper.iconFor(category.icon))
                binding.categoryIcon.backgroundTintList = ColorStateList.valueOf(category.colorArgb.toInt())
                binding.categoryName.text = category.name
            } else {
                binding.categoryIcon.setImageResource(R.drawable.ic_category_other_24)
                binding.categoryIcon.backgroundTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.arzikina_outline))
                binding.categoryName.text = context.getString(R.string.financial_plan_item_form_category_none)
            }
            binding.root.setOnClickListener { onClick() }
        }
    }
}
