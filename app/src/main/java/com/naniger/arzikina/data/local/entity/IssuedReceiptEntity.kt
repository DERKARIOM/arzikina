package com.naniger.arzikina.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Représentation Room d'un [com.naniger.arzikina.domain.model.IssuedReceipt] (reçu ÉMIS — à ne pas
 * confondre avec [ReceiptEntity], justificatif importé).
 *
 * Aucune clé étrangère : un reçu émis est un document autonome, indépendant des comptes et
 * transactions de l'application. [userId] : voir [AccountEntity] (filtrage direct, isolation
 * multi-utilisateurs).
 *
 * Colonnes volontairement peu nombreuses :
 * - [amountMinor] (total) est DÉNORMALISÉ pour trier/afficher la liste sans décoder de JSON ;
 * - [pricingJson] (lignes, remise, taxe) est `null` pour un reçu simple ; jamais interrogé seul,
 *   donc pas de table dédiée (décision d'architecture validée) ;
 * - [issuerJson] est l'instantané figé des coordonnées de l'émetteur (voir `IssuerSnapshot`) ;
 * - [paymentMethod] stocke le NOM de l'enum `PaymentMethod` (texte) : une valeur inconnue (client
 *   plus récent) retombe sur `OTHER` au mapping plutôt que de faire échouer la lecture.
 *
 * Unicité : `(userId, number)` — le numéro n'est jamais réutilisé, même après suppression douce.
 *
 * Synchronisation ([syncId]/[updatedAt]/[deletedAt]/[version]) : même gabarit que les autres
 * entités, voir `docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md`. [syncId] reste `null` tant que l'étape
 * « Synchronisation » n'a pas enregistré cette entité : elle l'attribuera alors à toutes les
 * lignes locales existantes (même mécanique que `enqueueUnsynced…`).
 *
 * [pdfFileName] : cache local du PDF généré, jamais synchronisé (régénérable).
 */
@Entity(
    tableName = "issued_receipts",
    indices = [
        Index("userId"),
        Index(value = ["userId", "number"], unique = true),
        Index(value = ["userId", "issuedAt"]),
        Index(value = ["syncId"], unique = true)
    ]
)
data class IssuedReceiptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
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
    val issuerJson: String,
    val pdfFileName: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncId: String? = null,
    val deletedAt: Long? = null,
    val version: Int = 1
)
