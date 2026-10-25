package com.naniger.arzikina.domain.shop

import com.naniger.arzikina.domain.issuedreceipt.IssuedReceiptValidator
import com.naniger.arzikina.domain.issuedreceipt.ReceiptCalculator
import com.naniger.arzikina.domain.model.IssuerProfile
import com.naniger.arzikina.domain.model.PaymentMethod
import com.naniger.arzikina.domain.model.ReceiptDiscount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SaleDomainTest {

    private val profile = IssuerProfile(
        businessName = "Boutique Awa", address = "Marché central, Niamey", phone = "+227 90 00 00 00",
        defaultIssuedByName = "Awa", defaultIssuedByTitle = "Gérante", signatureFileName = "sig.png"
    )

    private fun sale(discount: Long = 0, customer: String? = null) = Sale(
        id = 1, reference = "V-20261025-AB3D", soldAt = 1_790_000_000_000, currencyCode = "XOF", customerName = customer,
        customerPhone = null, subtotalMinor = 800_000, discountMinor = discount, totalMinor = 800_000 - discount,
        status = SaleStatus.CONFIRMED, accountId = 5, paymentMethod = PaymentMethod.CASH,
        lines = listOf(
            SaleLine(1, 10, "Sucre 1 kg", 250_000, 180_000, 2_000, 500_000),
            SaleLine(2, 11, "Savon", 300_000, null, 1_000, 300_000)
        )
    )

    // --- Reçu -------------------------------------------------------------------------------

    @Test fun `le recu est valide et son total egale celui de la vente`() {
        val receipt = SaleReceiptFactory.build(sale(), profile, "Client", "Vente")
        assertTrue(IssuedReceiptValidator.validate(receipt).isEmpty())
        assertEquals(800_000L, ReceiptCalculator.totalsOrNull(receipt.pricing!!)!!.totalMinor)
        assertEquals(800_000L, receipt.amountMinor)
    }

    @Test fun `la remise est reprise et le total reste identique`() {
        val s = sale(discount = 80_000)
        val receipt = SaleReceiptFactory.build(s, profile, "Client", "Vente")
        assertEquals(ReceiptDiscount.Fixed(80_000), receipt.pricing!!.discount)
        assertEquals(s.totalMinor, ReceiptCalculator.totalsOrNull(receipt.pricing!!)!!.totalMinor)
        assertTrue(IssuedReceiptValidator.validate(receipt).isEmpty())
    }

    @Test fun `le recu fige les lignes, le client et l'emetteur`() {
        val receipt = SaleReceiptFactory.build(sale(customer = "Awa Diallo"), profile, "Client", "Vente")
        assertEquals(listOf("Sucre 1 kg", "Savon"), receipt.pricing!!.lines.map { it.label })
        assertEquals(listOf(250_000L, 300_000L), receipt.pricing!!.lines.map { it.unitPriceMinor })
        assertEquals("Awa Diallo", receipt.customerName)
        assertEquals("V-20261025-AB3D", receipt.transactionReference)
        assertEquals("Vente V-20261025-AB3D", receipt.description)
        assertEquals("Boutique Awa", receipt.issuer.businessName)
        assertEquals("sig.png", receipt.issuer.signatureFileName)
        assertEquals("Gérante", receipt.issuedByTitle)
    }

    @Test fun `client de passage - nom par defaut fourni par l'appelant`() {
        assertEquals("Client", SaleReceiptFactory.build(sale(), profile, "Client", "Vente").customerName)
        assertEquals("Client", SaleReceiptFactory.build(sale(customer = "  "), profile, "Client", "Vente").customerName)
    }

    @Test fun `profil incomplet - le recu est invalide`() {
        val receipt = SaleReceiptFactory.build(sale(), profile.copy(address = " "), "Client", "Vente")
        assertTrue(IssuedReceiptValidator.validate(receipt).isNotEmpty())
    }

    // --- Annulation -------------------------------------------------------------------------

    @Test fun `remise en stock`() {
        assertEquals(10_000L, SaleCancellation.restoredStock(8_000, 2_000))
        assertEquals(2_000L, SaleCancellation.restoredStock(0, 2_000))
    }

    @Test fun `remise en stock refusee en cas de depassement ou de quantite nulle`() {
        assertNull(SaleCancellation.restoredStock(StockRules.MAX_QUANTITY_MILLI, 1_000))
        assertNull(SaleCancellation.restoredStock(Long.MAX_VALUE, 1))
        assertNull(SaleCancellation.restoredStock(1_000, 0))
    }

    @Test fun `motif d'annulation nettoye`() {
        assertEquals("Erreur de saisie", SaleCancellation.cleanReason("  Erreur   de saisie "))
        assertNull(SaleCancellation.cleanReason("   "))
        assertNull(SaleCancellation.cleanReason(null))
        assertEquals(SaleCancellation.MAX_REASON_LENGTH, SaleCancellation.cleanReason("x".repeat(999))!!.length)
    }

    // --- Périodes ---------------------------------------------------------------------------

    private val niamey = ZoneId.of("Africa/Niamey")
    private val now = ZonedDateTime.of(2026, 10, 25, 21, 45, 0, 0, niamey).toInstant().toEpochMilli()
    private fun at(y: Int, m: Int, d: Int, h: Int = 0) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, niamey).toInstant().toEpochMilli()

    @Test fun `aujourd'hui commence a minuit local`() {
        val (from, to) = SalePeriod.TODAY.bounds(now, niamey)
        assertEquals(at(2026, 10, 25), from)
        assertEquals(at(2026, 10, 26), to)
    }

    @Test fun `7 et 30 jours comptent le jour courant`() {
        assertEquals(at(2026, 10, 19), SalePeriod.LAST_7_DAYS.bounds(now, niamey).first)
        assertEquals(at(2026, 9, 26), SalePeriod.LAST_30_DAYS.bounds(now, niamey).first)
    }

    @Test fun `toutes les periodes sont ouvertes`() {
        assertEquals(Long.MIN_VALUE to Long.MAX_VALUE, SalePeriod.ALL.bounds(now, niamey))
    }

    @Test fun `un changement d'heure ne decale pas la periode`() {
        val paris = ZoneId.of("Europe/Paris")
        val afterDst = ZonedDateTime.of(2026, 10, 25, 12, 0, 0, 0, paris).toInstant().toEpochMilli()
        val (from, to) = SalePeriod.TODAY.bounds(afterDst, paris)
        assertEquals(ZonedDateTime.of(2026, 10, 25, 0, 0, 0, 0, paris).toInstant().toEpochMilli(), from)
        assertEquals(25 * 3_600_000L, to - from) // le 25 octobre 2026 (fin de l’heure d’été) dure 25 h en France
    }
}
