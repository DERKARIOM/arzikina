package com.naniger.arzikina.domain.shop

/** Échecs métier du module Boutique remontés par les repositories (jamais de message technique brut). */
sealed class ProductException(message: String) : Exception(message) {
    /** Saisie invalide : voir [ProductValidator]. */
    class Invalid(val errors: List<ProductError>) : ProductException("Produit invalide : $errors")

    /** Un autre produit de la boutique porte déjà ce code-barres. [existingProductId] permet de l'ouvrir. */
    class DuplicateBarcode(val existingProductId: Long) : ProductException("Code-barres déjà utilisé")

    /** Un autre produit de la boutique porte déjà cette référence interne. */
    class DuplicateSku(val existingProductId: Long) : ProductException("Référence déjà utilisée")

    class NotFound(val productId: Long) : ProductException("Produit introuvable : $productId")

    /** Aucun utilisateur connecté. */
    class NoSession : ProductException("Aucune session")
}
