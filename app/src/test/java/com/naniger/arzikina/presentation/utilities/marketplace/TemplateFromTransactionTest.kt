package com.naniger.arzikina.presentation.utilities.marketplace

import com.naniger.arzikina.domain.model.FeeType
import com.naniger.arzikina.domain.model.PaymentMethod
import com.naniger.arzikina.domain.model.Transaction
import com.naniger.arzikina.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Règles de « Créer un modèle à partir de cette transaction » (voir [TemplateFromTransaction]). */
class TemplateFromTransactionTest {

    private val courses = Transaction(
        id = 42L,
        amount = 1_000_000L,
        type = TransactionType.EXPENSE,
        accountId = 3L,
        categoryId = 7L,
        date = 1_790_000_000_000L,
        description = "  Courses du mois  ",
        latitude = 13.5,
        longitude = 2.1,
        paymentMethod = PaymentMethod.entries.first(),
        createdAt = 1_789_000_000_000L,
        feeTransactionId = 43L,
        receiptId = 9L
    )

    @Test
    fun `depense - seuls les champs pertinents sont repris`() {
        val prefill = TemplateFromTransaction.prefillOf(courses, categoryName = "Alimentation")

        assertEquals("Courses du mois", prefill.name)
        assertEquals("Courses du mois", prefill.description)
        assertEquals(TransactionType.EXPENSE, prefill.type)
        assertEquals(1_000_000L, prefill.amount)
        assertEquals(7L, prefill.categoryId)
        assertEquals(3L, prefill.accountId)
        // Date, id, frais, reçu, position, mode de paiement : absents de TemplatePrefill par construction.
    }

    @Test
    fun `revenu repris avec son type`() {
        val salary = courses.copy(type = TransactionType.INCOME, description = "Salaire", feeTransactionId = null)
        assertEquals(TransactionType.INCOME, TemplateFromTransaction.prefillOf(salary, null).type)
    }

    @Test
    fun `sans description - nom = categorie, sinon vide`() {
        val noDescription = courses.copy(description = "")
        assertEquals("Alimentation", TemplateFromTransaction.prefillOf(noDescription, "Alimentation").name)
        assertEquals("", TemplateFromTransaction.prefillOf(noDescription, null).name)
        assertEquals("", TemplateFromTransaction.prefillOf(noDescription, null).description)
    }

    @Test
    fun `sans categorie - a choisir dans le formulaire`() {
        assertEquals(0L, TemplateFromTransaction.prefillOf(courses.copy(categoryId = null), null).categoryId)
    }

    @Test
    fun `nom tronque, description complete conservee`() {
        val long = "x".repeat(100)
        val prefill = TemplateFromTransaction.prefillOf(courses.copy(description = long), null)
        assertEquals(TemplateFromTransaction.MAX_NAME_LENGTH, prefill.name.length)
        assertEquals(long, prefill.description)
    }

    @Test
    fun `eligibilite - depense et revenu oui, transfert et ligne de frais non`() {
        assertTrue(TemplateFromTransaction.isEligible(courses))
        assertTrue(TemplateFromTransaction.isEligible(courses.copy(type = TransactionType.INCOME)))
        assertFalse(TemplateFromTransaction.isEligible(courses.copy(type = TransactionType.TRANSFER, transferAccountId = 4L, categoryId = null)))
        assertFalse(TemplateFromTransaction.isEligible(courses.copy(feeType = FeeType.entries.first(), feeTransactionId = null)))
    }
}
