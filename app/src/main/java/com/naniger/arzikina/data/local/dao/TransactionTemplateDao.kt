package com.naniger.arzikina.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.naniger.arzikina.data.local.entity.TransactionTemplateEntity
import kotlinx.coroutines.flow.Flow

/** Voir `data/local/dao/AccountDao` pour le raisonnement sur le filtrage systématique par
 * `userId`. Pas de requête de recherche/filtre dédiée : même raisonnement que `ReceiptDao` — la
 * recherche et le filtre par catégorie (cahier des charges section 8) filtrent en mémoire la liste
 * déjà chargée de [observeAllForUser] (voir `MarketplaceViewModel`), un volume de modèles
 * personnel reste modeste.
 *
 * Suppression DOUCE (voir `RecurringTransactionDao` pour le raisonnement complet, même principe) :
 * toutes les lectures ci-dessous filtrent `deletedAt IS NULL`, [deleteById] est remplacé par
 * [softDeleteById] — étape "extension de la synchronisation à Marketplace personnelle" (voir
 * `TransactionTemplateRepositoryImpl`). Seul [deleteAllForUser] (restauration d'une sauvegarde)
 * ignore ce filtre.
 */
@Dao
interface TransactionTemplateDao {

    /** Favoris en tête (cahier des charges section 7), puis ordre alphabétique — jamais
     * recalculé côté appelant. */
    @Query("SELECT * FROM transaction_templates WHERE userId = :userId AND deletedAt IS NULL ORDER BY isFavorite DESC, name ASC")
    fun observeAllForUser(userId: Long): Flow<List<TransactionTemplateEntity>>

    @Query("SELECT * FROM transaction_templates WHERE id = :id AND userId = :userId AND deletedAt IS NULL")
    suspend fun getById(id: Long, userId: Long): TransactionTemplateEntity?

    /** Réservé à `SyncEngineImpl` — voir la KDoc de `CategoryDao.getBySyncId` (même raisonnement,
     * volontairement SANS filtre `deletedAt IS NULL` ni `userId`). */
    @Query("SELECT * FROM transaction_templates WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): TransactionTemplateEntity?

    /** Réservé aux résolveurs de `TransactionTemplateRepositoryImpl` — voir la KDoc de
     * `AccountDao.getByIdIncludingDeleted` (même raisonnement : filet de sécurité contre une
     * référence déjà soft-supprimée par ailleurs). */
    @Query("SELECT * FROM transaction_templates WHERE id = :id AND userId = :userId")
    suspend fun getByIdIncludingDeleted(id: Long, userId: Long): TransactionTemplateEntity?

    /** Réservé à `SyncEngineImpl.enqueueUnsyncedLocalData` — voir la KDoc de
     * `CategoryDao.getUnsyncedForUser` (même raisonnement). */
    @Query("SELECT * FROM transaction_templates WHERE userId = :userId AND deletedAt IS NULL AND syncId IS NULL")
    suspend fun getUnsyncedForUser(userId: Long): List<TransactionTemplateEntity>

    /** Retourne l'id de la ligne insérée, ou -1 en cas de mise à jour (voir `AccountDao.upsert`). */
    @Upsert
    suspend fun upsert(template: TransactionTemplateEntity): Long

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert` : toujours une
     * insertion neuve avec `id = 0L` lors d'une restauration, jamais une mise à jour). */
    @Insert
    suspend fun insertAll(templates: List<TransactionTemplateEntity>): List<Long>

    /** Suppression DOUCE (voir la doc de tête) : `deletedAt`/`updatedAt` seulement, même principe
     * que `RecurringTransactionDao.softDeleteById`. */
    @Query("UPDATE transaction_templates SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND userId = :userId")
    suspend fun softDeleteById(id: Long, userId: Long, deletedAt: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de
     * [userId] (voir `AccountDao.deleteAllForUser`). */
    @Query("DELETE FROM transaction_templates WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
