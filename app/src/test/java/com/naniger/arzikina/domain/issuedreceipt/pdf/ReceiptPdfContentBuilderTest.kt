package com.naniger.arzikina.domain.issuedreceipt.pdf

import com.naniger.arzikina.domain.model.IssuedReceipt
import com.naniger.arzikina.domain.model.IssuerSnapshot
import com.naniger.arzikina.domain.model.PaymentMethod
import com.naniger.arzikina.domain.model.ReceiptDiscount
import com.naniger.arzikina.domain.model.ReceiptLine
import com.naniger.arzikina.domain.model.ReceiptPricing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class ReceiptPdfContentBuilderTest {
    private val nb = ' '
    private val issuer = IssuerSnapshot("Boutique Aïssa", "Niamey, Plateau", "+227 90 00 00 00", email = "a@b.ne", taxId = "NIF 1")
    private fun receipt(pricing: ReceiptPricing? = null, amount: Long = 1_500_000, method: PaymentMethod = PaymentMethod.MOBILE_MONEY) = IssuedReceipt(
        number = "Recu-2026-001",
        issuedAt = Calendar.getInstance().apply { clear(); set(2026, Calendar.MARCH, 5) }.timeInMillis,
        customerName = "Moussa", customerPhone = "90112233", description = "Loyer", amountMinor = amount,
        currencyCode = "XOF", paymentMethod = method, transactionReference = "TX-9", issuer = issuer, pricing = pricing
    )

    @Test fun simpleReceiptFrench() {
        val c = ReceiptPdfContentBuilder.build(receipt(), "fr", null)
        assertNull(c.table)
        assertEquals("15${nb}000${nb}XOF", c.totalText)
        assertEquals("05/03/2026", c.dateText)
        assertEquals(listOf("Niamey, Plateau", "Tél. : +227 90 00 00 00", "E-mail : a@b.ne", "ID fiscal : NIF 1"), c.contactLines)
        assertEquals(listOf(PdfKeyValue("Mode de paiement", "Mobile Money"), PdfKeyValue("Référence", "TX-9")), c.details)
        assertEquals("Tél. : 90112233", c.customerPhone)
    }

    @Test fun englishLabels() {
        val c = ReceiptPdfContentBuilder.build(receipt(method = PaymentMethod.BANK_TRANSFER), "en-US", null)
        assertEquals("PAYMENT RECEIPT", c.labels.title)
        assertEquals("Bank transfer", c.details.first().value)
    }

    @Test fun detailedReceiptWithDiscountAndTax() {
        // 2 x 5 000 + 1,5 x 2 000 = 13 000 ; remise 10 % = 1 300 ; taxable 11 700 ; taxe 18 % = 2 106 ; total 13 806
        val pricing = ReceiptPricing(
            lines = listOf(ReceiptLine("Sac", 2000, 500_000), ReceiptLine("Riz (kg)", 1500, 200_000)),
            discount = ReceiptDiscount.Percent(1000), taxBasisPoints = 1800
        )
        val c = ReceiptPdfContentBuilder.build(receipt(pricing, 1_380_600), "fr", PdfLogo(10, 10))
        val table = c.table!!
        assertEquals(PdfTableRow("Riz (kg)", "1,5", "2${nb}000${nb}XOF", "3${nb}000${nb}XOF"), table.rows[1])
        assertEquals(
            listOf(
                PdfKeyValue("Sous-total", "13${nb}000${nb}XOF"),
                PdfKeyValue("Remise (10${nb}%)", "-1${nb}300${nb}XOF"),
                PdfKeyValue("Taxe (18${nb}%)", "2${nb}106${nb}XOF")
            ),
            table.summary
        )
        assertEquals("13${nb}806${nb}XOF", c.totalText)
    }

    @Test fun decimalsAppearEverywhereWhenOneAmountNeedsThem() {
        val pricing = ReceiptPricing(listOf(ReceiptLine("A", 1000, 1050)))
        val c = ReceiptPdfContentBuilder.build(receipt(pricing, 1050), "fr", null)
        assertEquals("10,50${nb}XOF", c.totalText)
        assertEquals("10,50${nb}XOF", c.table!!.rows[0].unitPrice)
    }
}
