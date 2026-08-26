package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

/**
 * Voir `data/local/dao/AccountDao` pour le raisonnement sur le filtrage systématique par `userId`.
 *
 * Suppression DOUCE (voir `CategoryDao`/`AccountDao` pour le raisonnement complet, même principe) :
 * toutes les lectures ci-dessous filtrent `deletedAt IS NULL`, [deleteById] est remplacé par
 * [softDeleteById] — voir `BudgetRepositoryImpl.deleteBudget` (étape 18.4). Contrairement à
 * `AccountEntity`, aucune cascade explicite à rattraper ici : la seule référence sortante de
 * `BudgetEntity` (`categoryId`, `onDelete = CASCADE`) ne se déclenche plus en pratique depuis que
 * `CategoryRepositoryImpl.deleteCategory` fait lui aussi une suppression DOUCE (un `UPDATE` ne
 * déclenche jamais une contrainte `ForeignKey.CASCADE` de Room/SQLite, seul un vrai `DELETE` le
 * ferait) — un budget peut donc survivre à la suppression douce de sa catégorie, comportement déjà
 * existant avant ce câblage, hors périmètre de cette étape. Seul [deleteAllForUser] (restauration
 * d'une sauvegarde) ignore le filtre `deletedAt IS NULL`.
 */
@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE userId = :userId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAllForUser(userId: Long): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id AND userId = :userId AND deletedAt IS NULL")
    suspend fun getById(id: Long, userId: Long): BudgetEntity?

    /** Réservé à `SyncEngineImpl` — voir la KDoc de `CategoryDao.getBySyncId` (même raisonnement,
     * volontairement SANS filtre `deletedAt IS NULL` ni `userId`). */
    @Query("SELECT * FROM budgets WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): BudgetEntity?

    /** Réservé à `SyncEngineImpl.enqueueUnsyncedLocalData` — voir la KDoc de
     * `CategoryDao.getUnsyncedForUser` (même raisonnement ; `BackupRepositoryImpl` étant ici le
     * seul chemin de contournement connu, aucun seeder par défaut n'existe pour cette entité). */
    @Query("SELECT * FROM budgets WHERE userId = :userId AND deletedAt IS NULL AND syncId IS NULL")
    suspend fun getUnsyncedForUser(userId: Long): List<BudgetEntity>

    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert` : toujours une
     * insertion neuve avec `id = 0L` lors d'une restauration, jamais une mise à jour). */
    @Insert
    suspend fun insertAll(budgets: List<BudgetEntity>): List<Long>

    /** Suppression DOUCE (voir la doc de tête) : `deletedAt`/`updatedAt` seulement, même principe
     * que `AccountDao.softDeleteById`. */
    @Query("UPDATE budgets SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND userId = :userId")
    suspend fun softDeleteById(id: Long, userId: Long, deletedAt: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de [userId]. */
    @Query("DELETE FROM budgets WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
