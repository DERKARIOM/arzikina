package com.naniger.arzikina.data.issuedreceipt

import com.naniger.arzikina.domain.model.IssuerSnapshot
import com.naniger.arzikina.domain.model.ReceiptDiscount
import com.naniger.arzikina.domain.model.ReceiptLine
import com.naniger.arzikina.domain.model.ReceiptPricing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IssuedReceiptJsonTest {

    @Test
    fun `pricing round trips for every discount kind`() {
        val lines = listOf(ReceiptLine("Riz « local »", 2_500, 150_000), ReceiptLine("Huile", 1_000, 90_000))
        listOf(ReceiptDiscount.None, ReceiptDiscount.Percent(750), ReceiptDiscount.Fixed(12_500)).forEach { discount ->
            val pricing = ReceiptPricing(lines, discount, taxBasisPoints = 1_800)
            assertEquals(pricing, IssuedReceiptJson.decodePricing(IssuedReceiptJson.encodePricing(pricing)))
        }
    }

    @Test
    fun `missing or corrupted pricing decodes to null`() {
        assertNull(IssuedReceiptJson.decodePricing(null))
        assertNull(IssuedReceiptJson.decodePricing("{not json"))
    }

    @Test
    fun `unknown keys are ignored so older clients can read newer data`() {
        val raw = """{"lines":[{"label":"A","quantityMilli":1000,"unitPriceMinor":500,"extra":1}],"future":true}"""
        assertEquals(ReceiptPricing(listOf(ReceiptLine("A", 1_000, 500))), IssuedReceiptJson.decodePricing(raw))
    }

    @Test
    fun `issuer snapshot round trips and tolerates corruption`() {
        val issuer = IssuerSnapshot("Boutique", "Niamey", "+227 90 00 00 00", "a@b.ne", "NIF123", "logo.png")
        assertEquals(issuer, IssuedReceiptJson.decodeIssuer(IssuedReceiptJson.encodeIssuer(issuer)))
        assertEquals("", IssuedReceiptJson.decodeIssuer("{broken").businessName)
    }
}
