package com.arzikina.ne.presentation.utilities.recurring

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import androidx.core.content.ContextCompat
import com.arzikina.ne.R
import com.arzikina.ne.databinding.ItemTransactionCompactBinding
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.domain.model.OccurrenceStatus
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.presentation.categories.CategoryIconMapper
import com.arzikina.ne.util.DatePeriods
import com.arzikina.ne.util.Money
import com.arzikina.ne.util.TriggerTimeFormatter
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Remplit une ligne `item_transaction_compact.xml` à partir d'un [RecurringOccurrenceUiItem] —
 * réutilise le même layout partagé que [com.arzikina.ne.presentation.transactions.TransactionItemBinder]
 * (icône/nom de catégorie + sous-titre + montant coloré, voir sa doc) plutôt que d'en dupliquer un
 * quasi identique pour cet écran : seule la logique de sous-titre/couleur diffère, propre à cet
 * écran (pas de compte/description réels tant que l'occurrence n'a pas été enregistrée).
 *
 * Pas de `runningBalance` ici (toujours masqué) : cette notion n'a de sens que pour une transaction
 * déjà enregistrée avec un historique de solde, voir `RunningBalance.kt`.
 */
object RecurringOccurrenceItemBinder {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH)

    fun bind(binding: ItemTransactionCompactBinding, item: RecurringOccurrenceUiItem, section: RecurringSection) {
        val context = binding.root.context
        val category = item.category
        val rule = item.recurringTransaction

        binding.categoryIcon.setImageResource(
            category?.let { CategoryIconMapper.iconFor(it.icon) } ?: R.drawable.ic_category_other_24
        )
        val circleColor = category?.colorArgb?.toInt() ?: ContextCompat.getColor(context, R.color.arzikina_outline)
        binding.categoryIcon.backgroundTintList = ColorStateList.valueOf(circleColor)

        binding.categoryName.text = category?.name ?: context.getString(R.string.transaction_uncategorized)

        binding.subtitle.visibility = View.VISIBLE
        binding.subtitle.text = subtitleFor(context, item, section)

        val isCredit = rule.type == TransactionType.INCOME
        val amountCurrency = item.account?.currencyCode
        val formattedAmount = amountCurrency?.let {
            Money.format(CurrencyAmount(it, rule.amount))
        } ?: Money.formatAmount(rule.amount)
        binding.amount.text = "${if (isCredit) "+" else "-"}$formattedAmount"
        binding.amount.setTextColor(
            ContextCompat.getColor(
                context,
                when {
                    item.status == OccurrenceStatus.REJECTED -> R.color.arzikina_on_balance_card_variant
                    isCredit -> R.color.income_green
                    else -> R.color.expense_red
                }
            )
        )

        binding.runningBalance.visibility = View.GONE
    }

    private fun subtitleFor(context: Context, item: RecurringOccurrenceUiItem, section: RecurringSection): String {
        val date = DatePeriods.toLocalDate(item.scheduledDate).format(dateFormatter)
        return when (section) {
            // Heure de déclenchement ajoutée ici (voir TriggerTimeFormatter) : c'est précisément
            // dans ces deux sections qu'elle a du sens ("à quelle heure ceci va-t-il se déclencher
            // ?", cahier des charges section 2) — HISTORY, ci-dessous, décrit un événement déjà
            // traité, l'heure de déclenchement de la règle n'y apporterait rien d'utile.
            RecurringSection.PENDING, RecurringSection.UPCOMING -> {
                val rule = item.recurringTransaction
                val time = TriggerTimeFormatter.format(context, rule.triggerHour, rule.triggerMinute)
                context.getString(R.string.recurring_transactions_scheduled_date, date, time)
            }
            RecurringSection.HISTORY -> {
                val statusLabel = context.getString(statusLabelRes(item.status))
                // Réutilise le gabarit générique "%1$s • %2$s" (voir dashboard_transaction_subtitle),
                // pas de duplication d'un format identique pour cet écran.
                context.getString(R.string.dashboard_transaction_subtitle, statusLabel, date)
            }
        }
    }

    private fun statusLabelRes(status: OccurrenceStatus?): Int = when (status) {
        OccurrenceStatus.ACCEPTED -> R.string.recurring_occurrence_status_accepted
        OccurrenceStatus.MODIFIED -> R.string.recurring_occurrence_status_modified
        OccurrenceStatus.REJECTED -> R.string.recurring_occurrence_status_rejected
        OccurrenceStatus.PENDING, null -> R.string.recurring_transactions_pending_title
    }
}
