package com.naniger.arzikina.presentation.accounts

import android.content.res.ColorStateList
import android.view.View
import com.naniger.arzikina.databinding.ItemAccountBinding
import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.CurrencyAmount
import com.naniger.arzikina.util.Money

/**
 * Remplit une carte `item_account.xml` (dégradé, icône, nom, type, solde —
 * voir [AccountCardGradient]/[AccountIconDisplay]) à partir d'un [Account] et
 * de son solde courant. Partagé entre [AccountsAdapter] (liste "Mes comptes")
 * et [AccountDetailFragment] (résumé en tête de "Détail du compte", qui
 * inclut le MÊME layout via `<include>` plutôt que d'en approximer une copie)
 * — pour ne pas dupliquer cette logique de rendu entre les deux écrans.
 */
object AccountCardBinder {
    fun bind(binding: ItemAccountBinding, account: Account, currentBalance: Long) {
        val context = binding.root.context

        binding.cardContent.background = AccountCardGradient.create(account.colorArgb)

        binding.accountIcon.setImageResource(AccountIconMapper.iconFor(account.icon))
        binding.accountIcon.imageTintList = ColorStateList.valueOf(account.colorArgb.toInt())

        binding.accountName.text = account.name
        binding.accountType.text = context.getString(account.icon.displayTextRes())
        binding.accountBalance.text = Money.format(CurrencyAmount(account.currencyCode, currentBalance))

        // Voir Account.isExcludedFromStatistics / item_account.xml (exclusionIndicator) : seul
        // indicateur visuel de cette fonctionnalité dans la liste, GONE par défaut.
        binding.exclusionIndicator.visibility =
            if (account.isExcludedFromStatistics) View.VISIBLE else View.GONE
    }
}
