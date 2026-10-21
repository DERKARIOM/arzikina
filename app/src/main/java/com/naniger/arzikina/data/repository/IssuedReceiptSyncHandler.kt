package com.naniger.arzikina.data.repository

import androidx.room.withTransaction
import com.naniger.arzikina.data.issuedreceipt.IssuedReceiptSyncMapper
import com.naniger.arzikina.data.issuedreceipt.IssuerLogoStorage
import com.naniger.arzikina.data.issuedreceipt.LogoSyncPolicy
import com.naniger.arzikina.data.local.dao.IssuedReceiptDao
import com.naniger.arzikina.data.local.dao.IssuerProfileDao
import com.naniger.arzikina.data.local.dao.UserServerLinkDao
import com.naniger.arzikina.data.local.database.ArzikinaDatabase
import com.naniger.arzikina.data.local.entity.IssuedReceiptEntity
import com.naniger.arzikina.data.local.entity.IssuerProfileEntity
import com.naniger.arzikina.data.local.entity.SyncQueueEntity
import com.naniger.arzikina.data.remote.api.HttpFailureException
import com.naniger.arzikina.data.remote.api.IssuerLogoApi
import com.naniger.arzikina.data.remote.dto.IssuedReceiptServerStateDto
import com.naniger.arzikina.data.remote.dto.IssuedReceiptSyncPayload
import com.naniger.arzikina.data.remote.dto.IssuerProfileServerStateDto
import com.naniger.arzikina.data.remote.dto.IssuerProfileSyncPayload
import com.naniger.arzikina.domain.issuedreceipt.IssuerLogoPolicy
import com.naniger.arzikina.domain.issuedreceipt.ReceiptNumber
import com.naniger.arzikina.domain.model.SyncOperation
import com.naniger.arzikina.domain.repository.SessionManager
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Tout ce qui concerne la synchronisation du profil émetteur (`issuer_profiles`) et des reçus émis
 * (`issued_receipts`), isolé de `SyncEngineImpl` (déjà très volumineux) : mise en file des écritures,
 * application des états serveur, doublons de numéro, transfert des logos. `SyncEngineImpl` ne fait
 * que déléguer (voir ses points d'appel).
 *
 * Décisions (détail : `claude/issued-receipts/SYNC-RECUS-EMIS-CONTRAT.md`) :
 * - **Profil** : `syncId` DÉTERMINISTE dérivé de l'identifiant serveur de l'utilisateur
 *   ([IssuedReceiptSyncMapper.profileSyncId]) — le serveur n'accepte qu'un profil par compte, donc
 *   tous les appareils doivent viser la même ligne. Sans lien serveur (jamais connecté), pas de
 *   `syncId` ni de mise en file : [enqueueUnsynced] rattrape après la connexion. Premier pull : le
 *   profil serveur l'emporte sur un profil local jamais synchronisé.
 * - **Reçus** : `syncId` aléatoire. Ils sont IMMUABLES (instantané émetteur) ; seules les mises à jour
 *   (modification, suppression, renumérotation d'un doublon) sont synchronisées.
 * - **Doublon de numéro** (deux appareils hors ligne ont attribué le même) : le serveur n'impose pas
 *   l'unicité, l'index local `(userId, number)` oui. Le perdant — voir [ReceiptNumber.loses] — est
 *   renuméroté ; si c'est la ligne locale, la renumérotation est poussée ; si c'est la ligne entrante,
 *   elle est rangée sous un numéro disponible, sans rien pousser (l'appareil qui la possède pousse la
 *   vraie renumérotation, que le pull appliquera ensuite).
 * - **Logos** : envoyés AVANT le lot JSON qui les cite ; téléchargés après le pull. Un logo manquant
 *   n'empêche jamais la synchronisation des données (le PDF retombe sur le logo actuel du profil).
 */
@Singleton
class IssuedReceiptSyncHandler @Inject constructor(
    private val database: ArzikinaDatabase,
    private val receiptDao: IssuedReceiptDao,
    private val profileDao: IssuerProfileDao,
    private val userServerLinkDao: UserServerLinkDao,
    private val syncQueueEnqueuer: SyncQueueEnqueuer,
    private val sessionManager: SessionManager,
    private val logoStorage: IssuerLogoStorage,
    private val logoApi: IssuerLogoApi,
    private val json: Json
) {

    /** Logos déjà envoyés pendant ce processus (l'envoi est idempotent, ceci évite les requêtes inutiles). */
    private val uploadedLogos: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Logos cités par des données reçues et absents du disque, à télécharger (mémoire : voir [downloadMissingLogos]). */
    private val pendingLogoDownloads: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // ───────────────────────────── Mise en file (écritures locales) ─────────────────────────────

    /** `syncId` du profil de [userId], ou `null` tant que le compte n'est pas lié à un utilisateur serveur. */
    suspend fun profileSyncIdFor(userId: Long): String? =
        userServerLinkDao.getByLocalUserId(userId)?.serverUserId?.let(IssuedReceiptSyncMapper::profileSyncId)

    /** [entity] doit déjà porter son `syncId` et être enregistré. */
    suspend fun enqueueProfile(entity: IssuerProfileEntity, operation: SyncOperation) {
        val syncId = requireNotNull(entity.syncId) { "syncId doit être attribué avant l'enfilage." }
        val payload = IssuedReceiptSyncMapper.toPayload(entity, syncId, operation)
        syncQueueEnqueuer.enqueue(
            entityType = ENTITY_TYPE_PROFILE,
            entitySyncId = syncId,
            operation = operation,
            payloadJson = json.encodeToString(IssuerProfileSyncPayload.serializer(), payload)
        )
    }

    /** [entity] doit déjà porter son `syncId` et être enregistré. */
    suspend fun enqueueReceipt(entity: IssuedReceiptEntity, operation: SyncOperation) {
        val syncId = requireNotNull(entity.syncId) { "syncId doit être attribué avant l'enfilage." }
        val payload = IssuedReceiptSyncMapper.toPayload(entity, syncId, operation)
        syncQueueEnqueuer.enqueue(
            entityType = ENTITY_TYPE_RECEIPT,
            entitySyncId = syncId,
            operation = operation,
            payloadJson = json.encodeToString(IssuedReceiptSyncPayload.serializer(), payload)
        )
    }

    /** Rattrapage (voir `SyncEngine.enqueueUnsyncedLocalData`) : lignes locales créées avant la synchronisation. */
    suspend fun enqueueUnsynced(userId: Long) {
        profileDao.getForUser(userId)?.takeIf { it.syncId == null }?.let { profile ->
            val syncId = profileSyncIdFor(userId) ?: return@let
            val entity = profile.copy(syncId = syncId)
            profileDao.upsert(entity)
            enqueueProfile(entity, SyncOperation.CREATE)
        }
        receiptDao.getUnsyncedForUser(userId).forEach { receipt ->
            val entity = receipt.copy(syncId = UUID.randomUUID().toString())
            receiptDao.upsert(entity)
            enqueueReceipt(entity, SyncOperation.CREATE)
        }
    }

    // ───────────────────────────── Application des états serveur ─────────────────────────────

    /**
     * Voir `SyncEngineImpl.applyServerEntity`. [allowCreate] : `false` pour un résultat de push (la
     * ligne existe forcément déjà), `true` pour un pull. En pull, un profil local jamais synchronisé
     * (autre `syncId`) est ADOPTÉ : la ligne locale prend l'état du serveur.
     */
    suspend fun applyProfile(state: IssuerProfileServerStateDto, allowCreate: Boolean) {
        val bySyncId = profileDao.getBySyncId(state.id)
        val userId = bySyncId?.userId ?: sessionManager.getCurrentUserIdOnce() ?: return
        val local = bySyncId ?: if (allowCreate) profileDao.getAnyForUser(userId) else null
        if (local == null && !allowCreate) return
        profileDao.upsert(IssuedReceiptSyncMapper.toEntity(state, userId, local))
        rememberLogo(state.logoFileName)
    }

    /** Voir [applyProfile] ; en plus, résout un éventuel doublon de numéro (voir la doc de la classe). */
    suspend fun applyReceipt(state: IssuedReceiptServerStateDto, allowCreate: Boolean) {
        database.withTransaction {
            val local = receiptDao.getBySyncId(state.id)
            if (local == null && !allowCreate) return@withTransaction
            val userId = local?.userId ?: sessionManager.getCurrentUserIdOnce() ?: return@withTransaction
            val number = resolveNumber(userId, state, local)
            receiptDao.upsert(IssuedReceiptSyncMapper.toEntity(state, userId, local, number))
            Unit
        }
        rememberLogo(state.issuerLogoFileName)
    }

    /** Numéro sous lequel ranger [state] localement ; libère le numéro si c'est la ligne locale qui perd. */
    private suspend fun resolveNumber(userId: Long, state: IssuedReceiptServerStateDto, local: IssuedReceiptEntity?): String {
        val holder = receiptDao.getByNumber(userId, state.number)
        if (holder == null || holder.id == local?.id) return state.number

        val incoming = ReceiptNumber.Claim(state.deletedAt != null, state.createdAt, state.id)
        val existing = ReceiptNumber.Claim(holder.deletedAt != null, holder.createdAt, holder.syncId.orEmpty())
        if (ReceiptNumber.loses(incoming, existing)) {
            // Le numéro actuel de la ligne locale (déjà renumérotée lors d'un pull précédent) compte comme libre.
            val taken = receiptDao.getNumbersWithPrefix(userId, state.number).filterNot { it == local?.number }
            return ReceiptNumber.disambiguated(state.number, taken)
        }
        renumber(userId, holder)
        return state.number
    }

    /** Renumérote [holder] (perdant) ; la modification est poussée sauf s'il est supprimé ou jamais synchronisé. */
    private suspend fun renumber(userId: Long, holder: IssuedReceiptEntity) {
        val newNumber = ReceiptNumber.disambiguated(holder.number, receiptDao.getNumbersWithPrefix(userId, holder.number))
        val active = holder.deletedAt == null
        val renamed = holder.copy(
            number = newNumber,
            pdfFileName = null,
            updatedAt = if (active) System.currentTimeMillis() else holder.updatedAt
        )
        receiptDao.update(renamed)
        if (active && renamed.syncId != null) enqueueReceipt(renamed, SyncOperation.UPDATE)
    }

    // ───────────────────────────── Logos ─────────────────────────────

    /**
     * Envoie les logos cités par les entrées [entries] (profil/reçus, hors suppressions) AVANT leur
     * envoi JSON. Refus définitif d'un fichier (voir [LogoSyncPolicy]) : ignoré, les données passent
     * quand même. Panne réseau ou serveur : l'exception remonte et le lot repasse en échec.
     */
    suspend fun uploadLogos(entries: List<SyncQueueEntity>) {
        val names = entries.filter { it.operation != SyncOperation.DELETE }.flatMap(::logoNamesOf).distinct()
        for (name in names) {
            if (name in uploadedLogos) continue
            val file = logoStorage.resolveFile(name) ?: continue
            try {
                logoApi.upload(name, file)
                uploadedLogos.add(name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpFailureException) {
                if (!LogoSyncPolicy.isPermanentFailure(e.code)) throw e
            }
        }
    }

    /**
     * Télécharge les logos manquants (celui du profil courant + ceux cités par les données reçues ce
     * processus). Ne lève pas pour une panne réseau : on s'arrête, la prochaine synchronisation
     * reprend. Limite assumée : la liste des logos de reçus à récupérer vit en mémoire ; après un
     * redémarrage du processus seul le logo du profil est retrouvé (le PDF d'un ancien reçu retombe
     * de toute façon sur le logo actuel du profil).
     */
    suspend fun downloadMissingLogos() {
        sessionManager.getCurrentUserIdOnce()?.let { profileDao.getForUser(it) }?.logoFileName?.let(::rememberLogo)
        for (name in pendingLogoDownloads.toList()) {
            if (logoStorage.resolveFile(name) != null) {
                pendingLogoDownloads.remove(name)
                continue
            }
            val bytes = try {
                logoApi.download(name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpFailureException) {
                if (LogoSyncPolicy.isPermanentFailure(e.code)) {
                    pendingLogoDownloads.remove(name)
                    continue
                }
                return
            } catch (e: IOException) {
                return
            }
            if (bytes != null && IssuerLogoPolicy.isValidSyncedLogo(name, bytes)) logoStorage.writeNamed(name, bytes)
            pendingLogoDownloads.remove(name)
        }
    }

    private fun rememberLogo(fileName: String?) {
        if (fileName != null && IssuerLogoPolicy.isSafeFileName(fileName) && logoStorage.resolveFile(fileName) == null) {
            pendingLogoDownloads.add(fileName)
        }
    }

    private fun logoNamesOf(entry: SyncQueueEntity): List<String> = runCatching {
        when (entry.entityType) {
            ENTITY_TYPE_PROFILE ->
                IssuedReceiptSyncMapper.logoNames(json.decodeFromString(IssuerProfileSyncPayload.serializer(), entry.payloadJson))
            ENTITY_TYPE_RECEIPT ->
                IssuedReceiptSyncMapper.logoNames(json.decodeFromString(IssuedReceiptSyncPayload.serializer(), entry.payloadJson))
            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    companion object {
        const val ENTITY_TYPE_PROFILE = "issuer_profiles"
        const val ENTITY_TYPE_RECEIPT = "issued_receipts"

        /** Types dont l'envoi nécessite d'abord celui des logos. */
        val ENTITY_TYPES: Set<String> = setOf(ENTITY_TYPE_PROFILE, ENTITY_TYPE_RECEIPT)
    }
}
