package com.naniger.arzikina.presentation.utilities.loans

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.ItemLoanDetailHeaderBinding
import com.naniger.arzikina.databinding.ItemLoanPaymentBinding
import com.naniger.arzikina.domain.model.CurrencyAmount
import com.naniger.arzikina.domain.model.LoanPayment
import com.naniger.arzikina.domain.model.LoanType
import com.naniger.arzikina.util.AppDateFormats
import com.naniger.arzikina.util.DatePeriods
import com.naniger.arzikina.util.LoanDateTime
import com.naniger.arzikina.util.Money
import com.naniger.arzikina.util.TriggerTimeFormatter

/**
 * Liste de l'écran "Détail du prêt/emprunt" : une ligne [LoanDetailListRow.Header] (résumé +
 * informations, voir `item_loan_detail_header.xml`) suivie d'une ligne [LoanDetailListRow.PaymentRow]
 * par remboursement RÉELLEMENT enregistré — voir la doc de [LoanDetailUiState.payments]. Même
 * raisonnement `ListAdapter`/`DiffUtil` que [LoansAdapter].
 *
 * [onDeletePayment] : icône de suppression de chaque ligne [LoanDetailListRow.PaymentRow] (voir
 * `item_loan_payment.xml`) — la confirmation elle-même reste la responsabilité du Fragment (voir
 * `LoanDetailFragment.confirmDeletePayment`), cet adapter reste volontairement sans logique
 * métier/dialogue, même principe que [LoansAdapter.onLoanClick].
 */
