package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

/**
 * Voir `data/local/dao/AccountDao` pour le raisonnement sur le filtrage systématique par `userId`.
 *
 * Suppression DOUCE (voir `CategoryDao` pour le raisonnement complet, même principe) : toutes les
 * lectures ci-dessous filtrent `deletedAt IS NULL`, `deleteById` est remplacé par `softDeleteById`
 * — voir `PersonRepositoryImpl.deletePerson` pour le rattrapage explicite du cascade `loans` que
 * SQLite ne peut plus fournir sur un simple `UPDATE` (contrairement à un vrai `DELETE`). Seul
 * [deleteAllForUser] (restauration d'une sauvegarde) ignore ce filtre.
 */
@Dao
interface PersonDao {

    @Query("SELECT * FROM persons WHERE userId = :userId AND deletedAt IS NULL ORDER BY name ASC")
    fun observeAllForUser(userId: Long): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons WHERE id = :id AND userId = :userId AND deletedAt IS NULL")
    suspend fun getById(id: Long, userId: Long): PersonEntity?

    /** Réservé à `SyncEngineImpl` — voir la KDoc de `CategoryDao.getBySyncId` (même raisonnement,
     * volontairement SANS filtre `deletedAt IS NULL` ni `userId`). */
    @Query("SELECT * FROM persons WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): PersonEntity?

    /** Réservé à `SyncEngineImpl.enqueueUnsyncedLocalData` — voir la KDoc de
     * `CategoryDao.getUnsyncedForUser` (même raisonnement ; `BackupRepositoryImpl` étant ici le
     * seul chemin de contournement connu, aucun seeder par défaut n'existe pour cette entité). */
    @Query("SELECT * FROM persons WHERE userId = :userId AND deletedAt IS NULL AND syncId IS NULL")
    suspend fun getUnsyncedForUser(userId: Long): List<PersonEntity>

    /** Retourne l'id de la ligne insérée, ou -1 en cas de mise à jour (voir `AccountDao.upsert`). */
    @Upsert
    suspend fun upsert(person: PersonEntity): Long

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert` : toujours une
     * insertion neuve avec `id = 0L` lors d'une restauration, jamais une mise à jour). */
    @Insert
    suspend fun insertAll(persons: List<PersonEntity>): List<Long>

    /** Suppression DOUCE (voir la doc de tête) : `deletedAt`/`updatedAt` seulement, même principe
     * que `CategoryDao.softDeleteById`. Ne touche PAS `loans` — voir
     * `PersonRepositoryImpl.deletePerson`, qui supprime explicitement (suppression PHYSIQUE,
     * inchangée) les prêts/emprunts de cette personne AVANT d'appeler cette méthode : la cascade
     * SQLite `personId` ne se déclenche que sur un vrai `DELETE`, jamais sur cet `UPDATE`. */
    @Query("UPDATE persons SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND userId = :userId")
    suspend fun softDeleteById(id: Long, userId: Long, deletedAt: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de
     * [userId] — suppression PHYSIQUE assumée (voir la doc de tête), pas une suppression métier. */
    @Query("DELETE FROM persons WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
