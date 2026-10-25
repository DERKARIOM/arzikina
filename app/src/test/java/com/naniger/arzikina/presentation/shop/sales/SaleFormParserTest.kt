package com.naniger.arzikina.presentation.shop.sales

import com.naniger.arzikina.domain.model.ReceiptDiscount
import com.naniger.arzikina.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SaleFormParserTest {

    @Test fun `pourcentage en points de base`() {
        assertEquals(1_000, SaleFormParser.parseBasisPoints("10"))
        assertEquals(750, SaleFormParser.parseBasisPoints("7,5"))
        assertEquals(750, SaleFormParser.parseBasisPoints("7.5 %"))
        assertEquals(1, SaleFormParser.parseBasisPoints("0,01"))
        assertEquals(10_000, SaleFormParser.parseBasisPoints("100"))
        assertEquals(0, SaleFormParser.parseBasisPoints("0"))
    }

    @Test fun `pourcentage invalide`() {
        assertNull(SaleFormParser.parseBasisPoints(""))
        assertNull(SaleFormParser.parseBasisPoints("abc"))
        assertNull(SaleFormParser.parseBasisPoints("-5"))
        assertNull(SaleFormParser.parseBasisPoints("100,01"))
        assertNull(SaleFormParser.parseBasisPoints("101"))
        assertNull(SaleFormParser.parseBasisPoints("1,234"))
    }

    @Test fun `champ vide ou mode aucune - aucune remise`() {
        assertEquals(DiscountParse.Valid(ReceiptDiscount.None), SaleFormParser.parseDiscount(DiscountMode.PERCENT, "  "))
        assertEquals(DiscountParse.Valid(ReceiptDiscount.None), SaleFormParser.parseDiscount(DiscountMode.NONE, "50"))
        assertEquals(DiscountParse.Valid(ReceiptDiscount.None), SaleFormParser.parseDiscount(DiscountMode.FIXED, ""))
    }

    @Test fun `remise en pourcentage`() {
        assertEquals(
            DiscountParse.Valid(ReceiptDiscount.Percent(500)),
            SaleFormParser.parseDiscount(DiscountMode.PERCENT, "5")
        )
        assertEquals(DiscountParse.Invalid, SaleFormParser.parseDiscount(DiscountMode.PERCENT, "150"))
    }

    @Test fun `remise en montant fixe`() {
        val expected = Money.parseToMinorUnits("500")!!
        assertEquals(DiscountParse.Valid(ReceiptDiscount.Fixed(expected)), SaleFormParser.parseDiscount(DiscountMode.FIXED, "500"))
        assertEquals(DiscountParse.Invalid, SaleFormParser.parseDiscount(DiscountMode.FIXED, "abc"))
    }
}
