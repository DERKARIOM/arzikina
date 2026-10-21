package com.naniger.arzikina.presentation.utilities.issuedreceipts.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.ItemIssuedReceiptBinding
import com.naniger.arzikina.domain.issuedreceipt.ReceiptNumber
import com.naniger.arzikina.domain.issuedreceipt.pdf.ReceiptAmountFormatter
import com.naniger.arzikina.domain.model.IssuedReceiptSummary
import com.naniger.arzikina.util.AppDateFormats
import java.time.Instant
import java.time.ZoneId

/** Actions disponibles sur une ligne de l'historique (voir `IssuedReceiptHistoryFragment`). */
class IssuedReceiptHistoryActions(
    val onView: (IssuedReceiptSummary) -> Unit,
    val onOpen: (IssuedReceiptSummary) -> Unit,
    val onShare: (IssuedReceiptSummary) -> Unit,
    val onDelete: (IssuedReceiptSummary) -> Unit
)

/** Liste de l'historique : `ListAdapter`/`DiffUtil` (ids stables) pour des listes longues et fluides. */
class IssuedReceiptHistoryAdapter(
    private val actions: IssuedReceiptHistoryActions
) : ListAdapter<IssuedReceiptSummary, IssuedReceiptHistoryAdapter.ViewHolder>(DIFF) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemIssuedReceiptBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position), actions)

    class ViewHolder(private val b: ItemIssuedReceiptBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: IssuedReceiptSummary, actions: IssuedReceiptHistoryActions) {
            val context = b.root.context
            b.receiptNumber.text = item.number
            b.receiptCustomer.text = item.customerName
            val date = Instant.ofEpochMilli(item.issuedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                .format(AppDateFormats.mediumDate(context))
            // Numéro renuméroté par la synchronisation (doublon entre appareils) : prévenu sans bruit.
            b.receiptDate.text = if (ReceiptNumber.isAdjusted(item.number)) {
                context.getString(R.string.issued_receipt_history_number_adjusted, date)
            } else {
                date
            }
            b.receiptAmount.text = ReceiptAmountFormatter.format(item.amountMinor, item.currencyCode, item.amountMinor % 100L != 0L)
            b.receiptStatus.setText(if (item.hasPdf) R.string.issued_receipt_history_status_ready else R.string.issued_receipt_history_status_todo)
            b.receiptCard.setOnClickListener { actions.onView(item) }
            b.openButton.setOnClickListener { actions.onOpen(item) }
            b.shareButton.setOnClickListener { actions.onShare(item) }
            b.deleteButton.setOnClickListener { actions.onDelete(item) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<IssuedReceiptSummary>() {
            override fun areItemsTheSame(a: IssuedReceiptSummary, b: IssuedReceiptSummary) = a.id == b.id
            override fun areContentsTheSame(a: IssuedReceiptSummary, b: IssuedReceiptSummary) = a == b
        }
    }
}
