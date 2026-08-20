package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.ReceiptEntity
import kotlinx.coroutines.flow.Flow

/** Voir `data/local/dao/AccountDao` pour le raisonnement sur le filtrage systématique par `userId`.
 * Pas de requête de recherche dédiée : la recherche (cahier des charges section 11) filtre en
 * mémoire le résultat déjà chargé de [observeAllForUser] (voir `ReceiptsViewModel`) — un volume de
 * reçus personnel reste modeste, inutile d'ajouter une requête `LIKE` avec ses pièges d'échappement
 * de caractères spéciaux pour ce cas. */
@Dao
interface ReceiptDao {

    /** Tri du plus récent au plus ancien (cahier des charges section 5) — même ordre que
     * `receivedAt`, jamais recalculé côté appelant. */
    @Query("SELECT * FROM receipts WHERE userId = :userId ORDER BY receivedAt DESC")
    fun observeAllForUser(userId: Long): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE id = :id AND userId = :userId")
    suspend fun getById(id: Long, userId: Long): ReceiptEntity?

    /** Retourne l'id de la ligne insérée, ou -1 en cas de mise à jour (voir `AccountDao.upsert`). */
    @Upsert
    suspend fun upsert(receipt: ReceiptEntity): Long

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert` : toujours une
     * insertion neuve avec `id = 0L` lors d'une restauration, jamais une mise à jour). */
    @Insert
    suspend fun insertAll(receipts: List<ReceiptEntity>): List<Long>

    /** Supprime uniquement la ligne Room : la suppression du fichier physique correspondant est de
     * la responsabilité de `ReceiptRepositoryImpl` (voir sa doc), jamais de ce DAO. */
    @Query("DELETE FROM receipts WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: Long, userId: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de
     * [userId] (voir `AccountDao.deleteAllForUser`). */
    @Query("DELETE FROM receipts WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
