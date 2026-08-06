package com.arzikina.ne.domain.repository

import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

/**
 * Contrat d'accès aux données des catégories. Voir [AccountRepository] pour
 * le raisonnement derrière cette séparation (indépendance vis-à-vis de Room).
 */
interface CategoryRepository {

    fun observeCategories(): Flow<List<Category>>

    /** Utilisé par le formulaire de transaction pour ne proposer que les catégories pertinentes. */
    fun observeCategoriesByType(type: TransactionType): Flow<List<Category>>

    suspend fun getCategory(id: Long): Category?

    /** Crée la catégorie si [Category.id] vaut 0, la met à jour sinon. */
    suspend fun saveCategory(category: Category)

    suspend fun deleteCategory(id: Long)
}
