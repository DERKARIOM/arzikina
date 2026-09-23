package com.naniger.arzikina.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.naniger.arzikina.domain.model.ThemeMode

/**
 * Représentation Room des préférences d'affichage SYNCHRONISABLES d'un utilisateur — voir
 * `UserPreferencesRepositoryImpl` pour le raisonnement complet (étape 22, migration hors de
 * DataStore Preferences). SEULS [themeMode]/[currencyCode] vivent ici :
 * [com.naniger.arzikina.domain.model.UserPreferences.biometricLockEnabled] reste dans DataStore
 * Preferences, EXPLICITEMENT PAR APPAREIL (voir `docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md`, section
 * 6.5) — jamais dans cette table, jamais synchronisé.
 *
 * DIFFÉRENT de toutes les autres entités synchronisées de ce projet : au plus UNE ligne par
 * [userId] (voir l'index `unique` ci-dessous), jamais plusieurs — une ligne n'est créée qu'au
 * premier changement de réglage effectué par l'utilisateur (voir
 * `UserPreferencesRepositoryImpl.upsertPreferences`), puis SEULEMENT mise à jour. Aucun écran
 * "supprimer mes préférences" n'existe ni n'est prévu — [deletedAt]/[version] existent malgré tout,
 * par cohérence avec le schéma générique attendu par le Sync Engine ([id]/[userId]/[createdAt]/
 * [updatedAt]/[deletedAt]/[version] sont les colonnes "implicites" de TOUTE entité synchronisée,
 * voir `server/api/config/entity_sync_configs.php`) — [deletedAt] ne devrait jamais être renseigné
 * en pratique, [version] reste en revanche réellement utile (deux appareils hors ligne peuvent
 * changer le thème/la devise différemment avant de resynchroniser, conflit LWW classique).
 */
@Entity(
    tableName = "user_preferences",
    indices = [Index(value = ["userId"], unique = true), Index(value = ["syncId"], unique = true)]
)
data class UserPreferencesEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val themeMode: ThemeMode,
    val currencyCode: String,
    val createdAt: Long,
    /** UUID partagé Android/API/MySQL pour la synchronisation multi-appareils — additif, voir
     * `docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md` (section 6.3, option B). `null` tant que cette
     * ligne n'a jamais été envoyée au serveur. */
    val syncId: String? = null,
    /** Horodatage de dernière modification, pour la détection de conflit lors de la
     * synchronisation (Last-Write-Wins, section 9 du document ci-dessus). */
    val updatedAt: Long = 0L,
    /** Suppression douce (section 8 du document ci-dessus) : `null` = ligne active — voir la doc de
     * tête, ne devrait jamais être renseigné en pratique pour cette entité précise. */
    val deletedAt: Long? = null,
    /** Compteur de version optimiste, pour la détection de conflit côté serveur (section 9). */
    val version: Int = 1
)
