package com.arzikina.ne.presentation.utilities.receipts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.R
import com.arzikina.ne.databinding.ItemReceiptBinding
import com.arzikina.ne.databinding.ItemReceiptDayHeaderBinding
import com.arzikina.ne.domain.model.Receipt
import com.arzikina.ne.util.DatePeriods
import com.arzikina.ne.util.DayLabel
import com.arzikina.ne.util.FileSizeFormatter
import com.arzikina.ne.util.Money
import com.arzikina.ne.util.TriggerTimeFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Une ligne de liste groupée par jour : soit un en-tête ([Header]), soit un reçu ([Row]) — même
 * principe que `presentation.transactions.TransactionListRow`. */
sealed interface ReceiptListRow {
    data class Header(val section: ReceiptDaySection) : ReceiptListRow
    data class Row(val receipt: Receipt) : ReceiptListRow
}

/** Aplatit [sections] en une liste de lignes prête pour [ReceiptsAdapter]. */
fun List<ReceiptDaySection>.toListRows(): List<ReceiptListRow> =
    flatMap { section ->
        listOf(ReceiptListRow.Header(section)) + section.items.map { ReceiptListRow.Row(it) }
    }

/**
 * Liste des reçus groupés par jour (en-têtes "Aujourd'hui"/"Hier"/date, cartes individuelles
 * claires — voir `item_receipt.xml`/`item_receipt_day_header.xml` pour le raisonnement visuel).
 *
 * [onClick] ouvrira "Détail du reçu" à partir de l'Étape 6/8 (écran + navigation pas encore
 * construits à l'Étape 5) — le callback est déjà posé ici pour ne pas retoucher cet adapter plus
 * tard, même principe que `GroupedTransactionsAdapter`/`LoansAdapter`.
 */
class ReceiptsAdapter(
    private val onClick: (Receipt) -> Unit
) : ListAdapter<ReceiptListRow, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ReceiptListRow.Header -> VIEW_TYPE_HEADER
        is ReceiptListRow.Row -> VIEW_TYPE_RECEIPT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(ItemReceiptDayHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        } else {
            ReceiptViewHolder(ItemReceiptBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is ReceiptListRow.Header -> (holder as HeaderViewHolder).bind(row.section)
            is ReceiptListRow.Row -> (holder as ReceiptViewHolder).bind(row.receipt, onClick)
        }
    }

    /** Même principe que `GroupedTransactionsAdapter.HeaderViewHolder`, sans total (voir
     * `item_receipt_day_header.xml`). */
    class HeaderViewHolder(private val binding: ItemReceiptDayHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(section: ReceiptDaySection) {
            val context = binding.root.context
            val label = section.label

            val date = when (label) {
                DayLabel.Today -> LocalDate.now()
                DayLabel.Yesterday -> LocalDate.now().minusDays(1)
                is DayLabel.Other -> label.date
            }

            val relativeLabelText = when (label) {
                DayLabel.Today -> context.getString(R.string.transaction_day_today)
                DayLabel.Yesterday -> context.getString(R.string.transaction_day_yesterday)
                is DayLabel.Other -> null
            }
            binding.dayRelativeLabel.visibility = if (relativeLabelText != null) {
                View.VISIBLE
            } else {
                View.GONE
            }
            binding.dayRelativeLabel.text = relativeLabelText.orEmpty()
            binding.dayDate.text = date.format(dateFormatter)
        }

        private companion object {
            val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH)
        }
    }

    class ReceiptViewHolder(private val binding: ItemReceiptBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(receipt: Receipt, onClick: (Receipt) -> Unit) {
            val context = binding.root.context

            binding.receiptName.text = receipt.fileName

            val receivedTime = DatePeriods.toLocalTime(receipt.receivedAt)
            val time = TriggerTimeFormatter.format(context, receivedTime.hour, receivedTime.minute)
            val size = FileSizeFormatter.format(receipt.fileSize)
            binding.receiptMetaLine.text = context.getString(R.string.receipt_meta_line_format, time, size)

            binding.receiptSourceLine.text = receipt.sourceName
                ?: context.getString(R.string.receipt_source_unknown)

            // Toujours `null` dans cette première version (voir Receipt.amountMinor) : cette
            // branche restera simplement inatteinte tant qu'aucune extraction ne le renseigne.
            val amountMinor = receipt.amountMinor
            if (amountMinor != null) {
                binding.receiptAmount.visibility = View.VISIBLE
                binding.receiptAmount.text = Money.formatAmount(amountMinor)
            } else {
                binding.receiptAmount.visibility = View.GONE
            }

            binding.root.setOnClickListener { onClick(receipt) }
        }
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_RECEIPT = 1

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ReceiptListRow>() {
            override fun areItemsTheSame(oldItem: ReceiptListRow, newItem: ReceiptListRow): Boolean =
                when {
                    oldItem is ReceiptListRow.Header && newItem is ReceiptListRow.Header ->
                        oldItem.section.label == newItem.section.label
                    oldItem is ReceiptListRow.Row && newItem is ReceiptListRow.Row ->
                        oldItem.receipt.id == newItem.receipt.id
                    else -> false
                }

            override fun areContentsTheSame(oldItem: ReceiptListRow, newItem: ReceiptListRow): Boolean =
                oldItem == newItem
        }
    }
}
