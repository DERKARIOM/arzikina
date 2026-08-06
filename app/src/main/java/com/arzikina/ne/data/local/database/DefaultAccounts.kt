package com.arzikina.ne.data.local.database

import com.arzikina.ne.data.local.entity.AccountEntity
import com.arzikina.ne.domain.model.AccountIcon
import com.arzikina.ne.util.Constants

/**
 * Comptes proposés par défaut à la première installation, pour permettre à
 * l'utilisateur de saisir des transactions immédiatement sans étape de
 * configuration préalable. Il pourra les renommer, changer leur couleur ou
 * les supprimer librement ensuite — voir [ArzikinaDatabase] pour le
 * déclenchement de ce peuplement au premier lancement.
 */
internal object DefaultAccounts {

    fun seed(now: Long): List<AccountEntity> = listOf(
        AccountEntity(
            name = "Espèces",
            icon = AccountIcon.CASH,
            colorArgb = 0xFF16A34AL,
            currencyCode = Constants.DEFAULT_CURRENCY_CODE,
            initialBalanceMinor = 0L,
            createdAt = now
        ),
        AccountEntity(
            name = "Banque",
            icon = AccountIcon.BANK,
            colorArgb = 0xFF006C4FL,
            currencyCode = Constants.DEFAULT_CURRENCY_CODE,
            initialBalanceMinor = 0L,
            createdAt = now
        ),
        AccountEntity(
            name = "Mobile Money",
            icon = AccountIcon.MOBILE_MONEY,
            colorArgb = 0xFFF59E0BL,
            currencyCode = Constants.DEFAULT_CURRENCY_CODE,
            initialBalanceMinor = 0L,
            createdAt = now
        ),
        AccountEntity(
            name = "Épargne",
            icon = AccountIcon.SAVINGS,
            colorArgb = 0xFF00A578L,
            currencyCode = Constants.DEFAULT_CURRENCY_CODE,
            initialBalanceMinor = 0L,
            createdAt = now
        ),
        AccountEntity(
            name = "Wallet",
            icon = AccountIcon.WALLET,
            colorArgb = 0xFF4C6B3FL,
            currencyCode = Constants.DEFAULT_CURRENCY_CODE,
            initialBalanceMinor = 0L,
            createdAt = now
        )
    )
}
