package com.arzikina.ne.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Forme exacte d'une catégorie dans le corps de `POST /api/sync/push.php` (voir
 * `server/api/sync/push.php`, bloc `operations[].entity`) — DISTINCT de `CategoryEntity` (Room) :
 * ce DTO ne transporte QUE ce que le serveur attend sur le fil, jamais `userId` (voir la note
 * sécurité de `push.php` : `userId` envoyé par l'appareil serait de toute façon ignoré, autant ne
 * pas le sérialiser) ni `id`/`deletedAt` locaux à Room.
 *
 * [id] : c'est le `syncId` (UUID) de la ligne, jamais son `id` Room local — voir la KDoc de
 * `SyncQueueEntity.entitySyncId`.
 * [baseVersion] : version de la ligne telle que CET appareil la connaissait avant cette écriture
 * (`CategoryEntity.version` avant modification) — `null` pour une création, jamais mise à jour par
 * un simple enregistrement local (voir `CategoryRepositoryImpl.saveCategory`) : seul le futur Sync
 * Engine la fait progresser, à partir de `serverEntity` retourné par le serveur après un envoi
 * réussi (section 9, détection de conflit Last-Write-Wins).
 * [icon]/[type] : nom brut de l'enum (`CategoryIcon`/`TransactionType`), même sérialisation que
 * kotlinx.serialization applique par défaut à un enum — cohérent avec la colonne équivalente côté
 * Room (voir `Converters`).
 */
@Serializable
data class CategorySyncPayload(
    val id: String,
    val baseVersion: Int?,
    val name: String,
    val icon: String,
    val colorArgb: Long,
    val type: String,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Forme exacte de `serverEntity` pour `categories` dans la réponse de `push.php` (voir
 * `fetchCategoryRow`/`toCamelCaseRow` côté serveur) — décodée par [SyncEngineImpl] à partir du
 * [kotlinx.serialization.json.JsonElement] brut de [SyncPushResultDto.serverEntity]. `userId` (le
 * `user_id` UUID côté MySQL) est IGNORÉ à la désérialisation (`ignoreUnknownKeys`, voir
 * `NetworkModule.provideJson`) : il ne correspond à rien côté Room, qui utilise son propre `userId`
 * Long local (voir `CategoryEntity`, `AccountRepositoryImpl` pour le même raisonnement).
 */
@Serializable
data class CategoryServerStateDto(
    val id: String,
    val name: String,
    val icon: String,
    val colorArgb: Long,
    val type: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)
