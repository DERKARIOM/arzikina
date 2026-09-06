package com.arzikina.ne.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Photo de profil de l'utilisateur COURANT (voir [com.arzikina.ne.domain.repository.SessionManager],
 * jamais de `userId` en paramètre — même convention que [UserPreferencesRepository]) — cahier des
 * charges "Gestion de la photo de profil".
 *
 * Volontairement séparé de [AuthRepository] : le nom/l'e-mail/le téléphone (déjà gérés par
 * [AuthRepository.updateProfile]) sont des informations SAISIES par l'utilisateur, alors qu'une
 * photo est un FICHIER traité (recadré, optimisé) puis synchronisé de façon indépendante — voir la
 * KDoc de tête de [com.arzikina.ne.data.local.entity.UserProfilePhotoEntity] pour le raisonnement
 * complet (table séparée, une seule ligne par utilisateur, contournement de la limitation
 * Room/KSP2 sur `UserEntity`).
 */
interface ProfilePhotoRepository {

    /**
     * URI `content://` prête pour Coil (voir
     * [com.arzikina.ne.data.profile.ProfilePhotoFileStorage.contentUriFor]), ou `null` si
     * l'utilisateur courant n'a pas (ou plus) de photo — l'appelant doit alors afficher l'avatar
     * par défaut (`R.drawable.ic_person_24`). Réémet dès qu'une nouvelle photo est enregistrée OU
     * supprimée (voir [saveNewPhoto]/[deletePhoto]), affichage immédiat garanti sans action
     * supplémentaire de l'appelant (cahier des charges, "mettre immédiatement à jour l'avatar
     * affiché dans l'application").
     */
    fun observeCurrentUserPhotoUri(): Flow<String?>

    /**
     * [optimizedJpegBytes] : déjà recadrée (carré) ET optimisée (résolution/compression) par
     * l'appelant (voir `ProfileFragment`, `CropImageOptions`) — cette fonction se contente
     * d'écrire le fichier local, de faire pivoter l'ancien (jamais l'inverse : le nouveau fichier
     * est écrit AVANT que l'ancien soit supprimé, voir l'implémentation) et de marquer la photo
     * comme en attente d'envoi au serveur (cahier des charges "Synchronisation hors ligne").
     * Ne fait rien silencieusement sans utilisateur courant — ne devrait pas arriver en pratique
     * (l'écran Profil exige déjà une session active).
     */
    suspend fun saveNewPhoto(optimizedJpegBytes: ByteArray)

    /**
     * Retour à l'avatar par défaut — jamais une suppression immédiate côté serveur (voir la KDoc de
     * tête de [com.arzikina.ne.data.local.entity.UserProfilePhotoEntity] : la ligne reste, avec
     * `localPath = null`, en attente de synchronisation, même principe que [saveNewPhoto]).
     */
    suspend fun deletePhoto()

    /**
     * Synchronise la photo de l'utilisateur courant avec le serveur — appelée par
     * [com.arzikina.ne.work.SyncWorker], jamais directement par la présentation (cahier des
     * charges "Synchronisation avec le serveur"/"Synchronisation hors ligne"/"Éviter les
     * conflits") :
     * - S'il y a un changement local EN ATTENTE (voir
     *   [com.arzikina.ne.data.local.entity.UserProfilePhotoEntity.pendingUpload]), l'envoie
     *   (nouvelle photo ou suppression) — PRIORITAIRE sur toute lecture serveur.
     * - Sinon, interroge le serveur et ne télécharge la photo qu'AU-DELÀ de la version déjà connue
     *   localement (jamais un retéléchargement inutile) — même principe "dernière version gagne"
     *   que le reste du moteur de synchronisation du projet.
     *
     * Ne lève AUCUNE exception "métier" : une erreur réseau/serveur remonte telle quelle (voir
     * l'implémentation), laissant le changement local `pendingUpload = true` intact pour une
     * nouvelle tentative au prochain déclenchement — même garantie que la file `sync_queue`
     * générique, sans réinventer de mécanisme de retry dédié.
     */
    suspend fun syncWithServer()
}
