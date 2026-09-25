package com.naniger.arzikina.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Forme exacte d'un compte dans le corps de `POST /api/sync/push.php` (voir
 * `server/api/config/entity_sync_configs.php`, entrée `accounts`) — même raisonnement que
 * [CategorySyncPayload] (voir sa KDoc pour [id]/[baseVersion]).
 *
 * [cardLastFourDigits]/[cardExpiryMonth]/[cardExpiryYear] : `null` explicite envoyé tel quel (voir
 * `array_key_exists` côté serveur, KDoc de tête de `push.php`) — même raisonnement que
 * [SavingsGoalSyncPayload.deadline]. Seuls les 4 derniers chiffres + l'expiration (déjà en clair
 * dans `AccountEntity`, affichage masqué) sont synchronisés : le numéro complet et le CVV chiffrés
 * (`CardSecretEntity`) restent VOLONTAIREMENT hors du champ de la synchronisation — voir
 * docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md, décision 6.2 (clé de chiffrement liée au Keystore d'un
 * seul appareil, non transportable telle quelle).
 * [isExcludedFromStatistics] : booléen Kotlin, sérialisé `true`/`false` dans CE payload SORTANT —
 * voir `entity_sync_configs.php` (`type: 'int'`, `(int) true|false` côté PHP) pour la conversion à
 * l'écriture. ⚠️ Le sens RETOUR ([AccountServerStateDto] ci-dessous) est différent : MySQL/PDO
 * renvoie cette colonne comme un entier JSON (`0`/`1`), jamais `true`/`false` — kotlinx.serialization
 * ne convertit pas silencieusement un nombre en `Boolean`, d'où le type `Int` côté [AccountServerStateDto].
 *
 * [savingsTargetAmount]/[savingsDescription] : objectif d'épargne (`AccountType.SAVINGS_GOAL`),
 * TOUJOURS envoyés (sans valeur par défaut, `null` explicite compris) — un compte repassé en compte
 * classique DOIT effacer ces colonnes côté serveur (`array_key_exists` + `null`, voir `push.php`).
 * Un ancien client qui ne connaît pas ces champs ne les envoie pas : le serveur conserve alors la
 * valeur actuelle au lieu de l'effacer (même mécanisme), aucune perte de montant cible.
 */
@Serializable
data class AccountSyncPayload(
    val id: String,
    val baseVersion: Int?,
    val name: String,
    val icon: String,
    val colorArgb: Long,
    val currencyCode: String,
    val initialBalanceMinor: Long,
    val type: String,
    val cardLastFourDigits: String?,
    val cardExpiryMonth: Int?,
    val cardExpiryYear: Int?,
    val isExcludedFromStatistics: Boolean,
    val mobileMoneyPackageName: String?,
    val displayOrder: Long,
    val savingsTargetAmount: Long?,
    val savingsDescription: String?,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Forme exacte de `serverEntity` pour `accounts` dans la réponse de `push.php`/`pull.php` — voir la
 * KDoc de [CategoryServerStateDto] (même raisonnement, `userId` ignoré à la désérialisation).
 */
@Serializable
data class AccountServerStateDto(
    val id: String,
    val name: String,
    val icon: String,
    val colorArgb: Long,
    val currencyCode: String,
    val initialBalanceMinor: Long,
    val type: String,
    val cardLastFourDigits: String? = null,
    val cardExpiryMonth: Int? = null,
    val cardExpiryYear: Int? = null,
    /** `0`/`1`, PAS `Boolean` — voir la KDoc de tête de ce fichier : conversion en `Boolean` faite
     * explicitement côté appelant ([com.naniger.arzikina.data.repository.SyncEngineImpl.applyAccountServerState]). */
    val isExcludedFromStatistics: Int = 0,
    val mobileMoneyPackageName: String? = null,
    /** `0` par défaut : tolère une réponse serveur antérieure au déploiement de cette colonne
     * (voir `database/migrations/004_add_display_order_to_accounts.sql`), même raisonnement que
     * les autres champs à défaut de ce DTO. */
    val displayOrder: Long = 0L,
    /** `null` par défaut : tolère une réponse serveur antérieure à
     * `database/migrations/006_savings_goal_accounts.sql` (même raisonnement que [displayOrder]). */
    val savingsTargetAmount: Long? = null,
    val savingsDescription: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)
