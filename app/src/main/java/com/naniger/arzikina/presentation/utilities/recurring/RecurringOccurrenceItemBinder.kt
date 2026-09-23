package com.naniger.arzikina.presentation.utilities.recurring

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import androidx.core.content.ContextCompat
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.ItemTransactionCompactBinding
import com.naniger.arzikina.domain.model.OccurrenceStatus
import com.naniger.arzikina.domain.model.TransactionType
import com.naniger.arzikina.presentation.categories.CategoryIconMapper
import com.naniger.arzikina.presentation.transactions.TransactionAmountTone
import com.naniger.arzikina.presentation.transactions.transactionAmountDisplay
import com.naniger.arzikina.util.DatePeriods
import com.naniger.arzikina.util.TriggerTimeFormatter
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Remplit une ligne `item_transaction_compact.xml` à partir d'un [RecurringOccurrenceUiItem] —
 * réutilise le même layout partagé que [com.naniger.arzikina.presentation.transactions.TransactionItemBinder]
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

        // Plus de signe +/- (voir TransactionAmountDisplay.kt) : une occurrence REJETÉE garde son
        // ton NEUTRE (gris) même si sa règle est un revenu/une dépense normal, exactement comme
        // avant — seule la présentation change, pas les cas déjà couverts.
        val tone = when {
            item.status == OccurrenceStatus.REJECTED -> TransactionAmountTone.NEUTRAL
            rule.type == TransactionType.INCOME -> TransactionAmountTone.INCOME
            else -> TransactionAmountTone.EXPENSE
        }
        val amountCurrency = item.account?.currencyCode
        val amountDisplay = transactionAmountDisplay(rule.amount, amountCurrency, tone)
        binding.amount.text = amountDisplay.text
        binding.amount.setTextColor(ContextCompat.getColor(context, amountDisplay.colorRes))

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
