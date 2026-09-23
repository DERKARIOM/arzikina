package com.naniger.arzikina.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.naniger.arzikina.data.local.entity.AccountEntity
import com.naniger.arzikina.domain.model.AccountType
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

    @Query("SELECT * FROM accounts WHERE userId = :userId AND deletedAt IS NULL ORDER BY displayOrder ASC")
    fun observeAllForUser(userId: Long): Flow<List<AccountEntity>>

    /** Réservé à `AccountRepositoryImpl.saveAccount` : position à attribuer à un NOUVEAU compte
     * (toujours en fin de liste, voir [com.naniger.arzikina.domain.model.Account.displayOrder]) —
     * `COUNT` plutôt que `MAX(displayOrder) + 1` pour rester correct même si l'utilisateur n'a
     * jamais réordonné (valeurs par défaut `0` potentiellement dupliquées entre plusieurs comptes
     * jamais déplacés, `COUNT` reste toujours strictement croissant). */
    @Query("SELECT COUNT(*) FROM accounts WHERE userId = :userId AND deletedAt IS NULL")
    suspend fun countForUser(userId: Long): Int

    /** Réservé à `AccountRepositoryImpl.reorderAccounts` : réécrit UNIQUEMENT la position et
     * l'horodatage d'un compte déplacé — jamais [AccountEntity.version] (voir la KDoc de
     * `AccountRepositoryImpl.saveAccount` : la version reste un compteur serveur, réattribuée par
     * `applyAccountServerState` après confirmation de la synchronisation, jamais localement). */
    @Query("UPDATE accounts SET displayOrder = :displayOrder, updatedAt = :updatedAt WHERE id = :id AND userId = :userId")
    suspend fun updateDisplayOrder(id: Long, userId: Long, displayOrder: Long, updatedAt: Long)

    @Query("SELECT * FROM accounts WHERE id = :id AND userId = :userId AND deletedAt IS NULL")
    suspend fun getById(id: Long, userId: Long): AccountEntity?

    /** Réservé à `SyncEngineImpl` — voir la KDoc de `CategoryDao.getBySyncId` (même raisonnement,
     * volontairement SANS filtre `deletedAt IS NULL` ni `userId`). */
    @Query("SELECT * FROM accounts WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): AccountEntity?

    /** Réservé aux résolveurs `*SyncEnqueuer` (`TransactionSyncEnqueuer`/`LoanSyncEnqueuer`) —
     * variante de [getById] SANS le filtre `deletedAt IS NULL`. Bug réel corrigé à l'étape 19.5 :
     * une transaction/un prêt référençant ce compte peut être enfilé APRÈS que le compte lui-même
     * a déjà été soft-supprimé dans la MÊME cascade (voir `AccountRepositoryImpl.deleteAccount` —
     * l'enfilage a lieu après `database.withTransaction`, donc après le commit du
     * `softDeleteById` du compte) : son `syncId` reste valide et doit être résolu normalement, pas
     * traité comme une corruption de données ([getById] renverrait `null` à tort dans ce cas). */
    @Query("SELECT * FROM accounts WHERE id = :id AND userId = :userId")
    suspend fun getByIdIncludingDeleted(id: Long, userId: Long): AccountEntity?

    /** Réservé à `SyncEngineImpl.enqueueUnsyncedLocalData` — voir la KDoc de
     * `CategoryDao.getUnsyncedForUser` (même raisonnement ; `BackupRepositoryImpl` étant ici le
     * seul chemin de contournement connu, aucun seeder par défaut n'existe pour cette entité). */
    @Query("SELECT * FROM accounts WHERE userId = :userId AND deletedAt IS NULL AND syncId IS NULL")
    suspend fun getUnsyncedForUser(userId: Long): List<AccountEntity>

    /**
     * Réservé à `SyncEngineImpl.applyAccountServerState` — voir la KDoc de
     * `CategoryDao.getUnsyncedByNameAndType` (même raisonnement de rattachement anti-doublon,
     * appliqué ici à `DefaultAccounts` : "Espèces"/"Banque"/"Mobile Money"/"Épargne"/"Wallet" semés
     * à l'inscription sur chaque appareil). Filtre par [type] en plus de [name] par cohérence avec
     * `CategoryDao` (les noms de `DefaultAccounts` sont en pratique tous uniques, mais rien ne
     * garantit qu'un compte renommé par l'utilisateur ne collisionne pas un jour).
     */
    @Query(
        "SELECT * FROM accounts WHERE userId = :userId AND name = :name AND type = :type " +
            "AND deletedAt IS NULL AND syncId IS NULL LIMIT 1"
    )
    suspend fun getUnsyncedByNameAndType(userId: Long, name: String, type: AccountType): AccountEntity?

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
