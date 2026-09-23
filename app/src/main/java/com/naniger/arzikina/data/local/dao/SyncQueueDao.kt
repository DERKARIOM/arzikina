package com.naniger.arzikina.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.naniger.arzikina.data.local.entity.SyncQueueEntity
import com.naniger.arzikina.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Voir `data/local/entity/SyncQueueEntity` : RIEN n'appelle encore ce DAO pour l'instant (étape
 * volontairement limitée à poser la fondation, voir sa doc de tête) — les méthodes ci-dessous
 * anticipent les besoins du futur Sync Engine (prendre un lot de `PENDING`, marquer
 * `SYNCING`/`SYNCED`/`FAILED`, purger) et du futur indicateur visuel de synchronisation
 * (`observeCountByStatus`, pour un badge "X en attente"/"erreur").
 *
 * Pas de filtrage par `userId` ici (contrairement aux autres DAO, voir `AccountDao`) : la file
 * d'attente est vidée AVANT tout changement de profil local actif dans le modèle envisagé (une
 * synchronisation en cours doit se terminer avant de changer de session) — à confirmer/affiner au
 * moment du câblage réel du Sync Engine.
 */
@Dao
interface SyncQueueDao {

    @Insert
    suspend fun insert(entry: SyncQueueEntity): Long

    @Update
    suspend fun update(entry: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getByStatus(status: SyncStatus): List<SyncQueueEntity>

    /** Pour le futur indicateur visuel (✓/↻/⚠/✕) — voir docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md. */
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = :status")
    fun observeCountByStatus(status: SyncStatus): Flow<Int>

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Purge des entrées `SYNCED` déjà confirmées (nettoyage périodique, voir section 8 du document). */
    @Query("DELETE FROM sync_queue WHERE status = :status")
    suspend fun deleteByStatus(status: SyncStatus)
}
