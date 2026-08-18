package com.arzikina.ne.domain.repository

import com.arzikina.ne.domain.model.FinancialPlan
import com.arzikina.ne.domain.model.FinancialPlanItem
import kotlinx.coroutines.flow.Flow

/**
 * Contrat d'accès aux données de la fonctionnalité "Planification" (voir [FinancialPlan]/
 * [FinancialPlanItem] pour le principe fondamental "prévision ≠ transaction réelle").
 *
 * [convertItemToTransaction] est la SEULE écriture de ce repository qui crée une
 * [com.arzikina.ne.domain.model.Transaction] réelle (voir sa doc, Étape 6 "Convertir une dépense
 * prévue en transaction") — UNIQUEMENT sur action explicite de l'utilisateur. Toutes les autres
 * écritures ([savePlan], [saveItem], [deletePlan], [deleteItem]) restent purement prévisionnelles,
 * exactement comme avant : une planification et ses dépenses prévues ne deviennent jamais des
 * transactions réelles "automatiquement".
 */
interface FinancialPlanRepository {

    /** Flux réactif de toutes les planifications de l'utilisateur courant. */
    fun observePlans(): Flow<List<FinancialPlan>>

    suspend fun getPlan(id: Long): FinancialPlan?

    /** Flux réactif des dépenses prévues d'UNE planification. */
    fun observeItems(planId: Long): Flow<List<FinancialPlanItem>>

    /** Flux réactif de TOUTES les dépenses prévues de l'utilisateur, toutes planifications
     * confondues — utilisé par l'écran liste pour calculer le total prévu/reste de chaque
     * planification (voir [com.arzikina.ne.util.FinancialPlanProgress]) sans une requête par
     * planification. */
    fun observeAllItems(): Flow<List<FinancialPlanItem>>

    suspend fun getItem(id: Long): FinancialPlanItem?

    /** Si [FinancialPlan.id] vaut 0 : crée la planification. Sinon : met à jour ses champs.
     * Retourne l'id définitif. */
    suspend fun savePlan(plan: FinancialPlan): Long

    /** Supprime la planification ET ses dépenses prévues (cascade SQLite, voir
     * `data/local/entity/FinancialPlanItemEntity`). N'affecte AUCUNE transaction réelle, même
     * pour les dépenses déjà converties (voir [FinancialPlanItem.transactionId]) — les
     * transactions déjà créées restent dans l'historique de l'utilisateur, comme n'importe quelle
     * transaction normale. */
    suspend fun deletePlan(id: Long)

    /** Si [FinancialPlanItem.id] vaut 0 : crée la dépense prévue. Sinon : met à jour ses champs.
     * Retourne l'id définitif. */
    suspend fun saveItem(item: FinancialPlanItem): Long

    suspend fun deleteItem(id: Long)

    /**
     * Convertit une dépense prévue en transaction réelle (voir cahier des charges "Planification
     * financière", section 12) : crée une [com.arzikina.ne.domain.model.Transaction] de type
     * DÉPENSE et met à jour [FinancialPlanItem.transactionId]/[FinancialPlanItem.actualAmount]/
     * [FinancialPlanItem.status] (toujours [com.arzikina.ne.domain.model.PlanItemStatus.DONE]
     * après conversion) EN UNE SEULE écriture atomique — même principe que
     * [LoanRepository.recordPayment] (voir sa doc). Échoue (exception) si [itemId] ne correspond à
     * aucune dépense prévue, si elle a déjà été convertie ([FinancialPlanItem.transactionId] déjà
     * renseigné) : on ne convertit jamais deux fois la même dépense prévue, ou si elle est
     * [com.arzikina.ne.domain.model.PlanItemStatus.CANCELLED] (Étape 11) : une dépense annulée n'a
     * plus lieu d'être honorée.
     *
     * [accountId]/[categoryId] : choisis par l'utilisateur sur l'écran de conversion (pré-remplis
     * depuis la dépense prévue quand c'est possible, mais jamais imposés — voir
     * `FinancialPlanItemConvertViewModel`). [actualAmount] : montant RÉELLEMENT dépensé, peut
     * différer du montant prévu ([FinancialPlanItem.amount]).
     *
     * @return l'id de la transaction créée.
     */
    suspend fun convertItemToTransaction(
        itemId: Long,
        accountId: Long,
        categoryId: Long,
        actualAmount: Long,
        date: Long,
        description: String
    ): Long
}
