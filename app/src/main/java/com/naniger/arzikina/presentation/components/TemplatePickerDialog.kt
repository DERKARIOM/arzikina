package com.naniger.arzikina.presentation.components

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.ItemTemplatePickerBinding
import com.naniger.arzikina.domain.model.Category
import com.naniger.arzikina.domain.model.CurrencyAmount
import com.naniger.arzikina.domain.model.TransactionTemplate
import com.naniger.arzikina.presentation.categories.CategoryIconMapper
import com.naniger.arzikina.util.Money
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Dialogue de sélection d'un modèle de transaction (cahier des charges "Marketplace personnelle",
 * extension "Choisir un modèle depuis l'ajout de transaction") — même structure que
 * [AccountPickerDialog] (`RecyclerView` dans une `MaterialAlertDialogBuilder`, pas de nouvel écran,
 * générique/sans état propre).
 *
 * [categoryFor] : résout l'icône/le nom de catégorie de chaque modèle — fourni par l'appelant
 * (voir `TransactionFormViewModel.allCategories`, catégories de TOUS types, pas seulement celui du
 * formulaire) plutôt que recalculé ici, même principe que [AccountPickerDialog.balanceFor].
 *
 * [currencyCode] : celui du compte ACTUELLEMENT sélectionné dans le formulaire, pas celui du compte
 * du modèle — le compte n'est délibérément pas repris d'un modèle ici (voir
 * `TransactionFormViewModel.applyTemplate`), donc son montant s'affiche dans la devise du compte
 * qui sera réellement utilisé.
 *
 * Si [templates] est vide, affiche un message dédié plutôt qu'une liste vide silencieuse (cahier
 * des charges "Marketplace personnelle" section 13, états vides).
 */
object TemplatePickerDialog {
    fun show(
        context: Context,
        templates: List<TransactionTemplate>,
        categoryFor: (Long) -> Category?,
        currencyCode: String,
        onSelect: (TransactionTemplate) -> Unit
    ) {
        if (templates.isEmpty()) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.transaction_form_template_picker_title)
                .setMessage(R.string.transaction_form_template_picker_empty)
                .setPositiveButton(R.string.action_close, null)
                .show()
            return
        }

        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.transaction_form_template_picker_title)
            .setView(recyclerView)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        recyclerView.adapter = object : RecyclerView.Adapter<TemplateViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TemplateViewHolder {
                val binding = ItemTemplatePickerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return TemplateViewHolder(binding)
            }

            override fun onBindViewHolder(holder: TemplateViewHolder, position: Int) {
                val template = templates[position]
                holder.bind(template, categoryFor(template.categoryId), currencyCode) {
                    onSelect(template)
                    dialog.dismiss()
                }
            }

            override fun getItemCount(): Int = templates.size
        }

        dialog.show()
    }

    private class TemplateViewHolder(private val binding: ItemTemplatePickerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(template: TransactionTemplate, category: Category?, currencyCode: String, onClick: () -> Unit) {
            val context = binding.root.context
            if (category != null) {
                binding.templatePickerIcon.setImageResource(CategoryIconMapper.iconFor(category.icon))
                binding.templatePickerIcon.backgroundTintList = ColorStateList.valueOf(category.colorArgb.toInt())
            }
            binding.templatePickerName.text = template.name
            binding.templatePickerSubtitle.text = context.getString(
                R.string.transaction_form_template_picker_subtitle,
                category?.displayName(context).orEmpty(),
                Money.format(CurrencyAmount(currencyCode, template.amount))
            )
            binding.root.setOnClickListener { onClick() }
        }
    }
}
