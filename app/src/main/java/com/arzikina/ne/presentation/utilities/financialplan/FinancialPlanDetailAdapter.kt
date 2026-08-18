package com.arzikina.ne.presentation.utilities.financialplan

import android.content.res.ColorStateList
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.R
import com.arzikina.ne.databinding.ItemFinancialPlanDetailHeaderBinding
import com.arzikina.ne.databinding.ItemFinancialPlanRowBinding
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.domain.model.FinancialPlanItem
import com.arzikina.ne.domain.model.PlanItemStatus
import com.arzikina.ne.presentation.categories.CategoryIconMapper
import com.arzikina.ne.util.Constants
import com.arzikina.ne.util.DatePeriods
import com.arzikina.ne.util.Money
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Liste de l'écran "Détail de la planification" : une ligne [FinancialPlanDetailListRow.Header]
 * (résumé, voir `item_financial_plan_detail_header.xml`) suivie d'une ligne
 * [FinancialPlanDetailListRow.ItemRow] par dépense prévue — même principe `ListAdapter`/`DiffUtil`
 * à deux types de vue que `LoanDetailAdapter` (voir sa doc).
 *
 * [onClickItem] (Étape 5) ouvre l'édition de la dépense — voir `FinancialPlanDetailFragment`.
 */
class FinancialPlanDetailAdapter(
    private val onClickItem: (FinancialPlanItem) -> Unit,
    private val onDeleteItem: (FinancialPlanItem) -> Unit
) : ListAdapter<FinancialPlanDetailListRow, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is FinancialPlanDetailListRow.Header -> VIEW_TYPE_HEADER
        is FinancialPlanDetailListRow.ItemRow -> VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(ItemFinancialPlanDetailHeaderBinding.inflate(inflater, parent, false))
        } else {
            ItemViewHolder(ItemFinancialPlanRowBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is FinancialPlanDetailListRow.Header -> (holder as HeaderViewHolder).bind(row.uiState)
            is FinancialPlanDetailListRow.ItemRow -> {
                // categoriesById ne vit que sur le Header (voir sa doc) : toujours en position 0,
                // seule ligne de ce type dans la liste — même principe que
                // LoanDetailAdapter.HeaderViewHolder qui porte à lui seul toutes les données de
                // résumé nécessaires aux lignes suivantes.
                val categoriesById = (getItem(0) as? FinancialPlanDetailListRow.Header)?.uiState?.categoriesById.orEmpty()
                val category = row.item.categoryId?.let { categoriesById[it] }
                (holder as ItemViewHolder).bind(row.item, category, onClickItem, onDeleteItem)
            }
        }
    }

    class HeaderViewHolder(private val binding: ItemFinancialPlanDetailHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(uiState: FinancialPlanDetailUiState) {
            val context = binding.root.context
            val plan = uiState.plan

            binding.planIcon.setImageResource(FinancialPlanIconMapper.iconFor(plan.icon))
            binding.planIcon.backgroundTintList = ColorStateList.valueOf(plan.colorArgb.toInt())
            binding.planName.text = plan.name

            binding.availableValue.text = Money.format(CurrencyAmount(Constants.DEFAULT_CURRENCY_CODE, plan.availableAmount))
            binding.plannedValue.text = Money.format(CurrencyAmount(Constants.DEFAULT_CURRENCY_CODE, uiState.totalPlanned))

            binding.progressBar.progress = uiState.progressPercent
            binding.progressBar.setIndicatorColor(plan.colorArgb.toInt())

            val overBudgetColor = ContextCompat.getColor(context, R.color.expense_red)
            val normalColor = ContextCompat.getColor(context, R.color.arzikina_on_surface_variant)

            if (uiState.isOverBudget) {
                val overspentAmount = Money.format(CurrencyAmount(Constants.DEFAULT_CURRENCY_CODE, -uiState.remainingAmount))
                binding.remainingLabel.text = context.getString(R.string.financial_plan_overbudget_prefix, overspentAmount)
                binding.remainingLabel.setTextColor(overBudgetColor)
                binding.progressBar.setIndicatorColor(overBudgetColor)
            } else {
                val remainingAmount = Money.format(CurrencyAmount(Constants.DEFAULT_CURRENCY_CODE, uiState.remainingAmount))
                binding.remainingLabel.text = context.getString(R.string.financial_plan_remaining_prefix, remainingAmount)
                binding.remainingLabel.setTextColor(normalColor)
            }

            binding.itemsSectionTitle.text =
                context.getString(R.string.financial_plan_detail_items_title, uiState.items.size)
            binding.itemsEmptyText.visibility = if (uiState.items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    class ItemViewHolder(private val binding: ItemFinancialPlanRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: FinancialPlanItem,
            category: Category?,
            onClickItem: (FinancialPlanItem) -> Unit,
            onDeleteItem: (FinancialPlanItem) -> Unit
        ) {
            val context = binding.root.context

            if (category != null) {
                binding.itemCategoryIcon.setImageResource(CategoryIconMapper.iconFor(category.icon))
                binding.itemCategoryIcon.backgroundTintList = ColorStateList.valueOf(category.colorArgb.toInt())
            } else {
                binding.itemCategoryIcon.setImageResource(R.drawable.ic_category_other_24)
                binding.itemCategoryIcon.backgroundTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.arzikina_outline))
            }

            binding.itemName.text = item.name
            binding.itemAmount.text = Money.format(CurrencyAmount(Constants.DEFAULT_CURRENCY_CODE, item.amount))
            // Voir la doc de item_financial_plan_row.xml/itemConvertedIcon (Étape 6).
            binding.itemConvertedIcon.visibility = if (item.transactionId != null) View.VISIBLE else View.GONE

            val statusDisplay = planItemStatusDisplay(item.status)
            binding.itemStatusPill.text = context.getString(statusDisplay.labelRes)
            binding.itemStatusPill.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, statusDisplay.colorRes))

            val priorityDisplay = planItemPriorityDisplay(item.priority)
            binding.itemPriorityLabel.text = context.getString(priorityDisplay.labelRes)
            binding.itemPriorityLabel.setTextColor(ContextCompat.getColor(context, priorityDisplay.colorRes))

            val plannedDate = item.plannedDate
            binding.itemDateLabel.visibility = if (plannedDate != null) View.VISIBLE else View.GONE
            if (plannedDate != null) {
                binding.itemDateLabel.text = DatePeriods.toLocalDate(plannedDate).format(DATE_FORMATTER)
            }

            val description = item.description.orEmpty().trim()
            binding.itemDescription.visibility = if (description.isEmpty()) View.GONE else View.VISIBLE
            binding.itemDescription.text = description

            // Dépense annulée : ligne atténuée + nom barré (voir la doc de planItemStatusDisplay) —
            // choix visuel volontairement discret ("alerte sobre"), pas de bandeau ni de dialogue.
            val isCancelled = item.status == PlanItemStatus.CANCELLED
            binding.root.alpha = if (isCancelled) 0.55f else 1f
            binding.itemName.paintFlags = if (isCancelled) {
                binding.itemName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.itemName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            binding.root.setOnClickListener { onClickItem(item) }
            binding.itemDeleteButton.setOnClickListener { onDeleteItem(item) }
        }
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_ITEM = 1

        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<FinancialPlanDetailListRow>() {
            override fun areItemsTheSame(oldItem: FinancialPlanDetailListRow, newItem: FinancialPlanDetailListRow): Boolean =
                when {
                    oldItem is FinancialPlanDetailListRow.Header && newItem is FinancialPlanDetailListRow.Header -> true
                    oldItem is FinancialPlanDetailListRow.ItemRow && newItem is FinancialPlanDetailListRow.ItemRow ->
                        oldItem.item.id == newItem.item.id
                    else -> false
                }

            override fun areContentsTheSame(oldItem: FinancialPlanDetailListRow, newItem: FinancialPlanDetailListRow): Boolean =
                oldItem == newItem
        }
    }
}
