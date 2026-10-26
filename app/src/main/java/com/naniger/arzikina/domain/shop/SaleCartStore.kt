package com.naniger.arzikina.domain.shop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
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
 * Le panier vit en mémoire pour la durée du processus ; il n'est pas écrit sur disque.
 */
@Singleton
class SaleCartStore @Inject constructor() {

    private val _lines = MutableStateFlow<Map<Long, Long>>(emptyMap())

    /** Lignes du panier (produit -> quantité en milli-unités), dans l'ordre d'ajout. */
    val lines: StateFlow<Map<Long, Long>> = _lines.asStateFlow()

    @Volatile
    var draftKey: String = newKey()
        private set

    /**
     * Ajoute une unité de plus du produit si le stock actuel ([stockMilli]) le permet. Règle unique,
     * utilisée par la liste des produits comme par l'écran de vente (bouton `+`).
     */
    fun tryAdd(productId: Long, stockMilli: Long, isArchived: Boolean): CartAddResult {
        if (isArchived) return CartAddResult.UNAVAILABLE
        if (stockMilli <= 0L) return CartAddResult.OUT_OF_STOCK
        var result = CartAddResult.ADDED
        _lines.update { current ->
            val next = (current[productId] ?: 0L) + StockRules.QUANTITY_UNIT_MILLI
            if (next > stockMilli) {
                result = CartAddResult.STOCK_LIMIT
                current
            } else {
                result = CartAddResult.ADDED
                current + (productId to next)
            }
        }
        return result
    }

    /** Fixe la quantité exacte ; une quantité nulle ou négative retire la ligne. */
    fun setQuantity(productId: Long, quantityMilli: Long) {
        if (quantityMilli <= 0L) remove(productId) else _lines.update { it + (productId to quantityMilli) }
    }

    fun remove(productId: Long) = _lines.update { it - productId }

    /** Vide le panier et ouvre un nouveau brouillon (nouvelle clé d'idempotence). */
    fun clear() {
        draftKey = newKey()
        _lines.value = emptyMap()
    }

    private companion object {
        fun newKey(): String = UUID.randomUUID().toString()
    }
}
