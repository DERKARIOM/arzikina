package com.naniger.arzikina.presentation.utilities.loans

import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.LoanType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Choix du gabarit de description automatique (la mise en forme elle-même est du `getString`). */
class LoanGiftDescriptionTest {

    @Test
    fun `pret - Cadeau a personne, sans elision quel que soit le nom`() {
        assertEquals(R.string.loan_gift_description_to, LoanGiftDescription.templateRes(LoanType.LENT, "Abdou"))
        assertEquals(R.string.loan_gift_description_to, LoanGiftDescription.templateRes(LoanType.LENT, "Aïcha"))
    }

    @Test
    fun `emprunt - Cadeau de la part d Aicha avec elision`() {
        assertEquals(R.string.loan_gift_description_from_elided, LoanGiftDescription.templateRes(LoanType.BORROWED, "Aïcha"))
    }

    @Test
    fun `emprunt - Cadeau de la part de Moussa sans elision`() {
        assertEquals(R.string.loan_gift_description_from, LoanGiftDescription.templateRes(LoanType.BORROWED, "Moussa"))
    }
}
