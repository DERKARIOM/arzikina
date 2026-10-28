package com.naniger.arzikina.data.local.database

import com.naniger.arzikina.data.local.entity.AccountEntity
import com.naniger.arzikina.domain.model.AccountIcon
import com.naniger.arzikina.domain.model.AccountType
import com.naniger.arzikina.domain.model.DefaultAccountKey
import com.naniger.arzikina.util.Constants

/**
 * Comptes proposés par défaut à un NOUVEL utilisateur juste après son
 * inscription, selon le mode d'utilisation qu'il a choisi (voir `DefaultAccountPlan`) :
 * un compte Boutique, un compte Espèces, ou les deux. Il pourra les renommer, changer
 * leur couleur ou les supprimer librement ensuite.
 *
 * [userId] est obligatoire : depuis l'introduction de l'authentification, ce
 * peuplement ne se déclenche plus à la création de la base (`RoomDatabase.Callback.onCreate`,
 * voir `di/DatabaseModule`) mais après l'inscription d'un utilisateur — une
 * base neuve n'a par définition encore aucun utilisateur auquel rattacher
 * des comptes par défaut.
 */
internal object DefaultAccounts {

    /**
     * Comptes par défaut d'un NOUVEL utilisateur ayant choisi un [com.naniger.arzikina.domain.model.UsageMode]
     * (voir `DefaultAccountPlan`) : uniquement les [keys] demandées, solde initial 0, `syncId` nul (rattaché
     * ensuite par le moteur de synchronisation, qui évite déjà les doublons par nom/type, voir
     * `AccountDao.getUnsyncedByNameAndType`). [firstDisplayOrder] place les comptes après ceux déjà présents.
     *
     * [excludeShopFromStatistics] : voir `DefaultAccountPlan.excludesShopFromStatistics` (compte Boutique seulement).
     *
     * `displayOrder` est assigné explicitement : ces comptes sont insérés via `accountDao.insertAll` (@Insert
     * direct, PAS `AccountRepositoryImpl.saveAccount`), sans quoi ils partageraient tous la valeur par défaut
     * `0L` et leur ordre d'affichage serait arbitraire.
     *
     * Seuls [DefaultAccountKey.SHOP] et [DefaultAccountKey.CASH] sont créés aujourd'hui. Les autres clés (Banque,
     * Mobile Money, Épargne, Wallet) restent déclarées pour l'affichage des comptes déjà existants.
     */
    fun seedFor(
        now: Long,
        userId: Long,
        keys: List<DefaultAccountKey>,
        firstDisplayOrder: Long = 0L,
        excludeShopFromStatistics: Boolean = false
    ): List<AccountEntity> {
        return keys.mapIndexed { index, key ->
            val order = firstDisplayOrder + index
            when (key) {
                DefaultAccountKey.CASH -> AccountEntity(
                    userId = userId,
                    name = DefaultAccountKey.CASH.canonicalName,
                    icon = AccountIcon.CASH,
                    type = AccountType.CASH,
                    colorArgb = CASH_COLOR_ARGB,
                    currencyCode = Constants.DEFAULT_CURRENCY_CODE,
                    initialBalanceMinor = 0L,
                    createdAt = now,
                    displayOrder = order
                )
                DefaultAccountKey.SHOP -> AccountEntity(
                    userId = userId,
                    name = DefaultAccountKey.SHOP.canonicalName,
                    icon = AccountIcon.WALLET,
                    type = AccountType.BOUTIQUE,
                    colorArgb = SHOP_COLOR_ARGB,
                    currencyCode = Constants.DEFAULT_CURRENCY_CODE,
                    initialBalanceMinor = 0L,
                    createdAt = now,
                    displayOrder = order,
                    isExcludedFromStatistics = excludeShopFromStatistics
                )
                else -> error("Compte par défaut non pris en charge par seedFor : $key")
            }
        }
    }

    /** Couleur principale d'Arsikina (#42B998), reprise pour le compte Boutique. */
    private const val SHOP_COLOR_ARGB = 0xFF42B998L

    /** Couleur historique du compte Espèces (inchangée : les comptes existants la portent déjà). */
    private const val CASH_COLOR_ARGB = 0xFF16A34AL
}
