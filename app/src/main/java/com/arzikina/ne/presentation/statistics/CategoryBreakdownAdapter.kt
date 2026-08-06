package com.arzikina.ne.presentation.statistics

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.R
import com.arzikina.ne.databinding.ItemCategoryBreakdownBinding
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.util.Money
import kotlin.math.roundToInt

/**
 * Légende de la répartition des dépenses par catégorie (voir [CategoryPieView] :
 * l'anneau affiche les proportions, cette liste donne le détail chiffré par
 * catégorie avec la même couleur que sa part d'anneau).
 *
 * [currencyCode] doit être mis à jour avant chaque [submitList] (voir
 * [StatisticsFragment]) : [CategoryBreakdownItem] ne porte pas la devise,
 * celle-ci vient de [com.arzikina.ne.presentation.statistics.StatisticsUiState.currencyCode].
 */
class CategoryBreakdownAdapter :
    ListAdapter<CategoryBreakdownItem, CategoryBreakdownAdapter.ViewHolder>(DIFF_CALLBACK) {

    var currencyCode: String = ""

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val binding = ItemCategoryBreakdownBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), currencyCode)
    }

    class ViewHolder(private val binding: ItemCategoryBreakdownBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CategoryBreakdownItem, currencyCode: String) {
            val context = binding.root.context

            val dotColor = item.category?.colorArgb?.toInt()
                ?: ContextCompat.getColor(context, R.color.arzikina_outline)
            binding.colorDot.backgroundTintList = ColorStateList.valueOf(dotColor)

            binding.categoryName.text = item.category?.name
                ?: context.getString(R.string.transaction_uncategorized)

            val amountText = Money.format(CurrencyAmount(currencyCode, item.amountMinor))
            binding.amount.text = context.getString(
                R.string.statistics_breakdown_item_subtitle,
                amountText,
                (item.percentage * 100).roundToInt()
            )
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<CategoryBreakdownItem>() {
            override fun areItemsTheSame(oldItem: CategoryBreakdownItem, newItem: CategoryBreakdownItem): Boolean =
                oldItem.category?.id == newItem.category?.id

            override fun areContentsTheSame(oldItem: CategoryBreakdownItem, newItem: CategoryBreakdownItem): Boolean =
                oldItem == newItem
        }
    }
}
