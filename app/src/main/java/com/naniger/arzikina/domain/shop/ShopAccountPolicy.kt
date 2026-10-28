package com.naniger.arzikina.domain.shop

import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.AccountType

/**
 * Règle UNIQUE de « quel compte peut encaisser une vente » : un compte de type
 * [AccountType.BOUTIQUE], dans la devise de la boutique. Un compte Espèces, Banque, Mobile Money…
 * ne reçoit jamais les ventes : le compte Boutique isole l'argent du commerce de l'argent personnel.
 *
 * Logique pure (aucune dépendance Android) : l'écran de vente l'utilise pour proposer les comptes, et
 * `SaleRepositoryImpl` refuse de son côté tout compte d'un autre type ([SaleException.NotShopAccount]),
 * de sorte qu'aucun chemin ne peut contourner la règle.
 */
object ShopAccountPolicy {

    /** Vrai si un compte de ce [type] peut recevoir l'encaissement d'une vente. */
    fun canCollectSales(type: AccountType): Boolean = type == AccountType.BOUTIQUE

    /** Comptes proposés à l'encaissement : type Boutique ET devise de la boutique. */
    fun eligibleAccounts(accounts: List<Account>, shopCurrencyCode: String): List<Account> =
        accounts.filter { canCollectSales(it.type) && it.currencyCode == shopCurrencyCode }

    /**
     * Compte présélectionné parmi [eligible] : le choix en cours s'il est encore éligible, sinon le compte
     * par défaut de la boutique, sinon le premier. `null` si aucun compte n'est éligible (l'écran propose
     * alors d'en créer un).
     */
    fun pickDefault(eligible: List<Account>, chosenAccountId: Long?, shopAccountId: Long?): Account? =
        eligible.firstOrNull { it.id == chosenAccountId }
            ?: eligible.firstOrNull { it.id == shopAccountId }
            ?: eligible.firstOrNull()
}
