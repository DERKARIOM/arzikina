package com.arzikina.ne.domain.repository

import com.arzikina.ne.domain.model.SavingsGoal
import kotlinx.coroutines.flow.Flow

/**
 * Contrat d'accès aux données des objectifs d'épargne. Voir [AccountRepository]
 * pour le raisonnement derrière cette séparation (indépendance vis-à-vis de Room).
 */
interface SavingsGoalRepository {

    fun observeSavingsGoals(): Flow<List<SavingsGoal>>

    suspend fun getSavingsGoal(id: Long): SavingsGoal?

    /** Crée l'objectif si [SavingsGoal.id] vaut 0, le met à jour sinon. */
    suspend fun saveSavingsGoal(goal: SavingsGoal)

    /**
     * Ajoute [amountDelta] (en unité mineure, peut être négatif pour un
     * retrait) au montant déjà épargné, en une seule opération atomique
     * (évite une lecture-puis-écriture concurrente).
     */
    suspend fun addContribution(id: Long, amountDelta: Long)

    suspend fun deleteSavingsGoal(id: Long)
}
