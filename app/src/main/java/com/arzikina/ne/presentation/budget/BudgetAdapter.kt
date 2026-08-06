package com.arzikina.ne.presentation.budget

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.R
import com.arzikina.ne.databinding.ItemBudgetBinding
import com.arzikina.ne.domain.model.BudgetPeriod
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.presentation.categories.CategoryIconMapper
import com.arzikina.ne.util.Money
import kotlin.math.roundToInt

/**
 * Liste des budgets avec leur progression sur la période en cours. Voir
 * [com.arzikina.ne.presentation.dashboard.RecentTransactionsAdapter] pour le
 * raisonnement (`ListAdapter`/`DiffUtil` plutôt que `notifyDataSetChanged`).
 */
class BudgetAdapter(
    private val onClick: (BudgetUiItem) -> Unit,
    private val onDeleteClick: (BudgetUiItem) -> Unit
) : ListAdapter<BudgetUiItem, BudgetAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val binding = ItemBudgetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClick, onDeleteClick)
    }

    class ViewHolder(private val binding: ItemBudgetBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BudgetUiItem, onClick: (BudgetUiItem) -> Unit, onDeleteClick: (BudgetUiItem) -> Unit) {
            val context = binding.root.context
            val category = item.category

            binding.categoryIcon.setImageResource(
                category?.let { CategoryIconMapper.iconFor(it.icon) } ?: R.drawable.ic_category_other_24
            )
            val circleColor = category?.colorArgb?.toInt()
                ?: ContextCompat.getColor(context, R.color.arzikina_outline)
            binding.categoryIcon.backgroundTintList = ColorStateList.valueOf(circleColor)
            binding.categoryName.text = category?.name
                ?: context.getString(R.string.transaction_uncategorized)

            val periodText = context.getString(
                if (item.budget.period == BudgetPeriod.WEEKLY) R.string.budget_period_weekly else R.string.budget_period_monthly
            )
            val limitText = Money.format(CurrencyAmount(item.budget.currencyCode, item.budget.limitAmount))
            binding.periodLabel.text = context.getString(R.string.budget_item_subtitle, periodText, limitText)

            val isOverspent = item.progress > 1f
            binding.progressBar.progress = (item.progress.coerceIn(0f, 1f) * 100).roundToInt()
            binding.progressBar.setIndicatorColor(
                ContextCompat.getColor(context, if (isOverspent) R.color.expense_red else R.color.arzikina_primary)
            )

            binding.remainingLabel.text = if (isOverspent) {
                val overspentAmount = Money.format(CurrencyAmount(item.budget.currencyCode, item.spentMinor - item.budget.limitAmount))
                context.getString(R.string.budget_overspent_prefix, overspentAmount)
            } else {
                val remainingAmount = Money.format(CurrencyAmount(item.budget.currencyCode, item.budget.limitAmount - item.spentMinor))
                context.getString(R.string.budget_remaining_prefix, remainingAmount)
            }
            binding.remainingLabel.setTextColor(
                ContextCompat.getColor(context, if (isOverspent) R.color.expense_red else R.color.arzikina_on_surface_variant)
            )

            binding.root.setOnClickListener { onClick(item) }
            binding.deleteButton.setOnClickListener { onDeleteClick(item) }
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<BudgetUiItem>() {
            override fun areItemsTheSame(oldItem: BudgetUiItem, newItem: BudgetUiItem): Boolean =
                oldItem.budget.id == newItem.budget.id

            override fun areContentsTheSame(oldItem: BudgetUiItem, newItem: BudgetUiItem): Boolean =
                oldItem == newItem
        }
    }
}
