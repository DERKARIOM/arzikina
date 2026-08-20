package com.arzikina.ne.presentation.utilities.recurring

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.R
import com.arzikina.ne.databinding.ItemRecurringSectionTitleBinding
import com.arzikina.ne.databinding.ItemRecurringSummaryHeaderBinding
import com.arzikina.ne.databinding.ItemTransactionCompactBinding

/**
 * Liste de l'écran "Transactions planifiées" : une ligne [RecurringTransactionsListRow.Header]
 * (cartes de résumé) suivie des sections "À traiter"/"À venir"/"Historique" — voir la doc de
 * [RecurringTransactionsListRow]. Même raisonnement `ListAdapter`/`DiffUtil` que
 * `com.arzikina.ne.presentation.utilities.loans.LoansAdapter`.
 */
class RecurringTransactionsAdapter(
    // La section est transmise en plus de l'item : seule "À venir" doit réagir au tap pour l'instant
    // (voir RecurringTransactionsFragment.onOccurrenceRowClick), "À traiter" restant réservée à un
    // futur dialogue de validation distinct de l'édition de la règle.
    private val onOccurrenceClick: (RecurringOccurrenceUiItem, RecurringSection) -> Unit
) : ListAdapter<RecurringTransactionsListRow, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is RecurringTransactionsListRow.Header -> VIEW_TYPE_HEADER
        is RecurringTransactionsListRow.SectionTitle -> VIEW_TYPE_SECTION_TITLE
        is RecurringTransactionsListRow.OccurrenceRow -> VIEW_TYPE_OCCURRENCE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(ItemRecurringSummaryHeaderBinding.inflate(inflater, parent, false))
            VIEW_TYPE_SECTION_TITLE -> SectionTitleViewHolder(ItemRecurringSectionTitleBinding.inflate(inflater, parent, false))
            else -> OccurrenceViewHolder(ItemTransactionCompactBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is RecurringTransactionsListRow.Header -> (holder as HeaderViewHolder).bind(row.summary)
            is RecurringTransactionsListRow.SectionTitle -> (holder as SectionTitleViewHolder).bind(row)
            is RecurringTransactionsListRow.OccurrenceRow -> (holder as OccurrenceViewHolder).bind(row, onOccurrenceClick)
        }
    }

    class HeaderViewHolder(private val binding: ItemRecurringSummaryHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(summary: RecurringTransactionsSummary) {
            binding.pendingCount.text = summary.pendingCount.toString()
            binding.upcomingCount.text = summary.upcomingCount.toString()
            binding.totalCount.text = summary.totalCount.toString()
        }
    }

    class SectionTitleViewHolder(private val binding: ItemRecurringSectionTitleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: RecurringTransactionsListRow.SectionTitle) {
            val context = binding.root.context
            binding.sectionTitle.text = context.getString(row.titleRes)
            binding.sectionCount.text = row.count.toString()
        }
    }

    class OccurrenceViewHolder(private val binding: ItemTransactionCompactBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: RecurringTransactionsListRow.OccurrenceRow, onClick: (RecurringOccurrenceUiItem, RecurringSection) -> Unit) {
            RecurringOccurrenceItemBinder.bind(binding, row.item, row.section)
            binding.root.setOnClickListener { onClick(row.item, row.section) }
        }
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_SECTION_TITLE = 1
        const val VIEW_TYPE_OCCURRENCE = 2

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RecurringTransactionsListRow>() {
            override fun areItemsTheSame(oldItem: RecurringTransactionsListRow, newItem: RecurringTransactionsListRow): Boolean = when {
                oldItem is RecurringTransactionsListRow.Header && newItem is RecurringTransactionsListRow.Header -> true
                oldItem is RecurringTransactionsListRow.SectionTitle && newItem is RecurringTransactionsListRow.SectionTitle ->
                    oldItem.titleRes == newItem.titleRes
                oldItem is RecurringTransactionsListRow.OccurrenceRow && newItem is RecurringTransactionsListRow.OccurrenceRow ->
                    oldItem.section == newItem.section &&
                        (oldItem.item.occurrenceId ?: oldItem.item.recurringTransaction.id) ==
                        (newItem.item.occurrenceId ?: newItem.item.recurringTransaction.id)
                else -> false
            }

            override fun areContentsTheSame(oldItem: RecurringTransactionsListRow, newItem: RecurringTransactionsListRow): Boolean =
                oldItem == newItem
        }
    }
}
