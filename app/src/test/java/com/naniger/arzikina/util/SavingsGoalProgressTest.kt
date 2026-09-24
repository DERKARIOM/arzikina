package com.naniger.arzikina.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SavingsGoalProgressTest {

    private val today = LocalDate.of(2026, 9, 24)

    /** 1 F CFA = 100 unités mineures. */
    private fun f(major: Long) = major * 100

    @Test
    fun `progression arrondie a l entier inferieur et bornee a 100`() {
        assertEquals(30, SavingsGoalProgress.progressPercent(f(45_000), f(150_000)))
        assertEquals(100, SavingsGoalProgress.progressPercent(f(250_000), f(200_000)))
        assertEquals(0, SavingsGoalProgress.progressPercent(f(10_000), 0L))
    }

    @Test
    fun `objectif atteint des que le montant epargne egale la cible`() {
        assertTrue(SavingsGoalProgress.isCompleted(f(200_000), f(200_000)))
        assertFalse(SavingsGoalProgress.isCompleted(f(199_999), f(200_000)))
        assertFalse(SavingsGoalProgress.isCompleted(0L, 0L))
    }

    @Test
    fun `reste a epargner jamais negatif`() {
        assertEquals(f(105_000), SavingsGoalProgress.remainingAmount(f(45_000), f(150_000)))
        assertEquals(0L, SavingsGoalProgress.remainingAmount(f(250_000), f(200_000)))
    }

    @Test
    fun `echeance - aucune, atteinte, aujourd hui, depassee`() {
        assertEquals(SavingsGoalDeadline.None, SavingsGoalProgress.deadlineOf(null, false, today))
        assertEquals(SavingsGoalDeadline.Reached, SavingsGoalProgress.deadlineOf(today.minusDays(3), true, today))
        assertEquals(SavingsGoalDeadline.Today(today), SavingsGoalProgress.deadlineOf(today, false, today))
        assertEquals(
            SavingsGoalDeadline.Overdue(today.minusDays(1)),
            SavingsGoalProgress.deadlineOf(today.minusDays(1), false, today)
        )
    }

    @Test
    fun `echeance proche signalee jusqu a 30 jours inclus`() {
        assertEquals(
            SavingsGoalDeadline.Remaining(today.plusDays(7), 7, isSoon = true),
            SavingsGoalProgress.deadlineOf(today.plusDays(7), false, today)
        )
        assertEquals(
            SavingsGoalDeadline.Remaining(today.plusDays(30), 30, isSoon = true),
            SavingsGoalProgress.deadlineOf(today.plusDays(30), false, today)
        )
        assertEquals(
            SavingsGoalDeadline.Remaining(today.plusDays(31), 31, isSoon = false),
            SavingsGoalProgress.deadlineOf(today.plusDays(31), false, today)
        )
    }

    @Test
    fun `suggestion mensuelle sur les mois complets restants`() {
        // 24/09/2026 -> 16/05/2027 : 7 mois complets, 105 000 F restants -> 15 000 F par mois.
        assertEquals(
            SavingsSuggestion.PerMonth(f(15_000)),
            SavingsGoalProgress.suggestionOf(f(45_000), f(150_000), LocalDate.of(2027, 5, 16), today)
        )
    }

    @Test
    fun `suggestion arrondie au franc superieur`() {
        // 100 F sur 3 mois = 33,33 F -> 34 F.
        assertEquals(
            SavingsSuggestion.PerMonth(f(34)),
            SavingsGoalProgress.suggestionOf(0L, f(100), today.plusMonths(3), today)
        )
    }

    @Test
    fun `moins d un mois avant l echeance - tout le reste d ici l echeance`() {
        assertEquals(
            SavingsSuggestion.BeforeDeadline(f(40_000)),
            SavingsGoalProgress.suggestionOf(f(80_000), f(120_000), today.plusDays(7), today)
        )
    }

    @Test
    fun `aucune suggestion sans echeance, objectif atteint ou echeance depassee`() {
        assertNull(SavingsGoalProgress.suggestionOf(f(10), f(100), null, today))
        assertNull(SavingsGoalProgress.suggestionOf(f(100), f(100), today.plusMonths(2), today))
        assertNull(SavingsGoalProgress.suggestionOf(f(10), f(100), today.minusDays(1), today))
    }
}
