package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.TransactionTemplateEntity
import kotlinx.coroutines.flow.Flow

/** Voir `data/local/dao/AccountDao` pour le raisonnement sur le filtrage systématique par
 * `userId`. Pas de requête de recherche/filtre dédiée : même raisonnement que `ReceiptDao` — la
 * recherche et le filtre par catégorie (cahier des charges section 8) filtrent en mémoire la liste
 * déjà chargée de [observeAllForUser] (voir `MarketplaceViewModel`), un volume de modèles
 * personnel reste modeste. */
@Dao
interface TransactionTemplateDao {

    /** Favoris en tête (cahier des charges section 7), puis ordre alphabétique — jamais
     * recalculé côté appelant. */
    @Query("SELECT * FROM transaction_templates WHERE userId = :userId ORDER BY isFavorite DESC, name ASC")
    fun observeAllForUser(userId: Long): Flow<List<TransactionTemplateEntity>>

    @Query("SELECT * FROM transaction_templates WHERE id = :id AND userId = :userId")
    suspend fun getById(id: Long, userId: Long): TransactionTemplateEntity?

    /** Retourne l'id de la ligne insérée, ou -1 en cas de mise à jour (voir `AccountDao.upsert`). */
    @Upsert
    suspend fun upsert(template: TransactionTemplateEntity): Long

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert` : toujours une
     * insertion neuve avec `id = 0L` lors d'une restauration, jamais une mise à jour). */
    @Insert
    suspend fun insertAll(templates: List<TransactionTemplateEntity>): List<Long>

    @Query("DELETE FROM transaction_templates WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: Long, userId: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de
     * [userId] (voir `AccountDao.deleteAllForUser`). */
    @Query("DELETE FROM transaction_templates WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
