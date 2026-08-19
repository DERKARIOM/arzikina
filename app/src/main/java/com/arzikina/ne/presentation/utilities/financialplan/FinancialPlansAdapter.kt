package com.arzikina.ne.presentation.utilities.financialplan

import android.view.LayoutInflater
import android.view.ViewGroup
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
 * PostCard dégradé (voir item_financial_plan.xml) : ni icône, ni bouton de suppression rapide sur
 * cette carte (redesign sur capture de référence) — la suppression reste accessible depuis
 * "Détail de la planification" (menu ⋮ > Supprimer). Réutilisé tel quel par le bloc "Mes
 * planifications" du Dashboard (voir `DashboardFragment`).
 */
class FinancialPlansAdapter(
    private val onClick: (FinancialPlanUiItem) -> Unit
) : ListAdapter<FinancialPlanUiItem, FinancialPlansAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val binding = ItemFinancialPlanBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class ViewHolder(private val binding: ItemFinancialPlanBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FinancialPlanUiItem, onClick: (FinancialPlanUiItem) -> Unit) {
            val context = binding.root.context
            val plan = item.plan

            binding.planName.text = plan.name
            binding.availableValue.text = Money.format(CurrencyAmount(Constants.DEFAULT_CURRENCY_CODE, plan.availableAmount))
            binding.plannedValue.text = Money.format(CurrencyAmount(Constants.DEFAULT_CURRENCY_CODE, item.totalPlanned))
            binding.progressBar.progress = item.progressPercent
            binding.percentUsedLabel.text = context.getString(R.string.financial_plan_card_percent_used, item.progressPercent)

            // Dépassement : le libellé passe de "Reste" à "Dépassement" et le montant devient
            // l'excédent (voir FinancialPlanProgress.calculateRemainingAmount, peut être négatif) —
            // volontairement PAS de couleur rouge ici (fond dégradé, tous les textes restent blancs
            // pour rester lisibles, voir la doc de item_financial_plan.xml) : le libellé + le
            // montant affiché suffisent à signaler l'état.
            if (item.isOverBudget) {
                binding.remainingLabel.text = context.getString(R.string.financial_plan_card_overbudget_label)
                binding.remainingValue.text =
                    Money.format(CurrencyAmount(Constants.DEFAULT_CURRENCY_CODE, -item.remainingAmount))
            } else {
                binding.remainingLabel.text = context.getString(R.string.financial_plan_card_remaining_label)
                binding.remainingValue.text =
                    Money.format(CurrencyAmount(Constants.DEFAULT_CURRENCY_CODE, item.remainingAmount))
            }

            binding.root.setOnClickListener { onClick(item) }
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
