package com.naniger.arzikina.domain.shop

import com.naniger.arzikina.domain.model.ReceiptDiscount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaleCalculatorTest {

    private fun line(product: Long, price: Long, qtyMilli: Long) = SaleLineDraft(product, price, qtyMilli)

    @Test fun `exemple du cahier des charges - 2 x 2500 plus 1 x 3000 egal 8000`() {
        val totals = SaleCalculator.totalsOrNull(listOf(line(1, 2_500, 2_000), line(2, 3_000, 1_000)))!!
        assertEquals(8_000L, totals.subtotalMinor)
        assertEquals(0L, totals.discountMinor)
        assertEquals(8_000L, totals.totalMinor)
    }

    @Test fun `vente de 10000 en un seul article`() {
        assertEquals(10_000L, SaleCalculator.totalsOrNull(listOf(line(1, 10_000, 1_000)))!!.totalMinor)
    }

    @Test fun `quantite fractionnaire - 0,5 kg a 1500 egale 750`() {
        assertEquals(750L, SaleCalculator.lineTotalOrNull(line(1, 1_500, 500)))
    }

    @Test fun `arrondi a l'unite mineure, demi vers le haut`() {
        // 1,5 x 3 = 4,5 -> 5 ; 0,001 x 499 = 0,499 -> 0
        assertEquals(5L, SaleCalculator.lineTotalOrNull(line(1, 3, 1_500)))
        assertEquals(0L, SaleCalculator.lineTotalOrNull(line(1, 499, 1)))
        assertEquals(1L, SaleCalculator.lineTotalOrNull(line(1, 500, 1)))
    }

    @Test fun `remise en pourcentage`() {
        // 10 % de 8000 = 800 (1000 points de base)
        val totals = SaleCalculator.totalsOrNull(
            listOf(line(1, 2_500, 2_000), line(2, 3_000, 1_000)),
            ReceiptDiscount.Percent(1_000)
        )!!
        assertEquals(800L, totals.discountMinor)
        assertEquals(7_200L, totals.totalMinor)
    }

    @Test fun `remise fixe plafonnee au sous-total`() {
        val totals = SaleCalculator.totalsOrNull(listOf(line(1, 1_000, 1_000)), ReceiptDiscount.Fixed(5_000))!!
        assertEquals(1_000L, totals.discountMinor)
        assertEquals(0L, totals.totalMinor)
    }

    @Test fun `panier vide ou ligne invalide donnent null`() {
        assertNull(SaleCalculator.totalsOrNull(emptyList()))
        assertNull(SaleCalculator.totalsOrNull(listOf(line(1, 1_000, 0))))
        assertNull(SaleCalculator.totalsOrNull(listOf(line(1, -1, 1_000))))
        assertNull(SaleCalculator.totalsOrNull(listOf(line(1, 1_000, -1_000))))
    }

    @Test fun `un montant hors plafond donne null sans debordement`() {
        assertNull(SaleCalculator.totalsOrNull(listOf(line(1, Long.MAX_VALUE / 2, 1_000_000))))
    }

    @Test fun `le total de la vente est la somme des lignes arrondies une a une`() {
        val lines = listOf(line(1, 333, 1_500), line(2, 333, 1_500)) // 499,5 -> 500 chacune
        val total = SaleCalculator.totalsOrNull(lines)!!.totalMinor
        assertEquals(lines.sumOf { SaleCalculator.lineTotalOrNull(it)!! }, total)
        assertEquals(1_000L, total)
    }

    @Test fun `stock insuffisant - quantites d'un meme produit cumulees`() {
        val lines = listOf(line(1, 100, 2_000), line(1, 100, 2_000), line(2, 100, 1_000))
        // produit 1 : 4 demandes pour 3 en stock ; produit 2 : 1 pour 1.
        val insufficient = SaleCalculator.insufficientProducts(lines, mapOf(1L to 3_000L, 2L to 1_000L))
        assertEquals(listOf(1L), insufficient)
    }

    @Test fun `stock suffisant donne une liste vide, produit inconnu compte pour un stock nul`() {
        val ok = SaleCalculator.insufficientProducts(listOf(line(1, 100, 1_000)), mapOf(1L to 1_000L))
        assertTrue(ok.isEmpty())
        assertEquals(listOf(9L), SaleCalculator.insufficientProducts(listOf(line(9, 100, 1_000)), emptyMap()))
    }
}
