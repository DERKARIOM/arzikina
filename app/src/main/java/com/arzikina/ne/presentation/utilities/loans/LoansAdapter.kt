package com.arzikina.ne.presentation.utilities.loans

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.R
import com.arzikina.ne.databinding.ItemLoanBinding
import com.arzikina.ne.databinding.ItemLoanSummaryHeaderBinding
import com.arzikina.ne.databinding.ItemLoansNoResultsBinding
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.domain.model.LoanType
import com.arzikina.ne.util.Money

/**
 * Liste de l'écran principal Prêts/Emprunts : une ligne [LoansListRow.Header] (cartes de résumé)
 * suivie soit d'une ligne [LoansListRow.LoanRow] par prêt/emprunt, soit d'une unique ligne
 * [LoansListRow.NoResults] si la recherche/le filtre courant n'en retourne aucun — voir la doc de
 * [LoansListRow]. Même raisonnement `ListAdapter`/`DiffUtil` que
 * [com.arzikina.ne.presentation.dashboard.RecentTransactionsAdapter].
 */
class LoansAdapter(
    private val onLoanClick: (LoanListItem) -> Unit
) : ListAdapter<LoansListRow, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is LoansListRow.Header -> VIEW_TYPE_HEADER
        is LoansListRow.LoanRow -> VIEW_TYPE_LOAN
        is LoansListRow.NoResults -> VIEW_TYPE_NO_RESULTS
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(ItemLoanSummaryHeaderBinding.inflate(inflater, parent, false))
            VIEW_TYPE_LOAN -> LoanViewHolder(ItemLoanBinding.inflate(inflater, parent, false))
            else -> NoResultsViewHolder(ItemLoansNoResultsBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is LoansListRow.Header -> (holder as HeaderViewHolder).bind(row.summary)
            is LoansListRow.LoanRow -> (holder as LoanViewHolder).bind(row.item, onLoanClick)
            is LoansListRow.NoResults -> Unit
        }
    }

    class HeaderViewHolder(private val binding: ItemLoanSummaryHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(summary: LoansSummary) {
            val context = binding.root.context
            binding.receivableAmount.text = formatCurrencyAmounts(summary.totalReceivable)
            binding.receivableCount.text = context.getString(R.string.loans_summary_receivable_count, summary.lentCount)
            binding.owedAmount.text = formatCurrencyAmounts(summary.totalOwed)
            binding.owedCount.text = context.getString(R.string.loans_summary_owed_count, summary.borrowedCount)
            binding.listCount.text = context.getString(R.string.loans_list_count, summary.totalCount)
        }
    }

    class LoanViewHolder(private val binding: ItemLoanBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LoanListItem, onClick: (LoanListItem) -> Unit) {
            val context = binding.root.context

            binding.personAvatar.text = personAvatarInitial(item.personName)
            binding.personAvatar.backgroundTintList =
                ColorStateList.valueOf(personAvatarColorArgb(item.personName).toInt())

            binding.loanTitle.text = item.title.ifBlank { context.getString(defaultLoanTitleRes(item.type)) }

            val personPrefixRes = if (item.type == LoanType.LENT) {
                R.string.loans_person_prefix_lent
            } else {
                R.string.loans_person_prefix_borrowed
            }
            binding.personLine.text = context.getString(personPrefixRes, item.personName)
            binding.personLine.setTextColor(
                ContextCompat.getColor(context, if (item.type == LoanType.LENT) R.color.loan_lent_color else R.color.expense_red)
            )

            val statusDisplay = loanStatusDisplay(item.status, item.type)
            binding.statusPill.text = context.getString(statusDisplay.labelRes)
            binding.statusPill.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, statusDisplay.colorRes))

            binding.amountValue.text = formatMinor(item.amountMinor, item.currencyCode)
            binding.remainingValue.text = formatMinor(item.remainingAmountMinor, item.currencyCode)
            // "Restant" en ambre tant qu'il reste quelque chose à régler, neutre une fois soldé
            // (voir maquette) — indépendant de LoanType : la pastille de statut porte déjà cette
            // distinction, pas besoin de la répéter ici avec un troisième code couleur.
            binding.remainingValue.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (item.remainingAmountMinor > 0L) R.color.warning_amber else R.color.arzikina_on_balance_card_variant
                )
            )

            binding.repaidLabel.text = context.getString(
                R.string.loans_repaid_amount,
                formatMinor(item.amountRepaidMinor, item.currencyCode)
            )

            binding.progressBar.progress = item.progressPercent
            binding.progressPercent.text = context.getString(R.string.loans_progress_percent, item.progressPercent)

            binding.loanCard.setOnClickListener { onClick(item) }
        }

        private fun formatMinor(amountMinor: Long, currencyCode: String): String =
            Money.format(CurrencyAmount(currencyCode, amountMinor))
    }

    /** Contenu entièrement statique (voir `item_loans_no_results.xml`) : rien à lier. */
    class NoResultsViewHolder(binding: ItemLoansNoResultsBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_LOAN = 1
        const val VIEW_TYPE_NO_RESULTS = 2

        /** Une ligne par devise (voir la doc de [LoansSummary]) ; "—" si la liste est vide — même
         * convention que `DashboardFragment.formatAmounts`. */
        fun formatCurrencyAmounts(amounts: List<CurrencyAmount>): String =
            if (amounts.isEmpty()) "—" else amounts.joinToString("\n") { Money.format(it) }

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<LoansListRow>() {
            override fun areItemsTheSame(oldItem: LoansListRow, newItem: LoansListRow): Boolean = when {
                oldItem is LoansListRow.Header && newItem is LoansListRow.Header -> true
                oldItem is LoansListRow.LoanRow && newItem is LoansListRow.LoanRow -> oldItem.item.id == newItem.item.id
                oldItem is LoansListRow.NoResults && newItem is LoansListRow.NoResults -> true
                else -> false
            }

            override fun areContentsTheSame(oldItem: LoansListRow, newItem: LoansListRow): Boolean = oldItem == newItem
        }
    }
}
