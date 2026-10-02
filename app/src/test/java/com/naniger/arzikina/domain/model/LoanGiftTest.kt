package com.naniger.arzikina.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Règles métier pures de « Transformer en cadeau » (voir `LoanGift.kt`). */
class LoanGiftTest {

    private val day = 24L * 60 * 60 * 1000
    private val start = 1_780_000_000_000L
    private val due = start + 30 * day
    private val now = start + 10 * day

    private fun loan(type: LoanType = LoanType.LENT, amount: Long = 100_000L, repaid: Long = 0L, gifted: Long = 0L) = Loan(
        id = 1L, personId = 1L, accountId = 1L, type = type,
        amount = amount, amountRepaid = repaid, remainingAmount = amount - repaid - gifted,
        startDate = start, dueDate = due, reason = LoanReason.OTHER, repaymentMode = RepaymentMode.SINGLE,
        status = LoanStatus.ONGOING, createdAt = start, updatedAt = start, transactionId = 10L,
        giftedAmount = gifted, giftTransactionId = if (gifted > 0) 10L else null
    )

    @Test
    fun `pret non rembourse - transformable, cadeau = montant total, decaissement reclasse sur place`() {
        val plan = loan().planGiftConversion(now)
        assertEquals(LoanGiftPlan(giftAmount = 100_000L, disbursementAmountAfter = 0L, reusesDisbursementTransaction = true), plan)
    }

    @Test
    fun `pret partiellement rembourse - cadeau = reste uniquement`() {
        val plan = loan(repaid = 40_000L).planGiftConversion(now)
        assertEquals(60_000L, plan.giftAmount)
        assertEquals(40_000L, plan.disbursementAmountAfter)
        assertFalse(plan.reusesDisbursementTransaction)
    }

    @Test
    fun `emprunt partiellement rembourse - meme regle`() {
        val plan = loan(type = LoanType.BORROWED, repaid = 40_000L).planGiftConversion(now)
        assertEquals(60_000L, plan.giftAmount)
        assertEquals(40_000L, plan.disbursementAmountAfter)
    }

    @Test
    fun `somme decaissement + cadeau = montant d origine - solde inchange`() {
        listOf(0L, 1L, 40_000L, 99_999L).forEach { repaid ->
            val plan = loan(repaid = repaid).planGiftConversion(now)
            assertEquals(100_000L, plan.disbursementAmountAfter + plan.giftAmount)
            assertEquals(repaid, plan.disbursementAmountAfter)
        }
    }

    @Test
    fun `pret ou emprunt entierement rembourse - action impossible`() {
        listOf(LoanType.LENT, LoanType.BORROWED).forEach { type ->
            val repaid = loan(type = type, repaid = 100_000L)
            assertFalse(repaid.canConvertToGift(now))
            assertThrows(LoanGiftException.NotConvertible::class.java) { repaid.planGiftConversion(now) }
        }
    }

    @Test
    fun `deja transforme - action impossible`() {
        val gifted = loan(repaid = 40_000L, gifted = 60_000L)
        assertFalse(gifted.canConvertToGift(now))
        assertThrows(LoanGiftException.NotConvertible::class.java) { gifted.planGiftConversion(now) }
    }

    @Test
    fun `en retard et a venir - transformables`() {
        assertTrue(loan().canConvertToGift(due + 5 * day))
        assertTrue(loan().canConvertToGift(start - day))
    }

    @Test
    fun `reste denormalise incoherent - les montants sources font foi`() {
        // remainingAmount périmé à 0 alors que rien n'est remboursé : la règle ne s'y fie pas.
        val stale = loan().copy(remainingAmount = 0L)
        assertEquals(100_000L, stale.planGiftConversion(now).giftAmount)
    }

    @Test
    fun `categorie et type du cadeau selon le sens`() {
        assertEquals(SystemCategoryKey.GIFTS, LoanType.LENT.giftCategoryKey)
        assertEquals(TransactionType.EXPENSE, LoanType.LENT.giftTransactionType)
        assertEquals(SystemCategoryKey.GIFTS_RECEIVED, LoanType.BORROWED.giftCategoryKey)
        assertEquals(TransactionType.INCOME, LoanType.BORROWED.giftTransactionType)
    }
}
