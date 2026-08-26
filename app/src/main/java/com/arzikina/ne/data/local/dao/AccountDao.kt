package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

/**
 * Accès Room aux comptes.
 *
 * Les écritures passent par [Upsert] : la couche appelante fournit l'état
 * final souhaité sans avoir à savoir si le compte existe déjà en base.
 *
 * Toutes les requêtes paramétrées par un `id` filtrent aussi par `userId` :
 * même une clé primaire connue ne doit jamais permettre de lire/modifier la
 * ligne d'un autre utilisateur (voir `data/repository/AccountRepositoryImpl`).
 *
 * Suppression DOUCE (voir `CategoryDao`/`PersonDao` pour le raisonnement complet, même principe) :
 * toutes les lectures ci-dessous filtrent `deletedAt IS NULL`, [deleteById] est remplacé par
 * [softDeleteById] — voir `AccountRepositoryImpl.deleteAccount` pour le rattrapage explicite des
 * cascades (`loans`/`loan_payments`/`transactions`) que SQLite ne peut plus fournir sur un simple
 * `UPDATE` (étape 16.1, avant même l'introduction de ce `softDeleteById`). Seul
 * [deleteAllForUser] (restauration d'une sauvegarde) ignore ce filtre.
 */
@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE userId = :userId AND deletedAt IS NULL ORDER BY createdAt ASC")
    fun observeAllForUser(userId: Long): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id AND userId = :userId AND deletedAt IS NULL")
    suspend fun getById(id: Long, userId: Long): AccountEntity?

    /** Réservé à `SyncEngineImpl` — voir la KDoc de `CategoryDao.getBySyncId` (même raisonnement,
     * volontairement SANS filtre `deletedAt IS NULL` ni `userId`). */
    @Query("SELECT * FROM accounts WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): AccountEntity?

    /** Réservé à `SyncEngineImpl.enqueueUnsyncedLocalData` — voir la KDoc de
     * `CategoryDao.getUnsyncedForUser` (même raisonnement ; `BackupRepositoryImpl` étant ici le
     * seul chemin de contournement connu, aucun seeder par défaut n'existe pour cette entité). */
    @Query("SELECT * FROM accounts WHERE userId = :userId AND deletedAt IS NULL AND syncId IS NULL")
    suspend fun getUnsyncedForUser(userId: Long): List<AccountEntity>

    /** Retourne l'id de la ligne insérée, ou -1 en cas de mise à jour (comportement standard
     * de Room @Upsert) — voir `AccountRepositoryImpl.saveAccount` pour la logique qui en dépend. */
    @Upsert
    suspend fun upsert(account: AccountEntity): Long

    /** Utilisé UNIQUEMENT par la restauration d'une sauvegarde (`BackupRepositoryImpl`), qui force
     * toujours `id = 0L` avant d'appeler cette méthode : `@Insert` (pas `@Upsert`) car c'est
     * TOUJOURS une insertion neuve, jamais une mise à jour d'une ligne existante — voir la doc de
     * tête de `BackupMappers` sur la réattribution des ids. Retourne les ids générés, dans le même
     * ordre que [accounts], pour construire la table de correspondance ancien → nouvel id. */
    @Insert
    suspend fun insertAll(accounts: List<AccountEntity>): List<Long>

    /** Suppression DOUCE (voir la doc de tête) : `deletedAt`/`updatedAt` seulement, même principe
     * que `CategoryDao.softDeleteById`/`PersonDao.softDeleteById`. Ne touche PAS `loans`/
     * `loan_payments`/`transactions` — voir `AccountRepositoryImpl.deleteAccount`, qui supprime
     * explicitement (suppression PHYSIQUE, inchangée) tout ce qui en dépend AVANT d'appeler cette
     * méthode (étape 16.1) : les cascades SQLite `accountId`/`transferAccountId` ne se déclenchent
     * que sur un vrai `DELETE`, jamais sur cet `UPDATE`. */
    @Query("UPDATE accounts SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND userId = :userId")
    suspend fun softDeleteById(id: Long, userId: Long, deletedAt: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de [userId]. */
    @Query("DELETE FROM accounts WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
