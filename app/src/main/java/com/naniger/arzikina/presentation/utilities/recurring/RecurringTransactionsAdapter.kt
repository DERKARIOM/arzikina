package com.naniger.arzikina.presentation.utilities.recurring

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.naniger.arzikina.databinding.ItemRecurringPendingOccurrenceBinding
import com.naniger.arzikina.databinding.ItemRecurringSectionTitleBinding
import com.naniger.arzikina.databinding.ItemRecurringSummaryHeaderBinding
import com.naniger.arzikina.databinding.ItemTransactionCompactBinding

/**
 * Liste de l'écran "Transactions planifiées" : une ligne [RecurringTransactionsListRow.Header]
 * (cartes de résumé) suivie des sections "À traiter"/"À venir"/"Historique" — voir la doc de
 * [RecurringTransactionsListRow]. Même raisonnement `ListAdapter`/`DiffUtil` que
 * `com.arzikina.ne.presentation.utilities.loans.LoansAdapter`.
 *
 * "À traiter" a désormais son PROPRE type de vue ([VIEW_TYPE_PENDING_OCCURRENCE],
 * `item_recurring_pending_occurrence.xml`) avec Valider/Rejeter directement sur la ligne (voir
 * cahier des charges "Supprimer le dialogue au lancement") — "À venir"/"Historique" continuent de
 * partager [VIEW_TYPE_OCCURRENCE] (`item_transaction_compact.xml` seul, sans bouton).
 */
class RecurringTransactionsAdapter(
    // "À traiter" (tap sur le résumé) ET "À venir" ouvrent chacun un écran différent au tap (voir
    // RecurringTransactionsFragment.onOccurrenceRowClick) ; "Historique" reste inerte.
    private val onOccurrenceClick: (RecurringOccurrenceUiItem, RecurringSection) -> Unit,
    private val onAccept: (Long) -> Unit,
    private val onReject: (Long) -> Unit
) : ListAdapter<RecurringTransactionsListRow, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    override fun getItemViewType(position: Int): Int = when (val row = getItem(position)) {
        is RecurringTransactionsListRow.Header -> VIEW_TYPE_HEADER
        is RecurringTransactionsListRow.SectionTitle -> VIEW_TYPE_SECTION_TITLE
        is RecurringTransactionsListRow.OccurrenceRow ->
            if (row.section == RecurringSection.PENDING) VIEW_TYPE_PENDING_OCCURRENCE else VIEW_TYPE_OCCURRENCE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(ItemRecurringSummaryHeaderBinding.inflate(inflater, parent, false))
            VIEW_TYPE_SECTION_TITLE -> SectionTitleViewHolder(ItemRecurringSectionTitleBinding.inflate(inflater, parent, false))
            VIEW_TYPE_PENDING_OCCURRENCE ->
                PendingOccurrenceViewHolder(ItemRecurringPendingOccurrenceBinding.inflate(inflater, parent, false))
            else -> OccurrenceViewHolder(ItemTransactionCompactBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is RecurringTransactionsListRow.Header -> (holder as HeaderViewHolder).bind(row.summary)
            is RecurringTransactionsListRow.SectionTitle -> (holder as SectionTitleViewHolder).bind(row)
            is RecurringTransactionsListRow.OccurrenceRow -> when (holder) {
                is PendingOccurrenceViewHolder -> holder.bind(row, onOccurrenceClick, onAccept, onReject)
                is OccurrenceViewHolder -> holder.bind(row, onOccurrenceClick)
                else -> Unit
            }
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

    /** "À traiter" — voir la doc de tête de cette classe. [onAccept]/[onReject] ignorés (pas
     *  d'exception levée) si [RecurringOccurrenceUiItem.occurrenceId] est `null` : ne devrait jamais
     *  arriver pour cette section (voir sa doc, toujours une VRAIE occurrence en base), simple garde-
     *  fou défensif plutôt qu'une supposition non vérifiée. */
    class PendingOccurrenceViewHolder(
        private val binding: ItemRecurringPendingOccurrenceBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            row: RecurringTransactionsListRow.OccurrenceRow,
            onClick: (RecurringOccurrenceUiItem, RecurringSection) -> Unit,
            onAccept: (Long) -> Unit,
            onReject: (Long) -> Unit
        ) {
            RecurringOccurrenceItemBinder.bind(binding.occurrenceSummary, row.item, row.section)
            binding.occurrenceSummary.root.setOnClickListener { onClick(row.item, row.section) }
            val occurrenceId = row.item.occurrenceId
            binding.acceptButton.setOnClickListener { occurrenceId?.let(onAccept) }
            binding.rejectButton.setOnClickListener { occurrenceId?.let(onReject) }
        }
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_SECTION_TITLE = 1
        const val VIEW_TYPE_OCCURRENCE = 2
        const val VIEW_TYPE_PENDING_OCCURRENCE = 3

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
