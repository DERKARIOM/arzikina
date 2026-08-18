package com.arzikina.ne.presentation.utilities.financialplan

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.R
import com.arzikina.ne.databinding.ItemFinancialPlanBinding
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.util.Constants
import com.arzikina.ne.util.Money

/**
 * Liste des planifications avec leur progression — voir [com.arzikina.ne.presentation.budget.BudgetAdapter]
 * pour le même principe (`ListAdapter`/`DiffUtil`).
 *
 * Aucune notion de devise par planification pour cette première version (voir [FinancialPlan] —
 * pas de champ `currencyCode`) : affichage dans la devise par défaut de l'application, comme les
 * autres montants sans compte associé.
 *
 * [showDeleteButton] (Étape 9) : `false` réutilisé par le bloc "Mes planifications" du Dashboard
 * (voir `DashboardFragment`), pour lequel aucune suppression rapide n'a de sens sur un simple
 * aperçu — même raisonnement que `budgetPreview.deleteButton.visibility = View.GONE` sur le bloc
 * Budget du Dashboard, mais ici via un paramètre plutôt qu'un accès direct puisque cet adapter,
 * contrairement à `BudgetAdapter.ViewHolder`, est réutilisé pour une VRAIE liste (plusieurs
 * planifications), pas un seul élément posé à la main.
 */
class FinancialPlansAdapter(
    private val onClick: (FinancialPlanUiItem) -> Unit,
    private val onDeleteClick: (FinancialPlanUiItem) -> Unit,
    private val showDeleteButton: Boolean = true
) : ListAdapter<FinancialPlanUiItem, FinancialPlansAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val binding = ItemFinancialPlanBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClick, onDeleteClick, showDeleteButton)
    }

    class ViewHolder(private val binding: ItemFinancialPlanBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: FinancialPlanUiItem,
            onClick: (FinancialPlanUiItem) -> Unit,
            onDeleteClick: (FinancialPlanUiItem) -> Unit,
            showDeleteButton: Boolean = true
        ) {
            val context = binding.root.context
            val plan = item.plan

            binding.planIcon.setImageResource(FinancialPlanIconMapper.iconFor(plan.icon))
            binding.planIcon.backgroundTintList = ColorStateList.valueOf(plan.colorArgb.toInt())
            binding.planName.text = plan.name

            binding.availableValue.text = Money.format(CurrencyAmount(Constants.DEFAULT_CURRENCY_CODE, plan.availableAmount))
            binding.plannedValue.text = Money.format(CurrencyAmount(Constants.DEFAULT_CURRENCY_CODE, item.totalPlanned))

            binding.progressBar.progress = item.progressPercent
            binding.progressBar.setIndicatorColor(plan.colorArgb.toInt())

            val overBudgetColor = ContextCompat.getColor(context, R.color.expense_red)
            val normalColor = ContextCompat.getColor(context, R.color.arzikina_on_surface_variant)

            if (item.isOverBudget) {
                val overspentAmount = Money.format(
                    CurrencyAmount(Constants.DEFAULT_CURRENCY_CODE, -item.remainingAmount)
                )
                binding.remainingLabel.text = context.getString(R.string.financial_plan_overbudget_prefix, overspentAmount)
                binding.remainingLabel.setTextColor(overBudgetColor)
                binding.progressBar.setIndicatorColor(overBudgetColor)
            } else {
                val remainingAmount = Money.format(CurrencyAmount(Constants.DEFAULT_CURRENCY_CODE, item.remainingAmount))
                binding.remainingLabel.text = context.getString(R.string.financial_plan_remaining_prefix, remainingAmount)
                binding.remainingLabel.setTextColor(normalColor)
            }

            binding.root.setOnClickListener { onClick(item) }
            binding.deleteButton.setOnClickListener { onDeleteClick(item) }
            binding.deleteButton.visibility = if (showDeleteButton) View.VISIBLE else View.GONE
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<FinancialPlanUiItem>() {
            override fun areItemsTheSame(oldItem: FinancialPlanUiItem, newItem: FinancialPlanUiItem): Boolean =
                oldItem.plan.id == newItem.plan.id

            override fun areContentsTheSame(oldItem: FinancialPlanUiItem, newItem: FinancialPlanUiItem): Boolean =
                oldItem == newItem
        }
    }
}
