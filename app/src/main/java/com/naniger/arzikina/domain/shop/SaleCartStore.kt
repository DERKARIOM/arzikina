package com.naniger.arzikina.domain.shop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Résultat d'un ajout au panier ; le refus est expliqué à l'utilisateur par la présentation. */
enum class CartAddResult { ADDED, UNAVAILABLE, OUT_OF_STOCK, STOCK_LIMIT }

/**
 * Panier de vente en cours, partagé entre la liste des produits (« Ajouter au panier ») et l'écran de
 * vente. Il ne contient que le choix de l'utilisateur — produit -> quantité en milli-unités, dans l'ordre
 * d'ajout — jamais de prix ni de stock : ceux-ci sont relus à l'affichage et à la validation.
 *
 * [draftKey] identifie CE panier de façon stable : valider deux fois le même panier (double clic, nouvelle
 * tentative après une coupure) ne crée jamais deux ventes. [clear] (vente validée) en génère une nouvelle.
 *
 * Persistance : ce store reste une simple structure en mémoire, synchrone et sans dépendance (domaine pur).
 * Chaque changement est publié dans [snapshot] ; c'est `data.shop.CartPersistenceCoordinator` qui l'enregistre
 * dans Room et qui le remplace ([replaceWith]) quand l'utilisateur ou la boutique active change. Le panier
 * survit ainsi à la mort du processus, séparément pour chaque utilisateur et chaque boutique.
 */
@Singleton
class SaleCartStore @Inject constructor() {

    private val lock = Any()
    private val _snapshot = MutableStateFlow(CartSnapshot.fresh())
    private val _lines = MutableStateFlow<Map<Long, Long>>(emptyMap())

    /** Lignes ET clé du brouillon, publiées ensemble (source de la persistance). */
    val snapshot: StateFlow<CartSnapshot> = _snapshot.asStateFlow()

    /** Lignes du panier (produit -> quantité en milli-unités), dans l'ordre d'ajout. */
    val lines: StateFlow<Map<Long, Long>> = _lines.asStateFlow()

    val draftKey: String get() = _snapshot.value.draftKey

    /**
     * Ajoute une unité de plus du produit si le stock actuel ([stockMilli]) le permet. Règle unique,
     * utilisée par la liste des produits comme par l'écran de vente (bouton `+`).
     */
    fun tryAdd(productId: Long, stockMilli: Long, isArchived: Boolean): CartAddResult {
        if (isArchived) return CartAddResult.UNAVAILABLE
        if (stockMilli <= 0L) return CartAddResult.OUT_OF_STOCK
        var result = CartAddResult.ADDED
        mutate { current ->
            val next = (current.lines[productId] ?: 0L) + StockRules.QUANTITY_UNIT_MILLI
            if (next > stockMilli) {
                result = CartAddResult.STOCK_LIMIT
                current
            } else {
                result = CartAddResult.ADDED
                current.copy(lines = current.lines + (productId to next))
            }
        }
        return result
    }

    /** Fixe la quantité exacte ; une quantité nulle ou négative retire la ligne. */
    fun setQuantity(productId: Long, quantityMilli: Long) {
        if (quantityMilli <= 0L) remove(productId) else mutate { it.copy(lines = it.lines + (productId to quantityMilli)) }
    }

    fun remove(productId: Long) = mutate { it.copy(lines = it.lines - productId) }

    /** Vide le panier et ouvre un nouveau brouillon (nouvelle clé d'idempotence). */
    fun clear() = mutate { CartSnapshot.fresh() }

    /** Remplace tout le panier (restauration depuis le disque, changement d'utilisateur ou de boutique). */
    fun replaceWith(snapshot: CartSnapshot) = mutate { snapshot }

    /** Applique [transform] de façon atomique et publie lignes et clé ensemble ; sans effet si rien ne change. */
    private inline fun mutate(transform: (CartSnapshot) -> CartSnapshot) {
        synchronized(lock) {
            val current = _snapshot.value
            val next = transform(current)
            if (next != current) {
                _snapshot.value = next
                _lines.value = next.lines
            }
        }
    }
}
