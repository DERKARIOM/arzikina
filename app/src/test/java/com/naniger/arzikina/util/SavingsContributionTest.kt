package com.naniger.arzikina.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavingsContributionTest {

    /** 1 F CFA = 100 unités mineures. */
    private fun f(major: Long) = major * 100

    private fun preview(current: Long, target: Long, type: ContributionType, amount: Long) =
        SavingsContribution.preview(f(current), f(target), type, amount)

    @Test
    fun `versement - nouveau montant, delta positif et progression`() {
        val result = preview(45_000, 150_000, ContributionType.DEPOSIT, f(10_000))
        assertEquals(ContributionPreview.Valid(f(10_000), f(55_000), 36, reachesTarget = false), result)
    }

    @Test
    fun `retrait - delta negatif`() {
        val result = preview(45_000, 150_000, ContributionType.WITHDRAWAL, f(5_000))
        assertEquals(ContributionPreview.Valid(-f(5_000), f(40_000), 26, reachesTarget = false), result)
    }

    @Test
    fun `retrait de tout le montant epargne autorise, au-dela bloque`() {
        val all = preview(45_000, 150_000, ContributionType.WITHDRAWAL, f(45_000))
        assertEquals(0L, (all as ContributionPreview.Valid).newAmount)

        val tooMuch = preview(45_000, 150_000, ContributionType.WITHDRAWAL, f(45_001))
        assertEquals(ContributionPreview.ExceedsSaved(f(45_000)), tooMuch)
    }

    @Test
    fun `montant nul, negatif ou demesure refuse`() {
        assertEquals(ContributionPreview.InvalidAmount, preview(0, 1_000, ContributionType.DEPOSIT, 0L))
        assertEquals(ContributionPreview.InvalidAmount, preview(0, 1_000, ContributionType.DEPOSIT, -1L))
        assertEquals(
            ContributionPreview.InvalidAmount,
            preview(0, 1_000, ContributionType.DEPOSIT, SavingsContribution.MAX_AMOUNT + 1)
        )
    }

    @Test
    fun `celebration seulement quand ce versement franchit la cible`() {
        val exact = preview(140_000, 150_000, ContributionType.DEPOSIT, f(10_000)) as ContributionPreview.Valid
        assertTrue(exact.reachesTarget)
        assertEquals(100, exact.newProgressPercent)

        val beyond = preview(140_000, 150_000, ContributionType.DEPOSIT, f(25_000)) as ContributionPreview.Valid
        assertTrue(beyond.reachesTarget)
        assertEquals(f(165_000), beyond.newAmount)

        val alreadyDone = preview(150_000, 150_000, ContributionType.DEPOSIT, f(5_000)) as ContributionPreview.Valid
        assertFalse(alreadyDone.reachesTarget)

        val notYet = preview(100_000, 150_000, ContributionType.DEPOSIT, f(49_999)) as ContributionPreview.Valid
        assertFalse(notYet.reachesTarget)
    }

    @Test
    fun `raccourcis adaptes a la devise`() {
        assertEquals(listOf(f(5_000), f(10_000), f(25_000)), SavingsContribution.quickAmounts("XOF"))
        assertEquals(listOf(f(10), f(20), f(50)), SavingsContribution.quickAmounts("EUR"))
        assertEquals(listOf(f(50), f(100), f(200)), SavingsContribution.quickAmounts("GHS"))
        assertEquals(SavingsContribution.quickAmounts("XOF"), SavingsContribution.quickAmounts("JPY"))
    }
}
