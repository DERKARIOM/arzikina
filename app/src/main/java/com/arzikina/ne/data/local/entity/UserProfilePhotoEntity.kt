package com.arzikina.ne.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * État de la photo de profil d'un utilisateur — cahier des charges "Gestion de la photo de
 * profil". Table SÉPARÉE de `users` plutôt qu'une colonne ajoutée directement à [UserEntity] :
 * `UserEntity.kt` ne peut PAS être modifié dans ce projet sans casser la compilation
 * (`kspDebugKotlin` échoue avec une erreur `[MissingType]` — voir la KDoc de tête de
 * `Migration22To23.kt` et `docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md` section 6.5, limitation
 * d'outillage déjà contournée une première fois par [UserServerLinkEntity]). Une table neuve
 * évite entièrement le problème.
 *
 * Une seule ligne par utilisateur (voir l'index unique sur [userId], même principe que
 * [UserServerLinkEntity]) — jamais un historique : supprimer la photo remet [localPath]/
 * [serverUrl] à `null` (avec incrément de [version]) plutôt que de supprimer la ligne, ce qui
 * garantit qu'aucune synchronisation ne peut créer par erreur "plusieurs photos" pour un même
 * profil (cahier des charges, section "Éviter les conflits").
 *
 * [localPath] : chemin RELATIF à `context.filesDir` (voir
 * [com.arzikina.ne.data.profile.ProfilePhotoFileStorage]) — jamais un chemin absolu, jamais une
 * URI `content://` d'une autre application (même raisonnement que `Receipt.localPath`, voir sa
 * doc : une URI externe n'a aucune garantie de survivre à un redémarrage).
 *
 * [serverUrl] : chemin/URL renvoyé par le serveur après upload réussi — `null` tant qu'aucun
 * upload n'a abouti pour la photo COURANTE (une nouvelle photo locale non encore envoyée a un
 * [serverUrl] qui pointe encore vers l'ANCIENNE photo tant que [pendingUpload] est vrai, jamais
 * remis à `null` prématurément : l'ancienne reste affichable sur les autres appareils en attendant).
 *
 * [version] : compteur incrémenté à chaque changement ACCEPTÉ localement (nouvelle photo ou
 * suppression) — sert à la fois de mécanisme d'invalidation de cache image (clé de cache Coil) et
 * de détection de conflit multi-appareils, même principe "dernière écriture gagne" que les autres
 * entités synchronisées du projet (voir [updatedAt]).
 *
 * [pendingUpload] : `true` tant que [localPath] n'a pas encore été confirmé reçu par le serveur —
 * relu par le Sync Engine pour réessayer l'upload après une coupure réseau (cahier des charges
 * "Synchronisation hors ligne"), jamais un simple "fire and forget" sans suivi d'état.
 *
 * PAS de `deletedAt`/`syncId` : contrairement aux entités "liste" (transactions, comptes...), il
 * n'y a jamais qu'une ligne par utilisateur à synchroniser, identifiée par [userId] lui-même —
 * une colonne de suppression douce n'aurait pas de sens ici (voir le raisonnement de suppression
 * ci-dessus).
 */
@Entity(
    tableName = "user_profile_photos",
    indices = [Index(value = ["userId"], unique = true)]
)
data class UserProfilePhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val localPath: String?,
    val serverUrl: String?,
    val version: Long,
    val pendingUpload: Boolean,
    val updatedAt: Long
)
