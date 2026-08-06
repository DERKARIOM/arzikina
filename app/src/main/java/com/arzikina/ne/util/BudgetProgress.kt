package com.arzikina.ne.util

import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Budget
import com.arzikina.ne.domain.model.BudgetPeriod
import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.model.TransactionType
import java.time.LocalDate

/**
 * Calcul du montant dépensé et de la progression d'un [Budget] sur sa période
 * en cours (semaine ou mois civil, voir [Budget.period]).
 *
 * Centralisé ici plutôt que dupliqué : cette même règle est utilisée par
 * [com.arzikina.ne.presentation.budget.BudgetViewModel] (écran Budget) et par
 * [com.arzikina.ne.presentation.dashboard.DashboardViewModel] (aperçu Budget
 * du tableau de bord) — voir instructions projet ("évite absolument le code
 * dupliqué").
 */
object BudgetProgress {

    data class Result(val spentMinor: Long, val progress: Float)

    /**
     * [spentMinor] ne compte que les dépenses dans la devise du budget (pas de
     * conversion de change). [progress] peut dépasser `1f` en cas de
     * dépassement du plafond ; il vaut `0f` si [Budget.limitAmount] est nul.
     */
    fun compute(
        budget: Budget,
        transactions: List<Transaction>,
        accountsById: Map<Long, Account>,
        today: LocalDate = LocalDate.now()
    ): Result {
        val spent = transactions
            .asSequence()
            .filter { it.categoryId == budget.categoryId && it.type == TransactionType.EXPENSE }
            .filter { accountsById[it.accountId]?.currencyCode == budget.currencyCode }
            .filter { isInCurrentPeriod(it.date, budget.period, today) }
            .sumOf { it.amount }
        val progress = if (budget.limitAmount > 0L) spent.toFloat() / budget.limitAmount.toFloat() else 0f
        return Result(spent, progress)
    }

    private fun isInCurrentPeriod(dateMillis: Long, period: BudgetPeriod, today: LocalDate): Boolean =
        when (period) {
            BudgetPeriod.MONTHLY -> DatePeriods.isInCurrentMonth(dateMillis, today)
            BudgetPeriod.WEEKLY -> DatePeriods.isInCurrentWeek(dateMillis, today)
        }
}
