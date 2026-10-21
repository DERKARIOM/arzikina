package com.naniger.arzikina.domain.issuedreceipt.pdf

import com.naniger.arzikina.domain.model.IssuedReceipt
import com.naniger.arzikina.domain.model.IssuerSnapshot
import com.naniger.arzikina.domain.model.PaymentMethod
import com.naniger.arzikina.domain.model.ReceiptLine
import com.naniger.arzikina.domain.model.ReceiptPricing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptPdfLayoutEngineTest {
    private val measurer = PdfTextMeasurer { text, s -> text.length * s.sizePt * 0.5f }
    private val issuer = IssuerSnapshot("Boutique Aïssa", "Niamey, Plateau", "+227 90 00 00 00")

    private fun receipt(
        description: String = "Paiement du loyer",
        pricing: ReceiptPricing? = null,
        notes: String? = null,
        amount: Long = 1_500_000
    ) = IssuedReceipt(
        number = "Recu-2026-001", issuedAt = 0L, customerName = "Moussa Ibrahim", description = description,
        amountMinor = amount, currencyCode = "XOF", paymentMethod = PaymentMethod.CASH, issuer = issuer,
        pricing = pricing, notes = notes, issuedByName = "Aïssa", issuedByTitle = "Gérante"
    )

    private fun layout(r: IssuedReceipt, logo: PdfLogo? = null, signature: PdfLogo? = null) =
        ReceiptPdfLayoutEngine.layout(ReceiptPdfContentBuilder.build(r, "fr", logo, signature), measurer)

    private fun texts(page: PdfPagePlan) = page.ops.filterIsInstance<PdfOp.Text>()

    @Test fun shortReceiptFitsOnOnePage() {
        val pages = layout(receipt())
        assertEquals(1, pages.size)
        val all = texts(pages[0]).map { it.text }
        assertTrue("REÇU DE PAIEMENT" in all)
        assertTrue("Page 1 / 1" in all)
        assertTrue(all.any { it.startsWith("15") && it.endsWith("XOF") })
    }

    @Test fun longDescriptionFlowsOverSeveralPagesWithinMargins() {
        val description = (1..200).joinToString("\n") { "Ligne $it de la description détaillée de la prestation" }
        val pages = layout(receipt(description = description))
        assertTrue(pages.size >= 3)
        assertWithinPage(pages)
        // Aucune ligne perdue : les 200 lignes sont toutes présentes, dans l'ordre.
        val rendered = pages.flatMap { texts(it) }.map { it.text }.filter { it.startsWith("Ligne ") }
        assertEquals((1..200).map { "Ligne $it de la description détaillée de la prestation" }, rendered)
        // Numérotation cohérente.
        pages.forEachIndexed { i, p -> assertTrue("Page ${i + 1} / ${pages.size}" in texts(p).map { it.text }) }
    }

    @Test fun tableHeaderIsRepeatedOnEachPageThatHasRows() {
        val lines = (1..120).map { ReceiptLine("Article $it", 1000, 100_000) }
        val pages = layout(receipt(pricing = ReceiptPricing(lines), amount = 120 * 100_000L))
        assertTrue(pages.size >= 3)
        assertWithinPage(pages)
        val pagesWithRows = pages.filter { p -> texts(p).any { it.text.startsWith("Article ") } }
        assertTrue(pagesWithRows.size >= 2)
        pagesWithRows.forEach { p -> assertTrue(texts(p).any { it.text == "Désignation" }) }
        assertEquals(120, pages.flatMap { texts(it) }.count { it.text.startsWith("Article ") })
    }

    @Test fun logoKeepsItsRatioAndFitsItsBox() {
        val page = layout(receipt(), PdfLogo(1000, 250))[0]
        val logo = page.ops.filterIsInstance<PdfOp.Logo>().single()
        assertEquals(4f, logo.width / logo.height, 0.001f)
        assertTrue(logo.width <= ReceiptPdfSpec.LOGO_MAX_WIDTH + 0.01f && logo.height <= ReceiptPdfSpec.LOGO_MAX_HEIGHT + 0.01f)
        val tall = layout(receipt(), PdfLogo(100, 400))[0].ops.filterIsInstance<PdfOp.Logo>().single()
        assertEquals(0.25f, tall.width / tall.height, 0.001f)
        assertTrue(tall.height <= ReceiptPdfSpec.LOGO_MAX_HEIGHT + 0.01f)
    }

    @Test fun noLogoMeansNoLogoOp() {
        assertTrue(layout(receipt())[0].ops.none { it is PdfOp.Logo })
    }

    @Test fun longNotesAndSignatureStayConsistent() {
        val pages = layout(receipt(notes = "Observation. ".repeat(600)))
        assertWithinPage(pages)
        assertTrue(pages.flatMap { texts(it) }.any { it.text == "Signature" })
    }

    @Test fun veryLongBusinessNameAndUnbreakableWordDoNotOverflow() {
        val r = receipt(description = "x".repeat(1500)).let { it.copy(issuer = issuer.copy(businessName = "N".repeat(300))) }
        assertWithinPage(layout(r))
    }

    @Test fun signatureIsScaledCenteredAndSitsAboveTheRule() {
        val pages = layout(receipt(), signature = PdfLogo(600, 200))
        val sign = pages[0].ops.filterIsInstance<PdfOp.Signature>().single()
        val right = ReceiptPdfSpec.MARGIN_X + ReceiptPdfSpec.CONTENT_WIDTH
        val boxLeft = ReceiptPdfSpec.MARGIN_X + ReceiptPdfSpec.CONTENT_WIDTH / 2f + 12f
        // Ratio d'origine conservé, jamais plus haut que le cadre.
        assertEquals(3f, sign.width / sign.height, 0.001f)
        assertTrue(sign.height <= ReceiptPdfSpec.SIGNATURE_MAX_HEIGHT + 0.001f)
        // Dans la moitié droite, centrée horizontalement.
        assertTrue(sign.x >= boxLeft - 0.001f && sign.x + sign.width <= right + 0.001f)
        assertEquals((boxLeft + right) / 2f, sign.x + sign.width / 2f, 0.01f)
        // Au-dessus du filet de signature, sous le libellé « Signature ».
        val rule = pages[0].ops.filterIsInstance<PdfOp.Line>().last { it.x1 == boxLeft }
        assertTrue(sign.y + sign.height <= rule.y1)
        val label = texts(pages[0]).first { it.text == "Signature" }
        assertTrue(sign.y >= label.baselineY)
    }

    @Test fun tallSignatureIsLimitedByHeightAndNoSignatureMeansNoOp() {
        val tall = layout(receipt(), signature = PdfLogo(100, 400))[0].ops.filterIsInstance<PdfOp.Signature>().single()
        assertEquals(ReceiptPdfSpec.SIGNATURE_MAX_HEIGHT, tall.height, 0.001f)
        assertEquals(0.25f, tall.width / tall.height, 0.001f)
        assertTrue(layout(receipt())[0].ops.none { it is PdfOp.Signature })
        assertTrue(layout(receipt(), signature = PdfLogo(0, 0))[0].ops.none { it is PdfOp.Signature })
    }

    @Test fun signatureStaysWithItsBlockWhenTheReceiptSpansPages() {
        val pages = layout(receipt(notes = "Observation. ".repeat(600)), signature = PdfLogo(500, 200))
        assertWithinPage(pages)
        val withSignature = pages.filter { p -> p.ops.any { it is PdfOp.Signature } }
        assertEquals(1, withSignature.size)
        assertTrue(texts(withSignature[0]).any { it.text == "Signature" })
    }


    private fun assertWithinPage(pages: List<PdfPagePlan>) {
        val right = ReceiptPdfSpec.MARGIN_X + ReceiptPdfSpec.CONTENT_WIDTH
        pages.forEach { page ->
            page.ops.forEach { op ->
                when (op) {
                    is PdfOp.Text -> {
                        val footer = op.baselineY == ReceiptPdfSpec.FOOTER_BASELINE
                        if (!footer) assertTrue("texte sous la zone : ${op.text}", op.baselineY <= ReceiptPdfSpec.CONTENT_BOTTOM)
                        val w = measurer.width(op.text, op.style)
                        val (l, r) = if (op.align == PdfAlign.LEFT) op.x to op.x + w else op.x - w to op.x
                        assertTrue("texte hors marges : ${op.text}", l >= ReceiptPdfSpec.MARGIN_X - 0.01f && r <= right + 0.01f)
                    }
                    is PdfOp.Rect -> assertTrue(op.y + op.height <= ReceiptPdfSpec.CONTENT_BOTTOM + 0.01f)
                    is PdfOp.Signature -> assertTrue(op.x + op.width <= right + 0.01f && op.y + op.height <= ReceiptPdfSpec.CONTENT_BOTTOM + 0.01f)
                    else -> Unit
                }
            }
        }
    }
}
