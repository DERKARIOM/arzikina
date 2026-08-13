package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

/** Voir `data/local/dao/AccountDao` pour le raisonnement sur le filtrage systématique par `userId`. */
@Dao
interface PersonDao {

    @Query("SELECT * FROM persons WHERE userId = :userId ORDER BY name ASC")
    fun observeAllForUser(userId: Long): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons WHERE id = :id AND userId = :userId")
    suspend fun getById(id: Long, userId: Long): PersonEntity?

    /** Retourne l'id de la ligne insérée, ou -1 en cas de mise à jour (voir `AccountDao.upsert`). */
    @Upsert
    suspend fun upsert(person: PersonEntity): Long

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert` : toujours une
     * insertion neuve avec `id = 0L` lors d'une restauration, jamais une mise à jour). */
    @Insert
    suspend fun insertAll(persons: List<PersonEntity>): List<Long>

    /** Supprime aussi, en cascade SQLite, tous les prêts/emprunts de cette personne (voir
     * `LoanEntity`) — et donc leurs remboursements. Les transactions liées ne sont PAS supprimées
     * automatiquement (pas de `FOREIGN KEY` vers `transactions`, voir `LoanPaymentEntity`) : c'est
     * la responsabilité de `PersonRepositoryImpl.deletePerson`, qui doit les nettoyer AVANT
     * d'appeler cette méthode. */
    @Query("DELETE FROM persons WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: Long, userId: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de [userId]. */
    @Query("DELETE FROM persons WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
