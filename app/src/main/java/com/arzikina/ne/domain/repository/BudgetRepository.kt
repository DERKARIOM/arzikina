package com.arzikina.ne.domain.repository

import com.arzikina.ne.domain.model.Budget
import kotlinx.coroutines.flow.Flow

/**
 * Contrat d'accès aux données des budgets. Voir [AccountRepository] pour le
 * raisonnement derrière cette séparation (indépendance vis-à-vis de Room).
 */
interface BudgetRepository {

    fun observeBudgets(): Flow<List<Budget>>

    suspend fun getBudget(id: Long): Budget?

    /** Utilisé par le formulaire pour n'autoriser qu'un budget actif par catégorie. */
    suspend fun getBudgetForCategory(categoryId: Long): Budget?

    /** Crée le budget si [Budget.id] vaut 0, le met à jour sinon. */
    suspend fun saveBudget(budget: Budget)

    suspend fun deleteBudget(id: Long)
}
