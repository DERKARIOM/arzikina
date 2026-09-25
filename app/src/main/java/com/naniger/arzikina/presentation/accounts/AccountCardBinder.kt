package com.naniger.arzikina.presentation.accounts

import android.content.res.ColorStateList
import android.view.View
import com.naniger.arzikina.databinding.ItemAccountBinding
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.AccountType
import com.naniger.arzikina.domain.model.CurrencyAmount
import com.naniger.arzikina.presentation.components.displayName
import com.naniger.arzikina.util.Money
import com.naniger.arzikina.util.SavingsGoalProgress

/**
 * Remplit une carte `item_account.xml` (dégradé, icône, nom, type, solde —
 * voir [AccountCardGradient]/[AccountIconDisplay]) à partir d'un [Account] et
 * de son solde courant. Partagé entre [AccountsAdapter] (liste "Mes comptes")
 * et [AccountDetailFragment] (résumé en tête de "Détail du compte", qui
 * inclut le MÊME layout via `<include>` plutôt que d'en approximer une copie)
 * — pour ne pas dupliquer cette logique de rendu entre les deux écrans.
 *
 * Objectif d'épargne ([AccountType.SAVINGS_GOAL]) : MÊME carte, avec en plus la progression
 * (`savingsGoalProgressGroup`, voir [bindSavingsGoal]) — aucune carte séparée.
 */
object AccountCardBinder {
    fun bind(binding: ItemAccountBinding, account: Account, currentBalance: Long) {
        val context = binding.root.context

        binding.cardContent.background = AccountCardGradient.create(account.colorArgb)

        binding.accountIcon.setImageResource(AccountIconMapper.iconFor(account.icon))
        binding.accountIcon.imageTintList = ColorStateList.valueOf(account.colorArgb.toInt())

        binding.accountName.text = account.displayName(binding.root.context)
        // Un objectif affiche son TYPE (« Objectif d'épargne ») plutôt que le libellé de son icône :
        // c'est ce qui le distingue d'un compte classique au premier coup d'œil.
        binding.accountType.text = context.getString(
            if (account.type == AccountType.SAVINGS_GOAL) account.type.displayTextRes() else account.icon.displayTextRes()
        )
        binding.accountBalance.text = Money.format(CurrencyAmount(account.currencyCode, currentBalance))

        // Voir Account.isExcludedFromStatistics / item_account.xml (exclusionIndicator) : seul
        // indicateur visuel de cette fonctionnalité dans la liste, GONE par défaut.
        binding.exclusionIndicator.visibility =
            if (account.isExcludedFromStatistics) View.VISIBLE else View.GONE

        bindSavingsGoal(binding, account, currentBalance)
    }

    /**
     * « 150 000 F / 500 000 F », barre et pourcentage — recalculés à chaque liaison depuis le
     * solde COURANT (jamais stocké, voir [SavingsGoalProgress]) : la progression suit donc toute
     * transaction/transfert/synchronisation. Barre plafonnée à 100 % ; au-delà, un statut
     * « Objectif dépassé de X » plutôt qu'un « 120 % ».
     */
    private fun bindSavingsGoal(binding: ItemAccountBinding, account: Account, currentBalance: Long) {
        val snapshot = if (account.type == AccountType.SAVINGS_GOAL) {
            SavingsGoalProgress.of(currentBalance, account.savingsTargetAmount)
        } else {
            null
        }
        binding.savingsGoalProgressGroup.visibility = if (snapshot != null) View.VISIBLE else View.GONE
        if (snapshot == null) return

        val context = binding.root.context
        binding.savingsGoalAmounts.text = context.getString(
            R.string.account_savings_goal_amount_of_target,
            Money.format(CurrencyAmount(account.currencyCode, snapshot.saved)),
            Money.format(CurrencyAmount(account.currencyCode, snapshot.target))
        )
        binding.savingsGoalPercent.text = context.getString(R.string.account_savings_goal_percent, snapshot.percent)
        binding.savingsGoalProgress.setProgressCompat(snapshot.percent, false)

        val status = when {
            snapshot.isExceeded -> context.getString(
                R.string.account_savings_goal_exceeded,
                Money.format(CurrencyAmount(account.currencyCode, snapshot.exceededBy))
            )
            snapshot.isReached -> context.getString(R.string.account_savings_goal_reached)
            else -> null
        }
        binding.savingsGoalStatus.text = status
        binding.savingsGoalStatus.visibility = if (status != null) View.VISIBLE else View.GONE
    }
}
