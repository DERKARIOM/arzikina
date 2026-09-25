package com.naniger.arzikina.data.repository

import com.naniger.arzikina.data.local.entity.SavingsGoalEntity
import com.naniger.arzikina.domain.model.AccountIcon
import com.naniger.arzikina.domain.model.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Règles de conversion d'un ancien objectif (`savings_goals`) en compte `SAVINGS_GOAL` — mêmes
 * règles que `database/migrations/006_savings_goal_accounts.sql` côté serveur. */
class LegacySavingsGoalConversionTest {

    private val goal = SavingsGoalEntity(
        id = 4L,
        userId = 1L,
        name = "Acheter une moto",
        targetAmount = 50_000_000L,
        currentAmount = 15_000_000L,
        currencyCode = "XOF",
        deadline = 1_900_000_000_000L,
        createdAt = 1_000L,
        syncId = "3f1c0e7a-0000-4000-8000-000000000001"
    )

    @Test
    fun `ancien objectif converti en compte objectif avec le meme syncId`() {
        val account = LegacySavingsGoalConversion.toAccountEntity(goal, userId = 1L, syncId = goal.syncId!!, displayOrder = 5L, now = 2_000L)

        assertEquals(0L, account.id)
        assertEquals("3f1c0e7a-0000-4000-8000-000000000001", account.syncId)
        assertEquals(AccountType.SAVINGS_GOAL, account.type)
        assertEquals(AccountIcon.SAVINGS, account.icon)
        assertEquals("Acheter une moto", account.name)
        assertEquals("XOF", account.currencyCode)
        // Ancien montant épargné → solde initial ; cible conservée.
        assertEquals(15_000_000L, account.initialBalanceMinor)
        assertEquals(50_000_000L, account.savingsTargetAmount)
        assertEquals(1_000L, account.createdAt)
        assertEquals(2_000L, account.updatedAt)
        assertEquals(5L, account.displayOrder)
        // Jamais compté dans le solde total jusqu'ici : exclu par défaut pour ne pas le fausser.
        assertTrue(account.isExcludedFromStatistics)
    }

    @Test
    fun `montants incoherents normalises - cible au moins 1, solde jamais negatif`() {
        val broken = goal.copy(targetAmount = 0L, currentAmount = -300L)
        val account = LegacySavingsGoalConversion.toAccountEntity(broken, userId = 1L, syncId = "x", displayOrder = 0L, now = 0L)

        assertEquals(1L, account.savingsTargetAmount)
        assertEquals(0L, account.initialBalanceMinor)
    }
}
