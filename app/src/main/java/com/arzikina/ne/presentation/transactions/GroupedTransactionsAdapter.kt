package com.arzikina.ne.presentation.transactions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.R
import com.arzikina.ne.databinding.ItemTransactionCompactBinding
import com.arzikina.ne.databinding.ItemTransactionDayHeaderBinding
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.util.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Une ligne de liste groupée par jour : soit un en-tête ([Header]), soit une transaction ([Row]). */
sealed interface TransactionListRow {
    data class Header(val section: TransactionDaySection) : TransactionListRow
    data class Row(val item: TransactionUiItem) : TransactionListRow
}

/** Aplatit [sections] en une liste de lignes prête pour [GroupedTransactionsAdapter]. */
fun List<TransactionDaySection>.toListRows(): List<TransactionListRow> =
    flatMap { section ->
        listOf(TransactionListRow.Header(section)) + section.items.map { TransactionListRow.Row(it) }
    }

/**
 * Liste de transactions groupées par jour (en-têtes "Aujourd'hui"/"Hier"/date
 * + total du jour, transactions avec solde après passage — voir
 * [TransactionItemBinder]), utilisée par l'écran Transactions et "Détail du
 * compte" — un seul adapter pour ne pas dupliquer la logique de rendu
 * multi-type entre les deux écrans.
 *
 * [onDeleteClick] : `null` masque le bouton de suppression (cas "Détail du
 * compte", où seul le menu "⋮" du compte gère la suppression) ; non-null
 * l'affiche (cas écran Transactions).
 */
class GroupedTransactionsAdapter(
    private val onClick: (TransactionUiItem) -> Unit,
    private val onDeleteClick: ((TransactionUiItem) -> Unit)? = null
) : ListAdapter<TransactionListRow, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is TransactionListRow.Header -> VIEW_TYPE_HEADER
        is TransactionListRow.Row -> VIEW_TYPE_TRANSACTION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(ItemTransactionDayHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        } else {
            TransactionViewHolder(ItemTransactionCompactBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is TransactionListRow.Header -> (holder as HeaderViewHolder).bind(row.section)
            is TransactionListRow.Row ->
                (holder as TransactionViewHolder).bind(row.item, onClick, onDeleteClick)
        }
    }

    /**
     * [dayDate] affiche toujours la date complète ("08/08/2026" pour
     * Aujourd'hui/Hier comme pour les jours plus anciens — dérivée de
     * [LocalDate.now] plutôt que stockée sur [DayLabel], qui n'en a pas
     * besoin ailleurs). [dayRelativeLabel] n'apparaît que pour
     * Aujourd'hui/Hier. [dayTotal] est le total NET de la journée (revenus -
     * dépenses) ; formaté avec la devise du premier compte rencontré ce
     * jour-là — limite acceptée si une journée mélange plusieurs devises (cas
     * rare), même compromis que documenté ailleurs dans l'app (ex. widget
     * revenu/dépense du Dashboard).
     */
    class HeaderViewHolder(private val binding: ItemTransactionDayHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(section: TransactionDaySection) {
            val context = binding.root.context
            val label = section.label

            val date = when (label) {
                DayLabel.Today -> LocalDate.now()
                DayLabel.Yesterday -> LocalDate.now().minusDays(1)
                is DayLabel.Other -> label.date
            }
            binding.dayDate.text = date.format(dateFormatter)

            val relativeLabelText = when (label) {
                DayLabel.Today -> context.getString(R.string.transaction_day_today)
                DayLabel.Yesterday -> context.getString(R.string.transaction_day_yesterday)
                is DayLabel.Other -> null
            }
            binding.dayRelativeLabel.visibility = if (relativeLabelText != null) View.VISIBLE else View.GONE
            binding.dayRelativeLabel.text = relativeLabelText.orEmpty()

            val total = computeDayTotal(section.items)
            if (total != null) {
                binding.dayTotal.visibility = View.VISIBLE
                binding.dayTotal.text = total.formatted
                binding.dayTotal.setTextColor(ContextCompat.getColor(context, total.colorRes))
            } else {
                binding.dayTotal.visibility = View.GONE
            }
        }

        private fun computeDayTotal(items: List<TransactionUiItem>): DayTotal? {
            val currencyCode = items.firstNotNullOfOrNull { it.account?.currencyCode } ?: return null
            val net = items.sumOf { item ->
                if (item.transaction.type == TransactionType.INCOME) item.transaction.amount else -item.transaction.amount
            }
            val formatted = Money.format(CurrencyAmount(currencyCode, kotlin.math.abs(net)))
            return DayTotal(
                formatted = "${if (net < 0) "-" else "+"}$formatted",
                colorRes = if (net < 0) R.color.expense_red else R.color.income_green
            )
        }

        private data class DayTotal(val formatted: String, val colorRes: Int)

        private companion object {
            val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH)
        }
    }

    class TransactionViewHolder(private val binding: ItemTransactionCompactBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: TransactionUiItem,
            onClick: (TransactionUiItem) -> Unit,
            onDeleteClick: ((TransactionUiItem) -> Unit)?
        ) {
            TransactionItemBinder.bind(binding, item, showDescriptionSubtitle = true)
            binding.root.setOnClickListener { onClick(item) }
            binding.deleteButton.visibility = if (onDeleteClick != null) View.VISIBLE else View.GONE
            if (onDeleteClick != null) {
                binding.deleteButton.setOnClickListener { onDeleteClick(item) }
            }
        }
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_TRANSACTION = 1

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TransactionListRow>() {
            override fun areItemsTheSame(oldItem: TransactionListRow, newItem: TransactionListRow): Boolean =
                when {
                    oldItem is TransactionListRow.Header && newItem is TransactionListRow.Header ->
                        oldItem.section.label == newItem.section.label
                    oldItem is TransactionListRow.Row && newItem is TransactionListRow.Row ->
                        oldItem.item.transaction.id == newItem.item.transaction.id
                    else -> false
                }

            override fun areContentsTheSame(oldItem: TransactionListRow, newItem: TransactionListRow): Boolean =
                oldItem == newItem
        }
    }
}
