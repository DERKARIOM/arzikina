package com.naniger.arzikina.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Forme exacte d'une transaction dans le corps de `POST /api/sync/push.php` (voir
 * `server/api/config/entity_sync_configs.php`, entrée `transactions`) — même raisonnement que
 * [CategorySyncPayload] (voir sa KDoc pour [id]/[baseVersion]).
 *
 * DIFFÉRENCE MAJEURE avec toutes les entités synchronisées jusqu'ici : `Transaction` référence
 * d'autres entités (compte, catégorie, transfert, transaction de frais) via des `id` Room LOCAUX,
 * qui n'ont AUCUN sens d'un appareil à l'autre. Ce payload transporte donc leurs `syncId` (UUID) —
 * [accountSyncId]/[transferAccountSyncId]/[categorySyncId]/[feeTransactionSyncId] — résolus par
 * [com.naniger.arzikina.data.repository.TransactionSyncEnqueuer] (voir sa KDoc pour la stratégie de
 * résolution et le filet de sécurité si un `syncId` référencé est étonnamment absent).
 *
 * Champs volontairement EXCLUS :
 * - [com.naniger.arzikina.data.local.entity.TransactionEntity.receiptId] : les reçus ne sont pas encore
 *   synchronisés — même raisonnement que `CardSecretEntity` pour `Account` (voir la KDoc de tête de
 *   `AccountSyncPayload.kt`).
 * - [com.naniger.arzikina.data.local.entity.TransactionEntity.receiptPhotoUri] : chemin de fichier LOCAL
 *   à CET appareil (`content://...`) — contrairement aux autres champs locaux jusqu'ici, celui-ci
 *   n'a même pas de traduction cross-appareil possible, pas seulement une histoire de `syncId`.
 *
 * [latitude]/[longitude] restent synchronisés tels quels : ce sont des coordonnées GPS absolues,
 * pas des références à une autre ligne locale.
 */
@Serializable
data class TransactionSyncPayload(
    val id: String,
    val baseVersion: Int?,
    val amount: Long,
    val type: String,
    val accountSyncId: String,
    val transferAccountSyncId: String?,
    val categorySyncId: String?,
    val date: Long,
    val description: String,
    val latitude: Double?,
    val longitude: Double?,
    val paymentMethod: String?,
    val feeTransactionSyncId: String?,
    val feeType: String?,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Forme exacte de `serverEntity` pour `transactions` dans la réponse de `push.php`/`pull.php` —
 * voir la KDoc de [CategoryServerStateDto] (même raisonnement, `userId` ignoré à la
 * désérialisation). Résolution INVERSE (`accountSyncId` → `accountId` LOCAL, etc.) faite par
 * `SyncEngineImpl.applyTransactionServerState`, jamais ici : ce DTO ne transporte que ce que le fil
 * contient, brut.
 */
@Serializable
data class TransactionServerStateDto(
    val id: String,
    val amount: Long,
    val type: String,
    val accountSyncId: String,
    val transferAccountSyncId: String? = null,
    val categorySyncId: String? = null,
    val date: Long,
    val description: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val paymentMethod: String? = null,
    val feeTransactionSyncId: String? = null,
    val feeType: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)
