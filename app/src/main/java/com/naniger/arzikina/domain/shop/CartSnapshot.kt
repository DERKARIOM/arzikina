package com.naniger.arzikina.domain.shop

import java.util.UUID

/**
 * État complet d'un panier de vente, lu et écrit comme UN SEUL bloc : les lignes (produit -> quantité en
 * milli-unités, dans l'ordre d'ajout) et la clé d'idempotence du brouillon ([draftKey], voir [SaleCartStore]).
 *
 * Lignes et clé changent toujours ensemble (valider puis vider le panier change la clé) : les enregistrer
 * séparément permettrait de restaurer d'anciennes lignes avec une nouvelle clé, et donc de créer deux ventes
 * pour un même panier.
 */
data class CartSnapshot(val lines: Map<Long, Long>, val draftKey: String) {

    companion object {
        /** Panier vide avec une nouvelle clé d'idempotence. */
        fun fresh(): CartSnapshot = CartSnapshot(emptyMap(), UUID.randomUUID().toString())
    }
}
