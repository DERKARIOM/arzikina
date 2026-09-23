package com.naniger.arzikina.data.local.database

import com.naniger.arzikina.data.local.entity.AccountEntity
import com.naniger.arzikina.domain.model.AccountIcon
import com.naniger.arzikina.domain.model.AccountType
import com.naniger.arzikina.domain.model.DefaultAccountKey
import com.naniger.arzikina.util.Constants

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

    // displayOrder assigné explicitement (0..4, dans l'ordre de la liste ci-dessous) : ces 5
    // comptes sont insérés en une fois via accountDao.insertAll (@Insert direct, PAS
    // AccountRepositoryImpl.saveAccount) — sans cela ils partageraient tous la valeur par défaut
    // `0L` d'AccountEntity.displayOrder, laissant leur ordre initial d'affichage arbitraire (voir
    // com.arzikina.ne.domain.model.Account.displayOrder).
    fun seed(now: Long, userId: Long): List<AccountEntity> = listOf(
        AccountEntity(
            userId = userId,
            name = DefaultAccountKey.CASH.canonicalName,
            icon = AccountIcon.CASH,
            type = AccountType.CASH,
            colorArgb = 0xFF16A34AL,
            currencyCode = Constants.DEFAULT_CURRENCY_CODE,
            initialBalanceMinor = 0L,
            createdAt = now,
            displayOrder = 0L
        ),
        AccountEntity(
            userId = userId,
            name = DefaultAccountKey.BANK.canonicalName,
            icon = AccountIcon.BANK,
            type = AccountType.BANK,
            colorArgb = 0xFF006C4FL,
            currencyCode = Constants.DEFAULT_CURRENCY_CODE,
            initialBalanceMinor = 0L,
            createdAt = now,
            displayOrder = 1L
        ),
        AccountEntity(
            userId = userId,
            name = DefaultAccountKey.MOBILE_MONEY.canonicalName,
            icon = AccountIcon.MOBILE_MONEY,
            type = AccountType.MOBILE_MONEY,
            colorArgb = 0xFFF59E0BL,
            currencyCode = Constants.DEFAULT_CURRENCY_CODE,
            initialBalanceMinor = 0L,
            createdAt = now,
            displayOrder = 2L
        ),
        AccountEntity(
            userId = userId,
            name = DefaultAccountKey.SAVINGS.canonicalName,
            icon = AccountIcon.SAVINGS,
            type = AccountType.SAVINGS,
            colorArgb = 0xFF00A578L,
            currencyCode = Constants.DEFAULT_CURRENCY_CODE,
            initialBalanceMinor = 0L,
            createdAt = now,
            displayOrder = 3L
        ),
        AccountEntity(
            userId = userId,
            name = DefaultAccountKey.WALLET.canonicalName,
            icon = AccountIcon.WALLET,
            // Pas d'équivalent WALLET dans AccountType (voir sa doc) : même
            // repli que la migration pour un compte existant sans type dédié.
            type = AccountType.CASH,
            colorArgb = 0xFF4C6B3FL,
            currencyCode = Constants.DEFAULT_CURRENCY_CODE,
            initialBalanceMinor = 0L,
            createdAt = now,
            displayOrder = 4L
        )
    )
}