class LoanDetailAdapter(
    private val onDeletePayment: (LoanPayment) -> Unit
) : ListAdapter<LoanDetailListRow, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is LoanDetailListRow.Header -> VIEW_TYPE_HEADER
        is LoanDetailListRow.PaymentRow -> VIEW_TYPE_PAYMENT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(ItemLoanDetailHeaderBinding.inflate(inflater, parent, false))
        } else {
            PaymentViewHolder(ItemLoanPaymentBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is LoanDetailListRow.Header -> (holder as HeaderViewHolder).bind(row.uiState)
            is LoanDetailListRow.PaymentRow -> (holder as PaymentViewHolder).bind(row, onDeletePayment)
        }
    }

    class HeaderViewHolder(private val binding: ItemLoanDetailHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(uiState: LoanDetailUiState) {
            val context = binding.root.context
            val loan = uiState.loan

            binding.personAvatar.text = personAvatarInitial(uiState.personName)
            binding.personAvatar.backgroundTintList =
                ColorStateList.valueOf(personAvatarColorArgb(uiState.personName).toInt())

            binding.loanTitle.text = loan.description.ifBlank { context.getString(defaultLoanTitleRes(loan.type)) }

            val personPrefixRes = if (loan.type == LoanType.LENT) {
                R.string.loans_person_prefix_lent
            } else {
                R.string.loans_person_prefix_borrowed
            }
            binding.personLine.text = context.getString(personPrefixRes, uiState.personName)
            binding.personLine.setTextColor(
                ContextCompat.getColor(context, if (loan.type == LoanType.LENT) R.color.loan_lent_color else R.color.expense_red)
            )

            val statusDisplay = loanStatusDisplay(loan.status, loan.type)
            binding.statusPill.text = context.getString(statusDisplay.labelRes)
            binding.statusPill.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, statusDisplay.colorRes))

            binding.amountValue.text = formatMinor(loan.amount, uiState.currencyCode)
            binding.remainingValue.text = formatMinor(loan.remainingAmount, uiState.currencyCode)
            binding.remainingValue.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (loan.remainingAmount > 0L) R.color.warning_amber else R.color.arzikina_on_balance_card_variant
                )
            )
            binding.repaidLabel.text = context.getString(R.string.loans_repaid_amount, formatMinor(loan.amountRepaid, uiState.currencyCode))
            binding.progressBar.progress = computeLoanProgressPercent(loan.amountRepaid, loan.amount)
            binding.progressPercent.text = context.getString(R.string.loans_progress_percent, binding.progressBar.progress)

            binding.createdValue.text = loan.startDate.toFormattedDate(binding.root.context)
            // Voir LoanDateTime.hasExplicitTime : aucune heure fictive (00:00) pour une ancienne
            // donnée enregistrée sans heure.
            val hasTime = LoanDateTime.hasExplicitTime(loan.startDate)
            binding.startTimeContainer.visibility = if (hasTime) View.VISIBLE else View.GONE
            if (hasTime) {
                val time = LoanDateTime.toLocalTime(loan.startDate)
                binding.startTimeValue.text = TriggerTimeFormatter.format(context, time.hour, time.minute)
            }
            binding.dueValue.text = loan.dueDate.toFormattedDate(binding.root.context)

            val description = loan.description.trim()
            binding.descriptionContainer.visibility = if (description.isEmpty()) View.GONE else View.VISIBLE
            binding.descriptionValue.text = description

            bindGiftCard(uiState)

            binding.paymentsSectionTitle.text =
                context.getString(R.string.loan_detail_payments_title, uiState.payments.size)
            binding.paymentsEmptyText.visibility = if (uiState.payments.isEmpty()) View.VISIBLE else View.GONE
        }

        /** Carte « Transformé en cadeau » (voir `item_loan_detail_header.xml`) : visible seulement
         * pour un prêt/emprunt transformé — montant offert (prêt) ou reçu (emprunt) et date. */
        private fun bindGiftCard(uiState: LoanDetailUiState) {
            val context = binding.root.context
            val loan = uiState.loan
            val isGifted = loan.giftedAmount > 0L
            binding.giftCard.visibility = if (isGifted) View.VISIBLE else View.GONE
            if (!isGifted) return

            binding.giftAmountLabel.setText(
                if (loan.type == LoanType.LENT) R.string.loan_gift_amount_label_lent else R.string.loan_gift_amount_label_borrowed
            )
            binding.giftAmountValue.text = formatMinor(loan.giftedAmount, uiState.currencyCode)
            val giftedAt = loan.giftedAt
            binding.giftDateContainer.visibility = if (giftedAt == null) View.GONE else View.VISIBLE
            if (giftedAt != null) binding.giftDateValue.text = giftedAt.toFormattedDate(context)
        }

        private fun formatMinor(amountMinor: Long, currencyCode: String): String =
            Money.format(CurrencyAmount(currencyCode, amountMinor))
    }

    class PaymentViewHolder(private val binding: ItemLoanPaymentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: LoanDetailListRow.PaymentRow, onDeletePayment: (LoanPayment) -> Unit) {
            val context = binding.root.context
            val payment = row.payment

            // Date + heure du remboursement ; heure omise pour une ancienne donnée saisie sans heure
            // (minuit technique, voir LoanDateTime.hasExplicitTime) — jamais « 00:00 » fictif.
            val paymentDay = payment.date.toFormattedDate(context)
            binding.paymentDate.text = if (LoanDateTime.hasExplicitTime(payment.date)) {
                val time = LoanDateTime.toLocalTime(payment.date)
                context.getString(R.string.loan_date_at_time, paymentDay, TriggerTimeFormatter.format(context, time.hour, time.minute))
            } else {
                paymentDay
            }
            binding.paymentAccountName.text = row.accountName

            val note = payment.note.trim()
            binding.paymentNote.visibility = if (note.isEmpty()) View.GONE else View.VISIBLE
            binding.paymentNote.text = note

            // Un remboursement fait revenir l'argent si le prêt a été ACCORDÉ (LENT), en fait
            // sortir s'il a été CONTRACTÉ (BORROWED) — même logique de signe que TransactionItemBinder.
            val isCredit = row.loanType == LoanType.LENT
            val formattedAmount = Money.format(CurrencyAmount(row.currencyCode, payment.amount))
            binding.paymentAmount.text = "${if (isCredit) "+" else "-"}$formattedAmount"
            binding.paymentAmount.setTextColor(
                ContextCompat.getColor(context, if (isCredit) R.color.loan_lent_color else R.color.expense_red)
            )

            // Masqué (pas seulement désactivé) sur un prêt/emprunt transformé en cadeau : l'action
            // n'a plus de sens, inutile de la montrer (voir LoanDetailUiState.canDeletePayments).
            binding.paymentDeleteButton.visibility = if (row.canDelete) View.VISIBLE else View.GONE
            binding.paymentDeleteButton.setOnClickListener { onDeletePayment(payment) }
        }
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_PAYMENT = 1

        /** [DatePeriods.toLocalDate] : même conversion que [com.naniger.arzikina.presentation.transactions.TransactionItemBinder],
         * mais avec l'année (contrairement au format court des transactions récentes) — un prêt/
         * emprunt s'étend souvent sur plusieurs mois, voire change d'année. */
        private fun Long.toFormattedDate(context: Context): String =
            DatePeriods.toLocalDate(this).format(AppDateFormats.longDate(context))

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<LoanDetailListRow>() {
            override fun areItemsTheSame(oldItem: LoanDetailListRow, newItem: LoanDetailListRow): Boolean = when {
                oldItem is LoanDetailListRow.Header && newItem is LoanDetailListRow.Header -> true
                oldItem is LoanDetailListRow.PaymentRow && newItem is LoanDetailListRow.PaymentRow ->
                    oldItem.payment.id == newItem.payment.id
                else -> false
            }

            override fun areContentsTheSame(oldItem: LoanDetailListRow, newItem: LoanDetailListRow): Boolean = oldItem == newItem
        }
    }
}
