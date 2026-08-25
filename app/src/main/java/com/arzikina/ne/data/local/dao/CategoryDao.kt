package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.CategoryEntity
import com.arzikina.ne.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

/**
 * Voir `data/local/dao/AccountDao` pour le raisonnement sur le filtrage systématique par `userId`.
 *
 * Suppression DOUCE (voir `CategoryEntity.deletedAt`, `CategoryRepositoryImpl.deleteCategory`,
 * docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md section 8) : toutes les lectures ci-dessous filtrent
 * `deletedAt IS NULL` — une ligne supprimée existe encore physiquement (nécessaire pour envoyer
 * l'opération DELETE au serveur via `sync_queue`) mais ne doit plus jamais apparaître comme une
 * catégorie active, y compris dans [getFirstByNameForUser] (une catégorie système supprimée doit
 * pouvoir être recréée, pas "retrouvée" telle quelle). Seul [deleteAllForUser] (restauration d'une
 * sauvegarde, purge complète) ignore ce filtre : il ne s'agit pas d'une suppression au sens métier.
 */
@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE userId = :userId AND deletedAt IS NULL ORDER BY name ASC")
    fun observeAllForUser(userId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type AND userId = :userId AND deletedAt IS NULL ORDER BY name ASC")
    fun observeByTypeForUser(type: TransactionType, userId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id AND userId = :userId AND deletedAt IS NULL")
    suspend fun getById(id: Long, userId: Long): CategoryEntity?

    /** Réservé à [com.arzikina.ne.data.repository.SyncEngineImpl] : volontairement SANS filtre
     * `deletedAt IS NULL` (contrairement à [getById] ci-dessus) — le moteur de synchronisation doit
     * pouvoir retrouver une ligne même APRÈS une suppression douce confirmée par le serveur, pour y
     * appliquer `serverEntity` (voir la KDoc de `push.php`, section résolution de conflit). Pas de
     * filtre `userId` non plus : `syncId` est déjà unique globalement (voir l'index de
     * `CategoryEntity`), et le Sync Engine ne traite jamais que les entrées de l'utilisateur
     * courant (voir la KDoc de `SyncQueueEntity` sur l'absence de `userId` dans la file). */
    @Query("SELECT * FROM categories WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): CategoryEntity?

    /** Réservé à [com.arzikina.ne.data.repository.SyncEngineImpl.enqueueUnsyncedLocalData] :
     * lignes actives (`deletedAt IS NULL`) jamais proposées à la synchronisation — créées par un
     * chemin qui contourne `CategoryRepositoryImpl.saveCategory` (ex. `NewUserDefaultDataSeeder` à
     * l'inscription, `BackupRepositoryImpl` lors d'une restauration), donc sans `syncId`. */
    @Query("SELECT * FROM categories WHERE userId = :userId AND deletedAt IS NULL AND syncId IS NULL")
    suspend fun getUnsyncedForUser(userId: Long): List<CategoryEntity>

    /** Utilisé par `LoanRepositoryImpl` pour retrouver l'une des 4 catégories par défaut Prêts/
     * Emprunts (voir `DefaultCategories`) par son nom exact — l'icône seule ([CategoryIcon.LOAN])
     * ne suffit pas à les distinguer : deux d'entre elles partagent le même [TransactionType]
     * (ex. "Prêt accordé" et "Remboursement d'emprunt" sont toutes deux des dépenses). */
    @Query("SELECT * FROM categories WHERE name = :name AND userId = :userId AND deletedAt IS NULL LIMIT 1")
    suspend fun getFirstByNameForUser(name: String, userId: Long): CategoryEntity?

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert` : toujours une
     * insertion neuve avec `id = 0L` lors d'une restauration, jamais une mise à jour). */
    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    /** Suppression DOUCE (voir la doc de tête) : `deletedAt`/`updatedAt` seulement, jamais les
     * autres colonnes — même principe que la branche DELETE de `server/api/sync/push.php`
     * (`upsertExistingCategory`), pour que l'état local et l'état serveur restent le même genre de
     * mutation. `version` n'est PAS incrémentée ici : voir la KDoc de
     * `CategoryRepositoryImpl.saveCategory` sur la progression de `version`, uniquement via le
     * futur Sync Engine. */
    @Query("UPDATE categories SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND userId = :userId")
    suspend fun softDeleteById(id: Long, userId: Long, deletedAt: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de
     * [userId] — suppression PHYSIQUE assumée (voir la doc de tête), pas une suppression métier. */
    @Query("DELETE FROM categories WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
