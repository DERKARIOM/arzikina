package com.naniger.arzikina.data.local.entity

import androidx.room.Entity

/**
 * Ligne du panier de vente : un produit et sa quantité en milli-unités (1 unité = 1000), jamais de prix ni
 * de stock (relus à l'affichage et à la validation, voir `SaleCartStore`). [position] conserve l'ordre
 * d'ajout. Clé primaire composite : un produit n'apparaît qu'une fois par panier ; elle sert aussi d'index
 * pour la lecture par (utilisateur, boutique).
 */
@Entity(tableName = "cart_lines", primaryKeys = ["userId", "shopId", "productId"])
data class CartLineEntity(
    val userId: Long,
    val shopId: Long,
    val productId: Long,
    val quantityMilli: Long,
    val position: Int
)
