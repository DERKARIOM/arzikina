package com.naniger.arzikina.data.repository

import android.content.Context
import com.naniger.arzikina.data.local.dao.UserDao
import com.naniger.arzikina.data.local.dao.UserProfilePhotoDao
import com.naniger.arzikina.data.local.entity.UserProfilePhotoEntity
import com.naniger.arzikina.data.profile.ProfilePhotoFileStorage
import com.naniger.arzikina.data.remote.api.HttpFailureException
import com.naniger.arzikina.data.remote.api.ProfilePhotoApi
import com.naniger.arzikina.data.remote.dto.ProfilePhotoSyncResponseDto
import com.naniger.arzikina.di.IoDispatcher
import com.naniger.arzikina.domain.repository.ProfilePhotoRepository
import com.naniger.arzikina.domain.repository.SessionManager
import com.naniger.arzikina.work.SyncWorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implémentation [ProfilePhotoRepository] — voir sa doc de tête et celle de
 * [UserProfilePhotoEntity]. Combine quatre responsabilités séparées, chacune déjà éprouvée
 * ailleurs dans le projet : [UserProfilePhotoDao] (état de sync), [ProfilePhotoFileStorage]
 * (octets sur le disque), [UserDao.updateProfilePhotoUri] (mirroir vers l'ancien champ lu par
 * Settings/Dashboard — voir sa KDoc), [ProfilePhotoApi] (transport réseau, voir [syncWithServer]).
 */
