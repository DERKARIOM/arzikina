package com.arzikina.ne.util

import com.arzikina.ne.domain.model.FinancialPlanItem
import com.arzikina.ne.domain.model.PlanItemStatus

/**
 * Calculs centralisés d'une [com.arzikina.ne.domain.model.FinancialPlan] à partir de ses
 * [FinancialPlanItem] (voir cahier des charges "Planification financière", sections 3/15) —
 * réutilisés par [com.arzikina.ne.presentation.utilities.financialplan.FinancialPlansViewModel]
 * (liste) et, à une étape ultérieure, par l'écran de détail. Même principe que [BudgetProgress]
 * ("évite absolument le code dupliqué").
 *
 * IMPORTANT : ces calculs sont PUREMENT AFFICHÉS, jamais stockés en base (voir la doc de
 * [com.arzikina.ne.domain.model.FinancialPlan]) — recalculés à chaque émission du flux d'items.
 *
 * [PlanItemStatus.CANCELLED] est exclu de [calculateTotalPlanned] : une dépense annulée ne doit
 * plus peser sur le "prévu" ni sur le "reste" (voir cahier des charges, section 10 — elle reste
 * seulement visible dans l'historique de la planification).
 */
object FinancialPlanProgress {

    /** Somme des [FinancialPlanItem.amount] non annulés, en unité mineure. */
    fun calculateTotalPlanned(items: List<FinancialPlanItem>): Long =
        items.filter { it.status != PlanItemStatus.CANCELLED }.sumOf { it.amount }

    /** Peut être NÉGATIF en cas de dépassement (voir [calculateOverBudget]) — volontairement non
     * borné à 0, contrairement à [calculateProgress], pour ne pas masquer un dépassement réel. */
    fun calculateRemainingAmount(availableAmount: Long, totalPlanned: Long): Long =
        availableAmount - totalPlanned

    /**
     * Progression 0..100, BORNÉE pour l'affichage d'une barre de progression (voir
     * `computeLoanProgressPercent` pour le même principe). `0` si [availableAmount] est nul ou
     * négatif (donnée invalide) sauf s'il existe déjà un [totalPlanned] positif, auquel cas `100`
     * (déjà entièrement dépassé).
     */
    fun calculateProgress(availableAmount: Long, totalPlanned: Long): Int {
        if (availableAmount <= 0L) return if (totalPlanned > 0L) 100 else 0
        return ((totalPlanned * 100) / availableAmount).coerceIn(0L, 100L).toInt()
    }

    /** `true` dès que le total prévu dépasse le montant disponible (voir cahier des charges,
     * section 15 : alerte sobre de dépassement). */
    fun calculateOverBudget(availableAmount: Long, totalPlanned: Long): Boolean =
        totalPlanned > availableAmount
}
