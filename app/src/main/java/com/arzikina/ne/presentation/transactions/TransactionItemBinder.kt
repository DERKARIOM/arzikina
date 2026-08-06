package com.arzikina.ne.presentation.transactions

import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.arzikina.ne.R
import com.arzikina.ne.databinding.ItemTransactionCompactBinding
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.presentation.categories.CategoryIconMapper
import com.arzikina.ne.util.DatePeriods
import com.arzikina.ne.util.Money
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Remplit une ligne `item_transaction_compact.xml` à partir d'un
 * [TransactionUiItem]. Partagé entre [com.arzikina.ne.presentation.dashboard.RecentTransactionsAdapter]
 * (lecture seule) et [TransactionsAdapter] (avec suppression) : seule la
 * gestion des clics diffère d'un écran à l'autre, la mise en forme du
 * contenu est identique et ne doit pas être dupliquée.
 */
object TransactionItemBinder {

    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)

    fun bind(binding: ItemTransactionCompactBinding, item: TransactionUiItem) {
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

        val accountName = item.account?.name ?: context.getString(R.string.transaction_unknown_account)
        val date = DatePeriods.toLocalDate(item.transaction.date).format(dateFormatter)
        binding.subtitle.text = context.getString(R.string.dashboard_transaction_subtitle, accountName, date)

        val isIncome = item.transaction.type == TransactionType.INCOME
        val amountCurrency = item.account?.currencyCode
        val formattedAmount = amountCurrency?.let {
            Money.format(CurrencyAmount(it, item.transaction.amount))
        } ?: Money.formatMajorUnits(item.transaction.amount)
        binding.amount.text = "${if (isIncome) "+" else "-"}$formattedAmount"
        binding.amount.setTextColor(
            ContextCompat.getColor(context, if (isIncome) R.color.income_green else R.color.expense_red)
        )
    }
}
