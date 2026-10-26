package com.naniger.arzikina.data.repository

import com.naniger.arzikina.data.issuedreceipt.LogoSyncPolicy
import com.naniger.arzikina.data.local.dao.ProductDao
import com.naniger.arzikina.data.local.entity.SyncQueueEntity
import com.naniger.arzikina.data.remote.api.HttpFailureException
import com.naniger.arzikina.data.remote.api.ProductImageApi
import com.naniger.arzikina.data.remote.dto.ProductSyncPayload
import com.naniger.arzikina.data.shop.ProductImageStore
import com.naniger.arzikina.domain.model.SyncOperation
import com.naniger.arzikina.domain.repository.SessionManager
import com.naniger.arzikina.domain.shop.ProductImagePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Transfert des FICHIERS de photos de produits, isolé de `SyncEngineImpl` (même découpage que les logos de
 * [IssuedReceiptSyncHandler]). Le JSON du produit ne porte que le nom ; l'image voyage à part.
 *
 * - **Envoi** : les photos citées par un lot `products` partent AVANT le JSON (un appareil qui reçoit le produit
 *   peut alors la télécharger). Refus définitif d'un fichier (voir [LogoSyncPolicy]) : ignoré, les données
 *   passent. Panne réseau/serveur : l'exception remonte et le lot repasse en échec.
 * - **Téléchargement** : après le pull, les photos citées par la base et absentes du disque sont récupérées (la
 *   liste vient de la base : elle survit à un redémarrage). Au plus [MAX_DOWNLOADS_PER_SYNC] par passage pour ne
 *   pas allonger la synchronisation ; le reste suit au passage suivant. Contenu vérifié avant écriture.
 */
@Singleton
class ProductImageSyncHandler @Inject constructor(
    private val store: ProductImageStore,
    private val api: ProductImageApi,
    private val productDao: ProductDao,
    private val sessionManager: SessionManager,
    private val json: Json
) {

    /** Photos déjà envoyées pendant ce processus (l'envoi est idempotent, ceci évite des requêtes inutiles). */
    private val uploaded: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Photos que le serveur ne peut pas fournir (404, refus définitif, contenu invalide) : pas de nouvel essai ce processus. */
    private val unavailable: MutableSet<String> = ConcurrentHashMap.newKeySet()

    suspend fun uploadImages(entries: List<SyncQueueEntity>) {
        val names = entries
            .filter { it.operation != SyncOperation.DELETE }
            .mapNotNull(::imageNameOf)
            .distinct()
        for (name in names) {
            if (name in uploaded) continue
            val file = store.resolve(name) ?: continue
            try {
                api.upload(name, file)
                uploaded.add(name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpFailureException) {
                if (!LogoSyncPolicy.isPermanentFailure(e.code)) throw e
            }
        }
    }

    /** Ne lève pas pour une panne réseau : on s'arrête, la prochaine synchronisation reprend. */
    suspend fun downloadMissingImages() {
        val userId = sessionManager.getCurrentUserIdOnce() ?: return
        var budget = MAX_DOWNLOADS_PER_SYNC
        for (name in productDao.imageFileNamesForUser(userId).distinct()) {
            if (budget == 0) return
            if (name in unavailable || !ProductImagePolicy.isSafeFileName(name) || store.resolve(name) != null) continue
            budget--
            val bytes = try {
                api.download(name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpFailureException) {
                if (LogoSyncPolicy.isPermanentFailure(e.code)) {
                    unavailable.add(name)
                    continue
                }
                return
            } catch (e: IOException) {
                return
            }
            if (bytes != null && ProductImagePolicy.isValidSyncedImage(name, bytes)) {
                store.writeNamed(name, bytes)
            } else {
                unavailable.add(name)
            }
        }
    }

    /** Nom de photo cité par le payload mis en file ; `null` si absent ou si le payload est d'un format antérieur. */
    private fun imageNameOf(entry: SyncQueueEntity): String? = runCatching {
        json.decodeFromString(ProductSyncPayload.serializer(), entry.payloadJson).imageFileName
    }.getOrNull()

    companion object {
        const val MAX_DOWNLOADS_PER_SYNC = 20
    }
}
