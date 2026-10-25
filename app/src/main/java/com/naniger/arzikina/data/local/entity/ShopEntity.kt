package com.naniger.arzikina.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Boutique d'un utilisateur : racine de tout le module « Boutique » (produits, stocks, ventes).
 *
 * V1 : une seule boutique par utilisateur est exposée par l'interface, MAIS toutes les tables
 * filles portent déjà `shopId` — ajouter plusieurs boutiques ne demandera aucune migration de
 * schéma, seulement un sélecteur.
 *
 * [accountId] : compte de type `BOUTIQUE` crédité par défaut lors d'une vente ; `null` tant qu'aucun
 * n'est choisi. Référence par id local, SANS `ForeignKey` (même convention que `loans`, `issued_receipts`) :
 * la cohérence est garantie par les repositories, et une ligne reçue de la synchronisation peut
 * arriver avant le compte qu'elle référence.
 *
 * Aucune clé étrangère SQL dans tout le module Boutique : une ligne synchronisée peut arriver avant
 * son parent (ordre de réception non garanti), et une contrainte ferait échouer l'insertion. Les
 * index sur les colonnes de liaison suffisent aux jointures.
 *
 * Synchronisation ([syncId]/[updatedAt]/[deletedAt]/[version]) : gabarit commun, voir
 * `docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md`.
 */
@Entity(
    tableName = "shops",
    indices = [Index("userId"), Index(value = ["syncId"], unique = true)]
)
data class ShopEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val name: String,
    val accountId: Long?,
    val currencyCode: String,
    val createdAt: Long,
    val updatedAt: Long,
    val syncId: String? = null,
    val deletedAt: Long? = null,
    val version: Int = 1
)