class ProfilePhotoRepositoryImpl @Inject constructor(
    private val userProfilePhotoDao: UserProfilePhotoDao,
    private val userDao: UserDao,
    private val fileStorage: ProfilePhotoFileStorage,
    private val sessionManager: SessionManager,
    private val profilePhotoApi: ProfilePhotoApi,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ProfilePhotoRepository {

    override fun observeCurrentUserPhotoUri(): Flow<String?> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(null)
            } else {
                userProfilePhotoDao.observeByUserId(userId).map { photo ->
                    photo?.localPath?.let { fileStorage.contentUriFor(it).toString() }
                }
            }
        }

    override suspend fun saveNewPhoto(optimizedJpegBytes: ByteArray) = withContext(ioDispatcher) {
        val userId = sessionManager.getCurrentUserIdOnce() ?: return@withContext
        val existing = userProfilePhotoDao.findByUserId(userId)

        // Écrit le NOUVEAU fichier avant de toucher quoi que ce soit d'autre : en cas d'échec ici,
        // ni la ligne Room ni l'ancien fichier ne sont modifiés (voir la KDoc de
        // ProfilePhotoRepository.saveNewPhoto, "jamais l'inverse").
        val newRelativePath = fileStorage.writeOptimizedImage(optimizedJpegBytes)
        val now = System.currentTimeMillis()

        userProfilePhotoDao.upsert(
            UserProfilePhotoEntity(
                id = existing?.id ?: 0L,
                userId = userId,
                localPath = newRelativePath,
                serverUrl = existing?.serverUrl,
                version = (existing?.version ?: 0L) + 1,
                pendingUpload = true,
                updatedAt = now
            )
        )
        userDao.updateProfilePhotoUri(userId, fileStorage.contentUriFor(newRelativePath).toString())

        // Nettoyage de l'ancien fichier APRÈS que la nouvelle ligne a été acceptée — jamais de
        // fichier orphelin (cahier des charges, "vérifier qu'aucun doublon ou fichier orphelin
        // n'est créé"), et jamais de fenêtre sans photo valide en cas d'échec entre les deux étapes.
        existing?.localPath
            ?.takeIf { it != newRelativePath }
            ?.let { fileStorage.deleteFile(it) }

        // Tentative d'envoi IMMÉDIATE si une connexion est disponible (cahier des charges
        // "Synchronisation hors ligne" : "connexion disponible → upload vers le serveur", pas
        // seulement au prochain cycle périodique de 6h) — sans effet si hors ligne, WorkManager
        // différera lui-même l'exécution (voir SyncWorkScheduler, contrainte NetworkType.CONNECTED).
        // `pendingUpload = true` déjà posé ci-dessus garantit qu'aucune tentative n'est perdue même
        // si l'app est tuée avant que ce déclenchement n'aboutisse.
        SyncWorkScheduler.triggerNow(context)
    }

    override suspend fun deletePhoto() = withContext(ioDispatcher) {
        val userId = sessionManager.getCurrentUserIdOnce() ?: return@withContext
        val existing = userProfilePhotoDao.findByUserId(userId) ?: return@withContext
        val oldLocalPath = existing.localPath ?: return@withContext

        userProfilePhotoDao.upsert(
            existing.copy(
                localPath = null,
                version = existing.version + 1,
                pendingUpload = true,
                updatedAt = System.currentTimeMillis()
            )
        )
        userDao.updateProfilePhotoUri(userId, null)
        fileStorage.deleteFile(oldLocalPath)

        // Même raisonnement que la fin de [saveNewPhoto].
        SyncWorkScheduler.triggerNow(context)
    }

    /**
     * Voir la KDoc de tête de [ProfilePhotoRepository.syncWithServer]. Exceptions volontairement
     * NON interceptées ici (sauf un cas précis, voir ci-dessous) : elles remontent jusqu'à
     * [com.naniger.arzikina.work.SyncWorker], qui les traite déjà comme un échec transitoire
     * (`Result.retry()`) — dupliquer cette logique ici introduirait un second mécanisme de retry,
     * contrairement aux instructions du projet.
     *
     * Seule exception : [pullRemoteChangeIfNewer] intercepte elle-même un 404 sur le téléchargement
     * de la photo (fichier serveur introuvable — PAS transitoire, jamais résolu par un retry) plutôt
     * que de le laisser remonter jusqu'ici.
     */
    override suspend fun syncWithServer() = withContext(ioDispatcher) {
        val userId = sessionManager.getCurrentUserIdOnce() ?: return@withContext
        val local = userProfilePhotoDao.findByUserId(userId)

        if (local != null && local.pendingUpload) {
            pushLocalChange(userId, local)
        } else {
            pullRemoteChangeIfNewer(userId, local)
        }
    }

    /** Envoie le changement local EN ATTENTE — une nouvelle photo (upload du fichier déjà sur le
     *  disque) ou une suppression (`localPath == null`), jamais les deux à la fois (une seule ligne,
     *  voir la KDoc de tête de [UserProfilePhotoEntity]). */
    private suspend fun pushLocalChange(userId: Long, local: UserProfilePhotoEntity) {
        val localPath = local.localPath
        val response = if (localPath != null) {
            profilePhotoApi.uploadPhoto(fileStorage.resolveFile(localPath).readBytes())
        } else {
            profilePhotoApi.deletePhoto()
        }

        // `localPath` INCHANGÉ ici (toujours le fichier déjà écrit localement, voir [saveNewPhoto]) —
        // seuls `serverUrl`/`version`/`updatedAt`/`pendingUpload` reflètent maintenant la
        // confirmation serveur. Une suppression (`localPath == null`) reste `null` des deux côtés.
        userProfilePhotoDao.upsert(
            local.copy(
                serverUrl = response.photoPath,
                version = response.version,
                pendingUpload = false,
                updatedAt = response.updatedAt
            )
        )
    }

    /**
     * Interroge le serveur (lecture seule) et n'agit QUE si sa version est strictement plus récente
     * que celle déjà connue localement (`response.version <= localVersion` : rien à faire, jamais de
     * retéléchargement inutile — cahier des charges "Gestion du cache"). Couvre les DEUX sens
     * possibles d'un changement venu d'un autre appareil : nouvelle photo (téléchargée puis mise en
     * cache local) ou suppression (`photoPath == null`, avatar par défaut appliqué ici aussi).
     */
    private suspend fun pullRemoteChangeIfNewer(userId: Long, local: UserProfilePhotoEntity?) {
        val response = profilePhotoApi.getPhoto()
        val localVersion = local?.version ?: 0L
        if (response.version <= localVersion) return

        val remotePhotoPath = response.photoPath
        if (remotePhotoPath == null) {
            clearLocalPhoto(userId, local, response)
            return
        }

        try {
            val bytes = profilePhotoApi.downloadPhoto(remotePhotoPath)
            val newLocalPath = fileStorage.writeOptimizedImage(bytes)
            val oldLocalPath = local?.localPath

            userProfilePhotoDao.upsert(
                UserProfilePhotoEntity(
                    id = local?.id ?: 0L,
                    userId = userId,
                    localPath = newLocalPath,
                    serverUrl = remotePhotoPath,
                    version = response.version,
                    pendingUpload = false,
                    updatedAt = response.updatedAt
                )
            )
            userDao.updateProfilePhotoUri(userId, fileStorage.contentUriFor(newLocalPath).toString())

            oldLocalPath?.takeIf { it != newLocalPath }?.let { fileStorage.deleteFile(it) }
        } catch (e: HttpFailureException) {
            // 404 : le fichier référencé par `photo_path` n'existe plus côté serveur (supprimé
            // hors du flux normal, chemin corrompu...) — PAS une erreur transitoire. La laisser
            // remonter ferait boucler SyncWorker indéfiniment en `Result.retry()` (voir sa KDoc)
            // sans jamais réussir. Traité comme `remotePhotoPath == null` ci-dessus : avatar par
            // défaut affiché localement plutôt qu'une boucle de retry sur une ressource qui ne
            // reviendra pas d'elle-même. Toute autre erreur (réseau, 5xx...) continue de remonter
            // normalement : c'est bien transitoire, le retry reste la bonne réponse.
            if (e.code != 404) throw e
            clearLocalPhoto(userId, local, response)
        }
    }

    /** État local "pas de photo" — factorisé entre `remotePhotoPath == null` (suppression confirmée
     *  par le serveur) et un 404 au téléchargement (fichier serveur introuvable, voir
     *  [pullRemoteChangeIfNewer]) : même résultat dans les deux cas, jamais de photo orpheline
     *  affichée localement ni de fichier local qui ne correspond plus à rien côté serveur. */
    private suspend fun clearLocalPhoto(
        userId: Long,
        local: UserProfilePhotoEntity?,
        response: ProfilePhotoSyncResponseDto
    ) {
        local?.localPath?.let { fileStorage.deleteFile(it) }
        userProfilePhotoDao.upsert(
            UserProfilePhotoEntity(
                id = local?.id ?: 0L,
                userId = userId,
                localPath = null,
                serverUrl = null,
                version = response.version,
                pendingUpload = false,
                updatedAt = response.updatedAt
            )
        )
        userDao.updateProfilePhotoUri(userId, null)
    }
}
