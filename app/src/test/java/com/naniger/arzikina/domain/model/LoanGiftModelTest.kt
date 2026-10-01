package com.naniger.arzikina.domain.model

import com.naniger.arzikina.data.backup.remapIds
import com.naniger.arzikina.data.backup.toDto
import com.naniger.arzikina.data.backup.toEntity
import com.naniger.arzikina.data.mapper.toDomain
import com.naniger.arzikina.data.mapper.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * « Transformer un prêt/emprunt en cadeau », étape 1 (modèle de données) : statut GIFTED, champs
 * `giftedAmount`/`giftTransactionId`/`giftedAt` et leur conservation à travers les mappers Room et
 * sauvegarde. La logique de transformation elle-même est testée à l'étape 2.
 */
class LoanGiftModelTest {

    private val day = 24L * 60 * 60 * 1000
    private val start = 1_780_000_000_000L
    private val due = start + 30 * day
    private val now = start + 10 * day

    private fun loan(
        amount: Long = 100_000L,
        repaid: Long = 0L,
        gifted: Long = 0L,
        giftTransactionId: Long? = null,
        giftedAt: Long? = null
    ) = Loan(
        id = 7L,
        personId = 1L,
        accountId = 2L,
        type = LoanType.LENT,
        amount = amount,
        amountRepaid = repaid,
        remainingAmount = amount - repaid - gifted,
        startDate = start,
        dueDate = due,
        reason = LoanReason.OTHER,
        repaymentMode = RepaymentMode.SINGLE,
        status = LoanStatus.ONGOING,
        createdAt = start,
        updatedAt = start,
        transactionId = 10L,
        giftedAmount = gifted,
        giftTransactionId = giftTransactionId,
        giftedAt = giftedAt
    )

    @Test
    fun `non transforme - statuts historiques inchanges`() {
        assertEquals(LoanStatus.ONGOING, computeLoanStatus(100_000L, 0L, start, due, now))
        assertEquals(LoanStatus.REPAID, computeLoanStatus(100_000L, 100_000L, start, due, now))
        assertEquals(LoanStatus.OVERDUE, computeLoanStatus(100_000L, 40_000L, start, due, due + 2 * day))
        assertEquals(LoanStatus.UPCOMING, computeLoanStatus(100_000L, 0L, start, due, start - day))
    }

    @Test
    fun `transforme sans remboursement - GIFTED`() {
        assertEquals(LoanStatus.GIFTED, computeLoanStatus(100_000L, 0L, start, due, now, giftedAmount = 100_000L))
    }

    @Test
    fun `transforme apres remboursement partiel - GIFTED et non REPAID`() {
        // 40 000 remboursés + 60 000 offerts = montant total : GIFTED doit primer sur REPAID.
        assertEquals(LoanStatus.GIFTED, computeLoanStatus(100_000L, 40_000L, start, due, now, giftedAmount = 60_000L))
    }

    @Test
    fun `GIFTED prime sur le retard et sur la date de debut`() {
        assertEquals(LoanStatus.GIFTED, computeLoanStatus(100_000L, 0L, start, due, due + 10 * day, giftedAmount = 100_000L))
        assertEquals(LoanStatus.GIFTED, computeLoanStatus(100_000L, 0L, start, due, start - day, giftedAmount = 100_000L))
    }

    @Test
    fun `liveStatus transmet toujours giftedAmount`() {
        assertEquals(LoanStatus.ONGOING, loan().liveStatus(now))
        assertEquals(LoanStatus.GIFTED, loan(repaid = 40_000L, gifted = 60_000L).liveStatus(now))
    }

    @Test
    fun `isSettled - seuls REPAID et GIFTED sont finaux`() {
        assertTrue(LoanStatus.REPAID.isSettled)
        assertTrue(LoanStatus.GIFTED.isSettled)
        assertFalse(LoanStatus.ONGOING.isSettled)
        assertFalse(LoanStatus.OVERDUE.isSettled)
        assertFalse(LoanStatus.UPCOMING.isSettled)
    }

    @Test
    fun `categorie cadeaux recus - meme nom canonique, type revenu, cles distinctes`() {
        assertEquals(SystemCategoryKey.GIFTS, SystemCategoryKey.of("Cadeaux", TransactionType.EXPENSE))
        assertEquals(SystemCategoryKey.GIFTS_RECEIVED, SystemCategoryKey.of("Cadeaux", TransactionType.INCOME))
    }

    @Test
    fun `mapper Room - champs cadeau conserves`() {
        val gifted = loan(repaid = 40_000L, gifted = 60_000L, giftTransactionId = 11L, giftedAt = now)
        assertEquals(gifted, gifted.toEntity(userId = 3L).toDomain())
        val plain = loan()
        val roundTrip = plain.toEntity(userId = 3L).toDomain()
        assertEquals(0L, roundTrip.giftedAmount)
        assertNull(roundTrip.giftTransactionId)
        assertNull(roundTrip.giftedAt)
    }

    @Test
    fun `sauvegarde - champs cadeau exportes, reimportes et remappes`() {
        val entity = loan(repaid = 40_000L, gifted = 60_000L, giftTransactionId = 11L, giftedAt = now).toEntity(userId = 3L)
        val dto = entity.toDto()
        assertEquals(60_000L, dto.giftedAmount)
        assertEquals(LoanStatus.GIFTED.name, entity.copy(status = LoanStatus.GIFTED).toDto().status)

        val remapped = dto.remapIds(
            newId = 70L,
            personIdMap = mapOf(1L to 100L),
            accountIdMap = mapOf(2L to 200L),
            transactionIdMap = mapOf(10L to 1000L, 11L to 1100L)
        )
        assertEquals(1000L, remapped.transactionId)
        assertEquals(1100L, remapped.giftTransactionId)

        val restored = remapped.copy(status = LoanStatus.GIFTED.name).toEntity(userId = 4L)
        assertEquals(LoanStatus.GIFTED, restored.status)
        assertEquals(60_000L, restored.giftedAmount)
        assertEquals(now, restored.giftedAt)
    }

    @Test
    fun `sauvegarde ancienne - sans champs cadeau, prêt non transforme`() {
        val dto = loan().toEntity(userId = 3L).toDto().copy(giftedAmount = 0L, giftTransactionId = null, giftedAt = null)
        val remapped = dto.remapIds(70L, mapOf(1L to 100L), mapOf(2L to 200L), mapOf(10L to 1000L))
        assertNull(remapped.giftTransactionId)
        assertEquals(0L, remapped.toEntity(userId = 4L).giftedAmount)
    }
}
