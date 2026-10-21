package com.naniger.arzikina.presentation.utilities.issuedreceipts

import com.naniger.arzikina.domain.issuedreceipt.IssuedReceiptError
import com.naniger.arzikina.domain.model.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IssuedReceiptDraftFactoryTest {

    private val complete = IssuedReceiptFormInput(
        businessName = "  Boutique Aïssa ", address = "Niamey", phone = "+227 90 00 00 00", email = " ", taxId = "",
        issuedAt = 1_000L, customerName = "Moussa", customerPhone = "", description = "Loyer de mars",
        amountInput = "15 000", currencyCode = "XOF", paymentMethod = PaymentMethod.MOBILE_MONEY, reference = " TX-1 ", notes = ""
    )

    @Test fun validInputBuildsTrimmedReceiptAndProfile() {
        val draft = IssuedReceiptDraftFactory.validate(complete) as IssuedReceiptDraft.Valid
        assertEquals("Boutique Aïssa", draft.receipt.issuer.businessName)
        assertEquals(1_500_000L, draft.receipt.amountMinor)
        assertNull(draft.receipt.issuer.email)
        assertNull(draft.receipt.customerPhone)
        assertEquals("TX-1", draft.receipt.transactionReference)
        assertEquals("", draft.receipt.number)
        assertEquals("Boutique Aïssa", draft.profile.businessName)
    }

    @Test fun customNumberIsValidated() {
        val bad = IssuedReceiptDraftFactory.validate(complete.copy(numberInput = "a/b")) as IssuedReceiptDraft.Invalid
        assertEquals(setOf(IssuedReceiptError.NUMBER_INVALID), bad.errors)
        assertTrue(IssuedReceiptDraftFactory.validate(complete.copy(numberInput = "FAC-2026.7")) is IssuedReceiptDraft.Valid)
    }

    @Test fun emptyFormReportsEveryRequiredField() {
        val invalid = IssuedReceiptDraftFactory.validate(IssuedReceiptFormInput()) as IssuedReceiptDraft.Invalid
        assertEquals(
            setOf(
                IssuedReceiptError.BUSINESS_NAME_BLANK, IssuedReceiptError.BUSINESS_ADDRESS_BLANK, IssuedReceiptError.BUSINESS_PHONE_INVALID,
                IssuedReceiptError.CUSTOMER_NAME_BLANK, IssuedReceiptError.DESCRIPTION_BLANK, IssuedReceiptError.AMOUNT_NOT_POSITIVE
            ),
            invalid.errors
        )
    }

    @Test fun unreadableAndZeroAmountsAreRejected() {
        for (amount in listOf("abc", "0", "-5")) {
            val invalid = IssuedReceiptDraftFactory.validate(complete.copy(amountInput = amount)) as IssuedReceiptDraft.Invalid
            assertEquals(setOf(IssuedReceiptError.AMOUNT_NOT_POSITIVE), invalid.errors)
        }
    }

    @Test fun hugeAmountIsRejected() {
        val invalid = IssuedReceiptDraftFactory.validate(complete.copy(amountInput = "999999999999999")) as IssuedReceiptDraft.Invalid
        assertEquals(setOf(IssuedReceiptError.AMOUNT_TOO_LARGE), invalid.errors)
    }

    @Test fun previewNeverThrowsOnIncompleteInput() {
        val receipt = IssuedReceiptDraftFactory.toReceipt(IssuedReceiptFormInput(amountInput = "1,5x"))
        assertEquals(0L, receipt.amountMinor)
        assertEquals("", receipt.customerName)
    }

    // --- Reçu détaillé -----------------------------------------------------------------------

    private val detailed = complete.copy(
        amountInput = "",
        detailed = true,
        lines = listOf(LineInput(1, "Sac de riz", "2", "5 000"), LineInput(2, "Huile (L)", "1,5", "2 000")),
        discountMode = DiscountMode.PERCENT, discountInput = "10", taxInput = "18"
    )

    @Test fun detailedReceiptComputesTheTotalFromTheLines() {
        // 10 000 + 3 000 = 13 000 ; remise 10 % = 1 300 ; taxable 11 700 ; taxe 18 % = 2 106 ; total 13 806
        val draft = IssuedReceiptDraftFactory.validate(detailed) as IssuedReceiptDraft.Valid
        assertEquals(1_380_600L, draft.receipt.amountMinor)
        assertEquals(2, draft.receipt.pricing!!.lines.size)
        assertEquals(1500L, draft.receipt.pricing!!.lines[1].quantityMilli)
        assertEquals(1_380_600L, IssuedReceiptDraftFactory.totalsOf(detailed)!!.totalMinor)
    }

    @Test fun amountFieldIsIgnoredWhenDetailed() {
        val draft = IssuedReceiptDraftFactory.validate(detailed.copy(amountInput = "abc"))
        assertTrue(draft is IssuedReceiptDraft.Valid)
    }

    @Test fun fixedDiscountIsCappedAtTheSubtotal() {
        val input = detailed.copy(discountMode = DiscountMode.FIXED, discountInput = "999 999", taxInput = "")
        val invalid = IssuedReceiptDraftFactory.validate(input) as IssuedReceiptDraft.Invalid
        assertEquals(setOf(IssuedReceiptError.AMOUNT_NOT_POSITIVE), invalid.errors)
    }

    @Test fun invalidLineValuesAreReported() {
        val input = detailed.copy(lines = listOf(LineInput(1, " ", "0", ""), LineInput(2, "A", "x", "10")))
        val invalid = IssuedReceiptDraftFactory.validate(input) as IssuedReceiptDraft.Invalid
        assertEquals(
            setOf(IssuedReceiptError.LINE_LABEL_BLANK, IssuedReceiptError.LINE_QUANTITY_INVALID, IssuedReceiptError.LINE_PRICE_INVALID),
            invalid.errors
        )
    }

    @Test fun noLinesIsInvalid() {
        val invalid = IssuedReceiptDraftFactory.validate(detailed.copy(lines = emptyList())) as IssuedReceiptDraft.Invalid
        assertEquals(setOf(IssuedReceiptError.LINES_EMPTY), invalid.errors)
    }

    @Test fun unreadableDiscountOrTaxIsReported() {
        assertEquals(
            setOf(IssuedReceiptError.DISCOUNT_INVALID),
            (IssuedReceiptDraftFactory.validate(detailed.copy(discountInput = "dix")) as IssuedReceiptDraft.Invalid).errors
        )
        assertEquals(
            setOf(IssuedReceiptError.TAX_INVALID),
            (IssuedReceiptDraftFactory.validate(detailed.copy(taxInput = "150")) as IssuedReceiptDraft.Invalid).errors
        )
    }

    @Test fun simpleReceiptHasNoPricing() {
        assertNull(IssuedReceiptDraftFactory.pricingOf(complete))
        assertNull(IssuedReceiptDraftFactory.totalsOf(complete))
    }

    @Test fun inputFromSimpleReceiptRoundTrips() {
        val receipt = (IssuedReceiptDraftFactory.validate(complete.copy(numberInput = "Recu-2026-001")) as IssuedReceiptDraft.Valid).receipt
        val editable = IssuedReceiptDraftFactory.inputFrom(receipt)
        assertEquals(receipt, IssuedReceiptDraftFactory.toReceipt(editable.input))
        assertEquals("Recu-2026-001", editable.input.numberInput)
        assertEquals(1L, editable.nextLineId)
    }

    @Test fun inputFromDetailedReceiptRoundTrips() {
        val source = complete.copy(
            numberInput = "Recu-2026-002", detailed = true, amountInput = "",
            lines = listOf(LineInput(1, "Sac", "2", "5 000"), LineInput(2, "Riz", "1,5", "2 000,50")),
            discountMode = DiscountMode.PERCENT, discountInput = "7,5", taxInput = "18"
        )
        val receipt = (IssuedReceiptDraftFactory.validate(source) as IssuedReceiptDraft.Valid).receipt
        val editable = IssuedReceiptDraftFactory.inputFrom(receipt, firstLineId = 10L)
        assertEquals(receipt, IssuedReceiptDraftFactory.toReceipt(editable.input))
        assertEquals(12L, editable.nextLineId)
        assertEquals(listOf(10L, 11L), editable.input.lines.map { it.id })
        assertEquals("1,5", editable.input.lines[1].quantityInput)
        assertEquals("7,5", editable.input.discountInput)
    }

    @Test fun inputFromFixedDiscountRoundTrips() {
        val source = complete.copy(
            detailed = true, amountInput = "", lines = listOf(LineInput(1, "Sac", "1", "10 000")),
            discountMode = DiscountMode.FIXED, discountInput = "500"
        )
        val receipt = (IssuedReceiptDraftFactory.validate(source) as IssuedReceiptDraft.Valid).receipt
        assertEquals(receipt, IssuedReceiptDraftFactory.toReceipt(IssuedReceiptDraftFactory.inputFrom(receipt).input))
    }
}
