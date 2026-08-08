package com.arzikina.ne.presentation.transactions

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import androidx.core.content.ContextCompat
import com.arzikina.ne.R
import com.arzikina.ne.databinding.ItemTransactionCompactBinding
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.model.PaymentMethod
import com.arzikina.ne.presentation.categories.CategoryIconMapper
import com.arzikina.ne.util.DatePeriods
import com.arzikina.ne.util.Money
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Remplit une ligne `item_transaction_compact.xml` à partir d'un
 * [TransactionUiItem]. Partagé entre [com.arzikina.ne.presentation.dashboard.RecentTransactionsAdapter]
 * (lecture seule) et [GroupedTransactionsAdapter] (avec ou sans suppression
 * selon l'écran) : seule la gestion des clics diffère d'un écran à l'autre,
 * la mise en forme du contenu est identique et ne doit pas être dupliquée.
 */
object TransactionItemBinder {

    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)

    /**
     * [showDescriptionSubtitle] bascule le sous-titre entre les deux
     * présentations de l'application :
     * - `false` (défaut, utilisé par le Dashboard) : "Compte • date", complété
     *   du moyen de paiement s'il est précisé (voir [PaymentMethod]).
     * - `true` (écrans Transactions et "Détail du compte", groupés par jour,
     *   où le compte/la date sont déjà visibles via l'en-tête de section) :
     *   la description libre de la transaction, masquée si elle est vide.
     */
    fun bind(binding: ItemTransactionCompactBinding, item: TransactionUiItem, showDescriptionSubtitle: Boolean = false) {
        val context = binding.root.context
        val category = item.category

        binding.categoryIcon.setImageResource(
            category?.let { CategoryIconMapper.iconFor(it.icon) } ?: R.drawable.ic_category_other_24
        )
        val circleColor = category?.colorArgb?.toInt()
            ?: ContextCompat.getColor(context, R.color.arzikina_outline)
        binding.categoryIcon.backgroundTintList = ColorStateList.valueOf(circleColor)

        binding.categoryName.text = category?.name
            ?: context.getString(R.string.transaction_uncategorized)

        binding.subtitle.setTypeface(binding.subtitle.typeface, if (showDescriptionSubtitle) Typeface.ITALIC else Typeface.NORMAL)
        if (showDescriptionSubtitle) {
            val description = item.transaction.description.trim()
            binding.subtitle.visibility = if (description.isEmpty()) View.GONE else View.VISIBLE
            binding.subtitle.text = description
        } else {
            binding.subtitle.visibility = View.VISIBLE
            val base = run {
                val accountName = item.account?.name ?: context.getString(R.string.transaction_unknown_account)
                val date = DatePeriods.toLocalDate(item.transaction.date).format(dateFormatter)
                context.getString(R.string.dashboard_transaction_subtitle, accountName, date)
            }
            binding.subtitle.text = item.transaction.paymentMethod?.let { method ->
                context.getString(R.string.dashboard_transaction_subtitle, base, context.getString(method.displayTextRes()))
            } ?: base
        }

        val isIncome = item.transaction.type == TransactionType.INCOME
        val amountCurrency = item.account?.currencyCode
        val formattedAmount = amountCurrency?.let {
            Money.format(CurrencyAmount(it, item.transaction.amount))
        } ?: Money.formatMajorUnits(item.transaction.amount)
        binding.amount.text = "${if (isIncome) "+" else "-"}$formattedAmount"
        binding.amount.setTextColor(
            ContextCompat.getColor(context, if (isIncome) R.color.income_green else R.color.expense_red)
        )

        if (item.runningBalance != null && amountCurrency != null) {
            binding.runningBalance.visibility = View.VISIBLE
            binding.runningBalance.text = "(${Money.format(CurrencyAmount(amountCurrency, item.runningBalance))})"
        } else {
            binding.runningBalance.visibility = View.GONE
        }
    }
}
