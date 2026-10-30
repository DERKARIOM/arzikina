package com.naniger.arzikina.presentation.auth.currency

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.naniger.arzikina.databinding.ItemCurrencyBinding

/** Ligne d'affichage d'une devise (données déjà résolues : nom traduit, état de sélection). */
data class CurrencyRow(
    val code: String,
    val name: String,
    val symbol: String,
    val selected: Boolean
)

/** Liste des devises : `ListAdapter` + `DiffUtil` (fluidité), une seule ligne cochée à la fois. */
class CurrencyAdapter(
    private val onClick: (String) -> Unit
) : ListAdapter<CurrencyRow, CurrencyAdapter.ViewHolder>(Diff) {

    class ViewHolder(val binding: ItemCurrencyBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemCurrencyBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = getItem(position)
        val b = holder.binding
        b.currencyName.text = row.name
        b.currencyCode.text = row.code
        b.currencySymbol.text = row.symbol
        val card = b.currencyCard
        card.isChecked = row.selected
        card.strokeColor = MaterialColors.getColor(
            card,
            if (row.selected) androidx.appcompat.R.attr.colorPrimary else com.google.android.material.R.attr.colorOutlineVariant
        )
        card.strokeWidth = ((if (row.selected) 2 else 1) * card.resources.displayMetrics.density).toInt()
        card.setOnClickListener { onClick(row.code) }
    }

    private object Diff : DiffUtil.ItemCallback<CurrencyRow>() {
        override fun areItemsTheSame(a: CurrencyRow, b: CurrencyRow) = a.code == b.code
        override fun areContentsTheSame(a: CurrencyRow, b: CurrencyRow) = a == b
    }
}
