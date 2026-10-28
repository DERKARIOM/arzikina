package com.naniger.arzikina.domain.shop

import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.AccountIcon
import com.naniger.arzikina.domain.model.AccountType
import com.naniger.arzikina.domain.repository.AccountRepository
import com.naniger.arzikina.domain.repository.ShopRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Raison pour laquelle un compte Boutique n'a pas été créé. Toujours levée AVANT toute écriture. */
enum class CreateShopAccountError { NAME_REQUIRED, NAME_TOO_LONG, NAME_DUPLICATE, SHOP_NOT_READY, FAILED }

sealed interface CreateShopAccountResult {
    data class Created(val accountId: Long, val name: String) : CreateShopAccountResult
    data class Rejected(val error: CreateShopAccountError) : CreateShopAccountResult
}

/**
 * Crée, depuis l'écran de vente, le compte [AccountType.BOUTIQUE] qui recevra les encaissements.
 *
 * Le compte hérite de la devise de la boutique (condition d'éligibilité, voir [ShopAccountPolicy]) et
 * démarre à 0 : un solde de départ fausserait les statistiques. Il passe par [AccountRepository.saveAccount],
 * donc par le même chemin que l'écran « Nouveau compte » (position, synchronisation) — aucun système
 * parallèle. Le nom est validé ici (vide, trop long, déjà utilisé) pour que la règle soit testable sans interface.
 */
class CreateShopAccountUseCase @Inject constructor(
    private val accounts: AccountRepository,
    private val shops: ShopRepository
) {

    suspend operator fun invoke(rawName: String): CreateShopAccountResult {
        val name = rawName.trim()
        if (name.isEmpty()) return rejected(CreateShopAccountError.NAME_REQUIRED)
        if (name.length > MAX_NAME_LENGTH) return rejected(CreateShopAccountError.NAME_TOO_LONG)

        val shop = shops.observeShop().first() ?: return rejected(CreateShopAccountError.SHOP_NOT_READY)
        if (accounts.observeAccounts().first().any { it.name.equals(name, ignoreCase = true) }) {
            return rejected(CreateShopAccountError.NAME_DUPLICATE)
        }

        val id = accounts.saveAccount(
            Account(
                name = name,
                icon = AccountIcon.WALLET,
                colorArgb = DEFAULT_COLOR_ARGB,
                currencyCode = shop.currencyCode,
                initialBalance = 0L,
                createdAt = System.currentTimeMillis(),
                type = AccountType.BOUTIQUE
            )
        )
        return CreateShopAccountResult.Created(id, name)
    }

    private fun rejected(error: CreateShopAccountError) = CreateShopAccountResult.Rejected(error)

    companion object {
        /** Même plafond d'affichage qu'un nom de produit raisonnable ; évite un titre qui déborde des cartes. */
        const val MAX_NAME_LENGTH = 40

        /** Couleur par défaut du formulaire « Nouveau compte » (`AccountFormState.colorArgb`). */
        const val DEFAULT_COLOR_ARGB = 0xFF10B981L
    }
}
