package com.naniger.arzikina.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Mouvement de stock : IMMUABLE et ajouté seulement (jamais modifié ni supprimé). Le stock d'un
 * produit est la somme de ses mouvements (voir `StockRules`).
 *
 * - [type] : NOM de `StockMovementType` (texte, valeur inconnue → `OTHER`).
 * - [deltaMilli] : quantité SIGNÉE en milli-unités (positive = entrée, négative = sortie).
 * - [unitCostMinor] : coût d'achat unitaire d'un réapprovisionnement, `null` sinon.
 * - [reason] : motif (obligatoire pour les corrections, pertes et mouvements libres).
 * - [reference] : référence de livraison ou note facultative.
 * - [saleId] : vente à l'origine du mouvement (vente ou annulation), `null` sinon.
 * - [occurredAt] : instant de l'événement ; [createdAt] : instant de l'enregistrement.
 *
 * [updatedAt]/[deletedAt]/[version] sont conservés pour rester compatibles avec le registre de
 * synchronisation générique (toutes les entités les portent) ; ils ne changent jamais après la
 * création (`deletedAt` reste `null`, `version` reste 1).
 *
 * Voir [ShopEntity] : pas de `ForeignKey`.
 */
@Entity(
    tableName = "stock_movements",
    indices = [
        Index("userId"),
        Index(value = ["productId", "occurredAt"]),
        Index("saleId"),
        Index(value = ["syncId"], unique = true)
    ]
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val productId: Long,
    val type: String,
    val deltaMilli: Long,
    val unitCostMinor: Long?,
    val reason: String?,
    val reference: String?,
    val saleId: Long?,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val syncId: String? = null,
    val deletedAt: Long? = null,
    val version: Int = 1
)
