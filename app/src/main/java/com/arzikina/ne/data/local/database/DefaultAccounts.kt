package com.arzikina.ne.data.local.database

import com.arzikina.ne.data.local.entity.AccountEntity
import com.arzikina.ne.domain.model.AccountIcon
import com.arzikina.ne.util.Constants

/**
 * Comptes proposés par défaut à un NOUVEL utilisateur juste après son
 * inscription, pour lui permettre de saisir des transactions immédiatement
 * sans étape de configuration préalable. Il pourra les renommer, changer
 * leur couleur ou les supprimer librement ensuite.
 *
 * [userId] est obligatoire : depuis l'introduction de l'authentification, ce
 * peuplement ne se déclenche plus à la création de la base (`RoomDatabase.Callback.onCreate`,
 * voir `di/DatabaseModule`) mais après l'inscription d'un utilisateur — une
 * base neuve n'a par définition encore aucun utilisateur auquel rattacher
 * des comptes par défaut.
 */
internal object DefaultAccounts {

    fun seed(now: Long, userId: Long): List<AccountEntity> = listOf(
        AccountEntity(
            userId = userId,
            name = "Espèces",
            icon = AccountIcon.CASH,
            colorArgb = 0xFF16A34AL,
            currencyCode = Constants.DEFAULT_CURRENCY_CODE,
            initialBalanceMinor = 0L,
            createdAt = now
        ),
        AccountEntity(
            userId = userId,
            name = "Banque",
            icon = AccountIcon.BANK,
            colorArgb = 0xFF006C4FL,
            currencyCode = Constants.DEFAULT_CURRENCY_CODE,
            initialBalanceMinor = 0L,
            createdAt = now
        ),
        AccountEntity(
            userId = userId,
            name = "Mobile Money",
            icon = AccountIcon.MOBILE_MONEY,
            colorArgb = 0xFFF59E0BL,
            currencyCode = Constants.DEFAULT_CURRENCY_CODE,
            initialBalanceMinor = 0L,
            createdAt = now
        ),
        AccountEntity(
            userId = userId,
            name = "Épargne",
            icon = AccountIcon.SAVINGS,
            colorArgb = 0xFF00A578L,
            currencyCode = Constants.DEFAULT_CURRENCY_CODE,
            initialBalanceMinor = 0L,
            createdAt = now
        ),
        AccountEntity(
            userId = userId,
            name = "Wallet",
            icon = AccountIcon.WALLET,
            colorArgb = 0xFF4C6B3FL,
            currencyCode = Constants.DEFAULT_CURRENCY_CODE,
            initialBalanceMinor = 0L,
            createdAt = now
        )
    )
}
