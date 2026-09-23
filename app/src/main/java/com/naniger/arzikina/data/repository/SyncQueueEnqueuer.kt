package com.naniger.arzikina.data.repository

import com.naniger.arzikina.data.local.dao.SyncQueueDao
import com.naniger.arzikina.data.local.entity.SyncQueueEntity
import com.naniger.arzikina.domain.model.SyncOperation
import com.naniger.arzikina.domain.model.SyncStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Point d'entrée UNIQUE pour enfiler une écriture dans `sync_queue` (voir
 * `data/local/entity/SyncQueueEntity.kt`) — chaque repository rendu synchronisable (en commençant
 * par [CategoryRepositoryImpl]) appelle [enqueue] APRÈS son écriture Room habituelle, jamais à sa
 * place. Centralise ainsi la construction de l'entrée de file (statut initial, horodatage) pour
 * qu'un futur deuxième repository câblé n'ait pas à la dupliquer.
 *
 * Prend volontairement un [payloadJson] DÉJÀ sérialisé plutôt qu'un type générique : chaque
 * repository connaît le format exact attendu par sa route `push.php` (voir `CategorySyncPayload`)
 * et le sérialise lui-même via l'instance [kotlinx.serialization.json.Json] partagée
 * (`NetworkModule.provideJson`) — évite d'imposer ici une abstraction générique/réflexive sur les
 * types de payload, qui n'apporterait rien tant qu'une seule entité n'est câblée.
 */
@Singleton
class SyncQueueEnqueuer @Inject constructor(
    private val syncQueueDao: SyncQueueDao
) {
    suspend fun enqueue(
        entityType: String,
        entitySyncId: String,
        operation: SyncOperation,
        payloadJson: String
    ) {
        syncQueueDao.insert(
            SyncQueueEntity(
                entityType = entityType,
                entitySyncId = entitySyncId,
                operation = operation,
                payloadJson = payloadJson,
                createdAt = System.currentTimeMillis(),
                status = SyncStatus.PENDING
            )
        )
    }
}
