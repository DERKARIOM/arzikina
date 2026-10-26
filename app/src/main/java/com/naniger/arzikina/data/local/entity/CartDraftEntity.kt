package com.naniger.arzikina.data.local.entity

import androidx.room.Entity

/**
 * Panier de vente en cours d'un utilisateur dans une boutique : la clé d'idempotence du brouillon
 * ([draftKey], voir `SaleCartStore`). Au plus UN panier par couple (utilisateur, boutique) ; la ligne
 * n'existe que tant que le panier contient au moins un produit.
 *
 * Données purement locales : jamais synchronisées ni sauvegardées (un panier est éphémère et ses
 * identifiants de produits sont locaux). Aucune `ForeignKey` (convention du module Boutique, voir
 * `ShopEntity`) : [shopId] n'est vérifié qu'à l'écriture, par `CartLocalDataSource`.
 */
@Entity(tableName = "cart_drafts", primaryKeys = ["userId", "shopId"])
data class CartDraftEntity(
    val userId: Long,
    val shopId: Long,
    val draftKey: String,
    val updatedAt: Long
)
