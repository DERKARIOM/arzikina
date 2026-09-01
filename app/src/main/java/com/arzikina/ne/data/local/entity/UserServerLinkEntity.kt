package com.arzikina.ne.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Rattachement entre un utilisateur LOCAL (`UserEntity.id`, propre à cet appareil) et le compte
 * SERVEUR de synchronisation correspondant (`serverUserId`, l'UUID `users.id` côté MySQL — voir
 * `server/database/migrations/001_initial_schema.sql`) — étape D du chantier "audit auth + sync +
 * doublons" (login unifié).
 *
 * Table SÉPARÉE plutôt qu'une colonne ajoutée directement à [UserEntity] : `UserEntity.kt` ne peut
 * PAS être modifié dans ce projet sans casser la compilation (`kspDebugKotlin` échoue avec une
 * erreur `[MissingType]`, très probablement liée à `@ColumnInfo(collate = ColumnInfo.NOCASE)` sur
 * `username`/`email` — voir la KDoc de tête de `Migration22To23.kt`, qui documente déjà cette
 * limitation constatée du toolchain Room/KSP2 de ce projet). Une table neuve contourne entièrement
 * le problème : aucune modification de `UserEntity.kt` n'est nécessaire.
 *
 * PUREMENT LOCALE : contrairement à toutes les autres entités de ce projet, celle-ci n'a PAS de
 * `syncId`/`updatedAt`/`deletedAt`/`version` — elle n'est jamais envoyée au serveur (le lien
 * local↔serveur n'a de sens que sur CET appareil ; un autre appareil du même utilisateur aura son
 * PROPRE [localUserId], mais pointera vers le MÊME [serverUserId]).
 *
 * `localUserId` ET `serverUserId` sont chacun UNIQUES (relation 1↔1) : un compte local est lié à
 * au plus un compte serveur, et réciproquement — voir `SyncAuthRepositoryImpl`/le futur flux de
 * connexion unifié (`presentation/auth`), seuls appelants prévus de cette table.
 */
@Entity(
    tableName = "user_server_links",
    indices = [
        Index(value = ["localUserId"], unique = true),
        Index(value = ["serverUserId"], unique = true)
    ]
)
data class UserServerLinkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val localUserId: Long,
    val serverUserId: String,
    /** Horodatage (millis) du rattachement — informatif uniquement (ex. futur écran de diagnostic
     *  "Paramètres > Compte"), jamais utilisé pour une logique de conflit (voir la doc de tête). */
    val linkedAt: Long
)
