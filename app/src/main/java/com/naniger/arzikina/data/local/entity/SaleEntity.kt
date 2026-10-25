package com.naniger.arzikina.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Vente validée d'une boutique. Une vente n'existe en base qu'une fois VALIDÉE (le panier en cours
 * n'est jamais persisté) ; elle n'est jamais supprimée, seulement annulée (voir [status]).
 *
 * - [reference] : référence d'affichage (`V-AAAAMMJJ-XXXX`), voir `SaleReference` ; l'identité réelle
 *   est [syncId] (index unique). Index non unique sur la référence (cf. `ProductEntity`).
 * - Montants en unités mineures : [subtotalMinor] (somme des lignes), [discountMinor], [totalMinor] =
 *   sous-total − remise. Le MONTANT ENCAISSÉ n'est pas stocké : c'est la somme des `sale_payments`
 *   (une seule source de vérité, aucun risque d'incohérence entre les deux).
 * - [status] : NOM de `SaleStatus` (texte). [cancelledAt]/[cancelReason] renseignés à l'annulation.
 * - [issuedReceiptId] : reçu PDF généré pour cette vente (module « Gestion de reçus »), `null`
 *   tant qu'il n'existe pas.
 * - [customerName]/[customerPhone] : client facultatif ; une vente ordinaire n'en exige pas.
 *
 * Voir [ShopEntity] : pas de `ForeignKey`.
 */
@Entity(
    tableName = "sales",
    indices = [
        Index("userId"),
        Index(value = ["shopId", "soldAt"]),
        Index(value = ["shopId", "reference"]),
        Index(value = ["syncId"], unique = true)
    ]
)
data class SaleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val shopId: Long,
    val reference: String,
    val soldAt: Long,
    val currencyCode: String,
    val customerName: String?,
    val customerPhone: String?,
    val subtotalMinor: Long,
    val discountMinor: Long,
    val totalMinor: Long,
    val status: String,
    val cancelledAt: Long?,
    val cancelReason: String?,
    val issuedReceiptId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncId: String? = null,
    val deletedAt: Long? = null,
    val version: Int = 1
)
