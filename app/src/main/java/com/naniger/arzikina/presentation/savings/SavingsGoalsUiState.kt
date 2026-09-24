package com.naniger.arzikina.presentation.savings

import androidx.annotation.StringRes
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.SavingsGoal
import com.naniger.arzikina.util.DatePeriods
import com.naniger.arzikina.util.SavingsGoalDeadline
import com.naniger.arzikina.util.SavingsGoalProgress
import com.naniger.arzikina.util.SavingsSuggestion
import java.time.LocalDate

/** Un objectif avec sa progression déjà calculée (voir [SavingsGoalProgress]), prêt à afficher. */
data class SavingsGoalUiItem(
    val goal: SavingsGoal,
    val progressPercent: Int,
    val isCompleted: Boolean,
    val deadline: SavingsGoalDeadline,
    val suggestion: SavingsSuggestion?
)

/**
 * Carte de synthèse en tête de liste. Les montants additionnent UNIQUEMENT les objectifs dans la
 * devise principale de l'utilisateur ([currencyCode]) : additionner des F CFA et des nairas
 * donnerait un total faux. Les compteurs, eux, portent sur tous les objectifs.
 */
data class SavingsGoalsSummary(
    val currencyCode: String,
    val totalSaved: Long,
    val totalTarget: Long,
    val progressPercent: Int,
    val goalCount: Int,
    val completedCount: Int
)

/** Lignes de la liste (voir [SavingsGoalsAdapter]) : synthèse, titres de section, objectifs. */
sealed interface SavingsGoalsRow {
    data class Summary(val summary: SavingsGoalsSummary) : SavingsGoalsRow
    data class Section(@StringRes val titleRes: Int) : SavingsGoalsRow
    data class Goal(val item: SavingsGoalUiItem) : SavingsGoalsRow
}

/**
 * Ordre d'affichage (voir la maquette) : synthèse, puis « En cours » triés par échéance la plus
 * proche (sans échéance en dernier), puis « Atteints ». Liste vide = état vide de l'écran.
 */
fun buildSavingsGoalsRows(
    goals: List<SavingsGoal>,
    mainCurrencyCode: String,
    today: LocalDate
): List<SavingsGoalsRow> {
    if (goals.isEmpty()) return emptyList()

    val items = goals.map { it.toUiItem(today) }
    val (completed, active) = items.partition { it.isCompleted }

    return buildList {
        add(SavingsGoalsRow.Summary(summaryOf(goals, mainCurrencyCode, completed.size)))
        if (active.isNotEmpty()) {
            add(SavingsGoalsRow.Section(R.string.savings_goals_section_active))
            active.sortedWith(compareBy<SavingsGoalUiItem, Long?>(nullsLast()) { it.goal.deadline }.thenBy { it.goal.name })
                .forEach { add(SavingsGoalsRow.Goal(it)) }
        }
        if (completed.isNotEmpty()) {
            add(SavingsGoalsRow.Section(R.string.savings_goals_section_completed))
            completed.sortedBy { it.goal.name }.forEach { add(SavingsGoalsRow.Goal(it)) }
        }
    }
}

private fun SavingsGoal.toUiItem(today: LocalDate): SavingsGoalUiItem {
    val completed = SavingsGoalProgress.isCompleted(currentAmount, targetAmount)
    val deadlineDate = deadline?.let(DatePeriods::toLocalDate)
    return SavingsGoalUiItem(
        goal = this,
        progressPercent = SavingsGoalProgress.progressPercent(currentAmount, targetAmount),
        isCompleted = completed,
        deadline = SavingsGoalProgress.deadlineOf(deadlineDate, completed, today),
        suggestion = SavingsGoalProgress.suggestionOf(currentAmount, targetAmount, deadlineDate, today)
    )
}

private fun summaryOf(goals: List<SavingsGoal>, mainCurrencyCode: String, completedCount: Int): SavingsGoalsSummary {
    val inMainCurrency = goals.filter { it.currencyCode == mainCurrencyCode }
    val totalSaved = inMainCurrency.sumOf { it.currentAmount }
    val totalTarget = inMainCurrency.sumOf { it.targetAmount }
    return SavingsGoalsSummary(
        currencyCode = mainCurrencyCode,
        totalSaved = totalSaved,
        totalTarget = totalTarget,
        progressPercent = SavingsGoalProgress.progressPercent(totalSaved, totalTarget),
        goalCount = goals.size,
        completedCount = completedCount
    )
}
