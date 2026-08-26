package com.arzikina.ne.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Forme exacte d'un prêt/emprunt dans le corps de `POST /api/sync/push.php` (voir
 * `server/api/config/entity_sync_configs.php`, entrée `loans`) — même raisonnement que
 * [CategorySyncPayload] (voir sa KDoc pour [id]/[baseVersion]).
 *
 * [personSyncId]/[accountSyncId]/[transactionSyncId] : PAS les `id` Room locaux de
 * [com.arzikina.ne.data.local.entity.LoanEntity] (sans aucun sens d'un appareil à l'autre) — voir
 * la KDoc de tête de `TransactionSyncPayload.kt` pour le raisonnement complet sur cette classe de
 * champs. Résolus par [LoanSyncEnqueuer] (voir sa KDoc de tête : `Loan`/`LoanPayment` ont TROIS
 * écrivains, contrairement à `Budget`, d'où cette classe partagée plutôt qu'une résolution privée
 * dupliquée).
 *
 * [reasonCustomText] : `null` explicite envoyé tel quel (voir `array_key_exists` côté serveur,
 * KDoc de tête de `push.php`) — même raisonnement que [SavingsGoalSyncPayload.deadline].
 */
@Serializable
data class LoanSyncPayload(
    val id: String,
    val baseVersion: Int?,
    val personSyncId: String,
    val accountSyncId: String,
    val type: String,
    val amount: Long,
    val amountRepaid: Long,
    val remainingAmount: Long,
    val startDate: Long,
    val dueDate: Long,
    val reason: String,
    val reasonCustomText: String?,
    val repaymentMode: String,
    val description: String,
    val status: String,
    val transactionSyncId: String,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Forme exacte de `serverEntity` pour `loans` dans la réponse de `push.php`/`pull.php` — voir la
 * KDoc de [CategoryServerStateDto] (même raisonnement, `userId` ignoré à la désérialisation).
 * Résolution inverse (→ `id` locaux) par `SyncEngineImpl.applyLoanServerState`, même principe que
 * `SyncEngineImpl.applyTransactionServerState`.
 */
@Serializable
data class LoanServerStateDto(
    val id: String,
    val personSyncId: String,
    val accountSyncId: String,
    val type: String,
    val amount: Long,
    val amountRepaid: Long,
    val remainingAmount: Long,
    val startDate: Long,
    val dueDate: Long,
    val reason: String,
    val reasonCustomText: String? = null,
    val repaymentMode: String,
    val description: String,
    val status: String,
    val transactionSyncId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)

/**
 * Forme exacte d'un remboursement dans le corps de `POST /api/sync/push.php` (voir
 * `server/api/config/entity_sync_configs.php`, entrée `loan_payments`) — voir la KDoc de tête de
 * [LoanSyncPayload] pour le raisonnement complet ([loanSyncId]/[accountSyncId]/[transactionSyncId]
 * résolus par [LoanSyncEnqueuer]).
 */
@Serializable
data class LoanPaymentSyncPayload(
    val id: String,
    val baseVersion: Int?,
    val loanSyncId: String,
    val accountSyncId: String,
    val amount: Long,
    val date: Long,
    val note: String,
    val transactionSyncId: String,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Forme exacte de `serverEntity` pour `loan_payments` — voir la KDoc de [LoanServerStateDto].
 * Résolution inverse par `SyncEngineImpl.applyLoanPaymentServerState`.
 */
@Serializable
data class LoanPaymentServerStateDto(
    val id: String,
    val loanSyncId: String,
    val accountSyncId: String,
    val amount: Long,
    val date: Long,
    val note: String,
    val transactionSyncId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)
