package com.naniger.arzikina.presentation.transactions

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import androidx.core.content.ContextCompat
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.ItemTransactionCompactBinding
import com.naniger.arzikina.domain.model.CurrencyAmount
import com.naniger.arzikina.domain.model.TransactionType
import com.naniger.arzikina.domain.model.PaymentMethod
import com.naniger.arzikina.presentation.categories.CategoryIconMapper
import com.naniger.arzikina.util.DatePeriods
import com.naniger.arzikina.util.Money
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Remplit une ligne `item_transaction_compact.xml` à partir d'un
 * [TransactionUiItem]. Partagé entre [com.naniger.arzikina.presentation.dashboard.RecentTransactionsAdapter]
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
        // Un TRANSFER n'a pas de Category (voir Transaction.categoryId) : icône/libellé/couleur
        // fixes dédiés plutôt que le générique "Sans catégorie", pour rester compréhensible dans
        // les listes qui mélangent transferts et dépenses/revenus.
        val isTransfer = item.transaction.type == TransactionType.TRANSFER

        binding.categoryIcon.setImageResource(
            when {
                isTransfer -> R.drawable.ic_swap_horiz_24
                else -> category?.let { CategoryIconMapper.iconFor(it.icon) } ?: R.drawable.ic_category_other_24
            }
        )
        val circleColor = when {
            isTransfer -> ContextCompat.getColor(context, R.color.arzikina_primary)
            else -> category?.colorArgb?.toInt() ?: ContextCompat.getColor(context, R.color.arzikina_outline)
        }
        binding.categoryIcon.backgroundTintList = ColorStateList.valueOf(circleColor)

        binding.categoryName.text = when {
            isTransfer -> context.getString(R.string.transaction_category_transfer)
            else -> category?.name ?: context.getString(R.string.transaction_uncategorized)
        }

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

        // Un transfert n'est ni un revenu ni une dépense (voir TransactionType.TRANSFER, exclu
        // des totaux revenus/dépenses partout ailleurs) : ton TRANSFER dédié (couleur PRIMARY),
        // jamais vert/rouge, pour ne pas le confondre visuellement avec un vrai revenu/une vraie
        // dépense — plus de signe +/- affiché nulle part (voir TransactionAmountDisplay.kt), le
        // sens réel du transfert (isTransferReceived) ne joue donc plus sur cet affichage.
        val tone = when {
            isTransfer -> TransactionAmountTone.TRANSFER
            item.transaction.type == TransactionType.INCOME -> TransactionAmountTone.INCOME
            else -> TransactionAmountTone.EXPENSE
        }
        val amountCurrency = item.account?.currencyCode
        val amountDisplay = transactionAmountDisplay(item.transaction.amount, amountCurrency, tone)
        binding.amount.text = amountDisplay.text
        binding.amount.setTextColor(ContextCompat.getColor(context, amountDisplay.colorRes))

        if (item.runningBalance != null && amountCurrency != null) {
            binding.runningBalance.visibility = View.VISIBLE
            binding.runningBalance.text = "(${Money.format(CurrencyAmount(amountCurrency, item.runningBalance))})"
        } else {
            binding.runningBalance.visibility = View.GONE
        }

        // Indicateur discret "+ Frais X" (voir TransactionUiItem.feeAmount) : devise du compte
        // affiché sur CETTE ligne, comme pour "amount"/"runningBalance" juste au-dessus — un frais
        // payé depuis un compte tiers reste rare et, si son compte est dans une devise différente,
        // ce compromis d'affichage reste préférable à une recherche supplémentaire du compte réel
        // des frais rien que pour cet indicateur secondaire.
        if (item.feeAmount != null && amountCurrency != null) {
            binding.feeIndicator.visibility = View.VISIBLE
            binding.feeIndicator.text = context.getString(
                R.string.transaction_fee_indicator,
                Money.format(CurrencyAmount(amountCurrency, item.feeAmount))
            )
        } else {
            binding.feeIndicator.visibility = View.GONE
        }
    }
}
