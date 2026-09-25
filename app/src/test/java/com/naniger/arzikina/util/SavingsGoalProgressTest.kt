package com.naniger.arzikina.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavingsGoalProgressTest {

    private val franc = Money.MINOR_UNITS_PER_MAJOR.toLong()

    @Test
    fun `progression arrondie a l entier inferieur et bornee a 100`() {
        assertEquals(30, SavingsGoalProgress.progressPercent(150_000 * franc, 500_000 * franc))
        assertEquals(99, SavingsGoalProgress.progressPercent(499_999 * franc, 500_000 * franc))
        assertEquals(100, SavingsGoalProgress.progressPercent(500_000 * franc, 500_000 * franc))
        assertEquals(100, SavingsGoalProgress.progressPercent(600_000 * franc, 500_000 * franc))
    }

    @Test
    fun `progression jamais negative et cible nulle geree`() {
        assertEquals(0, SavingsGoalProgress.progressPercent(-10_000 * franc, 500_000 * franc))
        assertEquals(0, SavingsGoalProgress.progressPercent(0L, 500_000 * franc))
        assertEquals(0, SavingsGoalProgress.progressPercent(10_000 * franc, 0L))
    }

    @Test
    fun `aucun debordement pour un solde enorme`() {
        assertEquals(100, SavingsGoalProgress.progressPercent(Long.MAX_VALUE, 1L))
    }

    @Test
    fun `objectif atteint des que le montant epargne egale la cible`() {
        assertFalse(SavingsGoalProgress.isCompleted(499_999 * franc, 500_000 * franc))
        assertTrue(SavingsGoalProgress.isCompleted(500_000 * franc, 500_000 * franc))
        assertFalse(SavingsGoalProgress.isCompleted(0L, 0L))
    }

    @Test
    fun `reste a epargner jamais negatif`() {
        assertEquals(350_000 * franc, SavingsGoalProgress.remainingAmount(150_000 * franc, 500_000 * franc))
        assertEquals(0L, SavingsGoalProgress.remainingAmount(600_000 * franc, 500_000 * franc))
        assertEquals(500_000 * franc, SavingsGoalProgress.remainingAmount(-20_000 * franc, 500_000 * franc))
    }

    @Test
    fun `exemple du cahier des charges - 150 000 sur 500 000 puis transfert de 50 000`() {
        val before = SavingsGoalProgress.of(150_000 * franc, 500_000 * franc)!!
        assertEquals(30, before.percent)
        assertEquals(150_000 * franc, before.saved)
        assertEquals(350_000 * franc, before.remaining)
        assertFalse(before.isReached)

        val after = SavingsGoalProgress.of(200_000 * franc, 500_000 * franc)!!
        assertEquals(40, after.percent)
        assertEquals(300_000 * franc, after.remaining)
    }

    @Test
    fun `objectif depasse - barre plafonnee a 100 et depassement signale`() {
        val snapshot = SavingsGoalProgress.of(600_000 * franc, 500_000 * franc)!!
        assertEquals(100, snapshot.percent)
        assertTrue(snapshot.isReached)
        assertTrue(snapshot.isExceeded)
        assertEquals(100_000 * franc, snapshot.exceededBy)
        assertEquals(0L, snapshot.remaining)
    }

    @Test
    fun `solde nul ou negatif - rien d epargne`() {
        val zero = SavingsGoalProgress.of(0L, 500_000 * franc)!!
        assertEquals(0, zero.percent)
        assertEquals(0L, zero.saved)

        val negative = SavingsGoalProgress.of(-5_000 * franc, 500_000 * franc)!!
        assertEquals(0, negative.percent)
        assertEquals(0L, negative.saved)
        assertEquals(-5_000 * franc, negative.balance)
        assertFalse(negative.isExceeded)
    }

    @Test
    fun `pas de progression sans cible valide`() {
        assertNull(SavingsGoalProgress.of(150_000 * franc, null))
        assertNull(SavingsGoalProgress.of(150_000 * franc, 0L))
        assertNull(SavingsGoalProgress.of(150_000 * franc, -1L))
    }
}
