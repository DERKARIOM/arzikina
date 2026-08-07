package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

/** Voir `data/local/dao/AccountDao` pour le raisonnement sur le filtrage systématique par `userId`. */
@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeAllForUser(userId: Long): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id AND userId = :userId")
    suspend fun getById(id: Long, userId: Long): BudgetEntity?

    /**
     * Filtre `userId` ajouté par cohérence avec le reste de ce DAO (défense en
     * profondeur), même si `categoryId` référence déjà une catégorie
     * appartenant à un seul utilisateur (voir `BudgetEntity`) : aucune requête
     * de ce DAO ne doit dépendre implicitement de l'unicité d'un id étranger
     * pour rester isolée par utilisateur.
     */
    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId AND userId = :userId")
    suspend fun getByCategoryId(categoryId: Long, userId: Long): BudgetEntity?

    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    /** Utilisé uniquement par la restauration d'une sauvegarde (remplacement complet). */
    @Upsert
    suspend fun insertAll(budgets: List<BudgetEntity>)

    @Query("DELETE FROM budgets WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: Long, userId: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de [userId]. */
    @Query("DELETE FROM budgets WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
