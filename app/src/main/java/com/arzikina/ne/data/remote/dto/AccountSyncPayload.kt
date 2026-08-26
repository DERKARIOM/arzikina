package com.arzikina.ne.data.remote.dto

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
     * explicitement côté appelant ([com.arzikina.ne.data.repository.SyncEngineImpl.applyAccountServerState]). */
    val isExcludedFromStatistics: Int = 0,
    val mobileMoneyPackageName: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)
