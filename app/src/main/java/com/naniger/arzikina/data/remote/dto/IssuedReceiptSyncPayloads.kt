package com.naniger.arzikina.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Charges utiles de synchronisation du profil émetteur et des reçus émis (voir
 * `server/api/config/entity_sync_configs.php`, entrées `issuer_profiles` et `issued_receipts`, et
 * `claude/issued-receipts/SYNC-RECUS-EMIS-CONTRAT.md`).
 *
 * Aucune référence croisée : un reçu est un document autonome (l'émetteur y est figé, voir
 * `issuerBusinessName`…), donc aucune contrainte d'ordre entre ces deux types.
 *
 * Le PDF n'est jamais synchronisé (régénérable). Le logo n'est transporté que par son NOM : l'image
 * passe par `IssuerLogoApi` (voir `IssuedReceiptSyncHandler`).
 */
@Serializable
data class IssuerProfileSyncPayload(
    val id: String,
    val baseVersion: Int?,
    val businessName: String,
    val address: String,
    val phone: String,
    val email: String?,
    val taxId: String?,
    val logoFileName: String?,
    val signatureFileName: String?,
    val defaultIssuedByName: String?,
    val defaultIssuedByTitle: String?,
    val createdAt: Long,
    val updatedAt: Long
)

/** Forme de `serverEntity` pour `issuer_profiles` (`userId` serveur ignoré à la désérialisation). */
@Serializable
data class IssuerProfileServerStateDto(
    val id: String,
    val businessName: String,
    val address: String,
    val phone: String,
    val email: String? = null,
    val taxId: String? = null,
    val logoFileName: String? = null,
    val signatureFileName: String? = null,
    val defaultIssuedByName: String? = null,
    val defaultIssuedByTitle: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)

/**
 * [pricingJson] : texte JSON produit par `IssuedReceiptJson.encodePricing`, transporté TEL QUEL (le
 * serveur ne l'interprète pas ; la console Web doit reproduire le même format).
 */
@Serializable
data class IssuedReceiptSyncPayload(
    val id: String,
    val baseVersion: Int?,
    val number: String,
    val issuedAt: Long,
    val customerName: String,
    val customerPhone: String?,
    val description: String,
    val amountMinor: Long,
    val currencyCode: String,
    val paymentMethod: String,
    val transactionReference: String?,
    val notes: String?,
    val issuedByName: String?,
    val issuedByTitle: String?,
    val pricingJson: String?,
    val issuerBusinessName: String,
    val issuerAddress: String,
    val issuerPhone: String,
    val issuerEmail: String?,
    val issuerTaxId: String?,
    val issuerLogoFileName: String?,
    val issuerSignatureFileName: String?,
    val createdAt: Long,
    val updatedAt: Long
)

/** Forme de `serverEntity` pour `issued_receipts`. */
@Serializable
data class IssuedReceiptServerStateDto(
    val id: String,
    val number: String,
    val issuedAt: Long,
    val customerName: String,
    val customerPhone: String? = null,
    val description: String,
    val amountMinor: Long,
    val currencyCode: String,
    val paymentMethod: String,
    val transactionReference: String? = null,
    val notes: String? = null,
    val issuedByName: String? = null,
    val issuedByTitle: String? = null,
    val pricingJson: String? = null,
    val issuerBusinessName: String,
    val issuerAddress: String,
    val issuerPhone: String,
    val issuerEmail: String? = null,
    val issuerTaxId: String? = null,
    val issuerLogoFileName: String? = null,
    val issuerSignatureFileName: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)
