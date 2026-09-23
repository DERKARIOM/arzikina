package com.naniger.arzikina.presentation.transactions

import androidx.annotation.ColorRes
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.CurrencyAmount
import com.naniger.arzikina.util.Money

/**
 * Sens à donner à l'affichage d'un montant lié à une transaction — voir [transactionAmountDisplay].
 *
 * Volontairement distinct de [com.naniger.arzikina.domain.model.TransactionType] : chaque écran
 * détermine son propre [TransactionAmountTone] à partir de ses propres règles (ex. un transfert
 * REÇU côté [com.naniger.arzikina.presentation.transactions.TransactionUiItem.isTransferReceived] reste
 * quand même affiché en [TRANSFER], jamais en [INCOME] — voir [TransactionItemBinder] ; une
 * occurrence rejetée d'une règle récurrente utilise [NEUTRAL] bien que
 * [com.naniger.arzikina.domain.model.RecurringTransaction.type] soit un revenu ou une dépense normal —
 * voir `RecurringOccurrenceItemBinder`). Un mappage 1-à-1 depuis une seule
 * [com.naniger.arzikina.domain.model.Transaction] ne suffirait donc pas à couvrir tous les écrans.
 */
enum class TransactionAmountTone {
    INCOME,
    EXPENSE,
    TRANSFER,
    NEUTRAL
}

data class TransactionAmountDisplay(
    val text: String,
    @ColorRes val colorRes: Int
)

/**
 * Texte + couleur d'un montant de transaction, SANS signe `+`/`-` (cahier des charges "Modifier
 * l'affichage des montants des transactions dans toute l'application" — le type se lit uniquement à
 * la couleur désormais). Centralise ce que chaque Binder faisait séparément :
 * 1. valeur ABSOLUE de [amountMinor] — jamais utilisée pour un calcul, uniquement pour cet affichage
 *    (voir [com.naniger.arzikina.domain.model.signedAmount], toujours utilisé tel quel par
 *    `computeCurrentBalances`/`computeRunningBalances`, jamais impacté par cette fonction) ;
 * 2. formatage via [Money] (déjà centralisé, inchangé) ;
 * 3. couleur selon [tone].
 *
 * [currencyCode] `null` retombe sur [Money.formatAmount] (sans devise) — même compromis que les
 * Binders existants quand le compte d'une ligne n'a pas pu être résolu.
 */
fun transactionAmountDisplay(amountMinor: Long, currencyCode: String?, tone: TransactionAmountTone): TransactionAmountDisplay {
    val absoluteAmount = kotlin.math.abs(amountMinor)
    val text = currencyCode?.let { Money.format(CurrencyAmount(it, absoluteAmount)) } ?: Money.formatAmount(absoluteAmount)
    val colorRes = when (tone) {
        TransactionAmountTone.INCOME -> R.color.income_green
        TransactionAmountTone.EXPENSE -> R.color.expense_red
        TransactionAmountTone.TRANSFER -> R.color.arzikina_primary
        TransactionAmountTone.NEUTRAL -> R.color.arzikina_on_balance_card_variant
    }
    return TransactionAmountDisplay(text, colorRes)
}
