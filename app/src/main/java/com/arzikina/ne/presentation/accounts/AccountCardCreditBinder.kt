package com.arzikina.ne.presentation.accounts

import android.view.View
import com.arzikina.ne.R
import com.arzikina.ne.databinding.ItemAccountCreditCardBinding
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.util.Money
import java.util.Locale

/**
 * Remplit une carte `item_account_credit_card.xml` (puce, numéro/expiration
 * masqués, titulaire — voir [AccountCardGradient]) à partir d'un [Account] de
 * type [com.arzikina.ne.domain.model.AccountType.CREDIT_CARD]. Pendant de
 * [AccountCardBinder] pour les comptes classiques ; les deux binders restent
 * séparés (layouts trop différents pour partager une seule fonction de rendu,
 * voir le commentaire du layout).
 *
 * Numéro : toujours ses 4 derniers chiffres (voir [Account.cardLastFourDigits],
 * seuls connus de l'app) — rien de plus à révéler, donc pas concerné par
 * [isSensitiveInfoVisible]. Expiration : masquée par défaut, affichée en
 * clair si [isSensitiveInfoVisible] — c'est la seule donnée que ce bouton
 * fait réellement basculer.
 *
 * @param showVisibilityToggle affiche le bouton œil — uniquement sur "Détail
 * du compte" ([AccountDetailFragment]), jamais dans la liste "Mes comptes"
 * ([AccountsAdapter]), qui reste toujours masquée sans interaction possible.
 * @param onToggleVisibility ignoré si [showVisibilityToggle] est `false`.
 */
object AccountCardCreditBinder {
    fun bind(
        binding: ItemAccountCreditCardBinding,
        account: Account,
        currentBalance: Long,
        cardHolderName: String,
        isSensitiveInfoVisible: Boolean = false,
        showVisibilityToggle: Boolean = false,
        onToggleVisibility: (() -> Unit)? = null
    ) {
        val context = binding.root.context

        binding.cardContent.background = AccountCardGradient.create(account.colorArgb)
        binding.accountName.text = account.name
        binding.accountBalance.text = Money.format(CurrencyAmount(account.currencyCode, currentBalance))
        binding.cardMaskedNumber.text = context.getString(
            R.string.dashboard_card_number_format,
            account.cardLastFourDigits ?: "----"
        )
        binding.cardHolderName.text = cardHolderName.uppercase(Locale.FRENCH)

        binding.cardMaskedExpiry.text = if (isSensitiveInfoVisible) {
            val month = account.cardExpiryMonth
            val year = account.cardExpiryYear
            if (month != null && year != null) "%02d/%02d".format(month, year % 100) else context.getString(R.string.account_card_masked_expiry)
        } else {
            context.getString(R.string.account_card_masked_expiry)
        }

        if (showVisibilityToggle) {
            binding.cardVisibilityToggle.visibility = View.VISIBLE
            binding.cardVisibilityToggle.setImageResource(
                if (isSensitiveInfoVisible) R.drawable.ic_visibility_off_24 else R.drawable.ic_visibility_24
            )
            binding.cardVisibilityToggle.contentDescription = context.getString(
                if (isSensitiveInfoVisible) R.string.account_card_hide_action else R.string.account_card_reveal_action
            )
            binding.cardVisibilityToggle.setOnClickListener { onToggleVisibility?.invoke() }
        } else {
            binding.cardVisibilityToggle.visibility = View.GONE
            binding.cardVisibilityToggle.setOnClickListener(null)
        }
    }
}
