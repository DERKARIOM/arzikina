package com.naniger.arzikina.domain.issuedreceipt.pdf

import com.naniger.arzikina.domain.issuedreceipt.pdf.ReceiptPdfSpec as S

/**
 * Dispose un [ReceiptPdfContent] sur des pages A4 : en-tête, client, description, tableau de
 * détail, totaux, paiement, observations, signature, pied de page numéroté. Gère le débordement :
 * les textes longs sont coupés ligne par ligne d'une page à l'autre ; les lignes du tableau ne sont
 * jamais coupées et l'en-tête du tableau est répété ; les blocs courts (totaux, signature) restent
 * d'un seul tenant. Logique pure : testée avec une fausse mesure de texte, rendue par Android.
 */
object ReceiptPdfLayoutEngine {

    private const val HEADER_LEFT_WIDTH = 300f
    private const val QTY_WIDTH = 56f
    private const val UNIT_WIDTH = 92f
    private const val TOTAL_WIDTH = 96f
    private const val CELL_PAD = 6f
    private const val SIGNATURE_HEIGHT = 84f
    private const val RIGHT = S.MARGIN_X + S.CONTENT_WIDTH

    fun layout(content: ReceiptPdfContent, measurer: PdfTextMeasurer): List<PdfPagePlan> {
        val flow = PageFlow()
        header(flow, content, measurer)
        customer(flow, content, measurer)
        textSection(flow, content.labels.description, content.description, measurer)
        content.table?.let { table(flow, content, it, measurer) }
        total(flow, content, measurer)
        content.notes?.takeIf { it.isNotBlank() }?.let { textSection(flow, content.labels.notes, it, measurer) }
        signature(flow, content, measurer)
        return footers(flow, content)
    }

    // --- En-tête -----------------------------------------------------------------------------

    private fun header(flow: PageFlow, c: ReceiptPdfContent, m: PdfTextMeasurer) {
        val top = flow.y
        var logoWidth = 0f
        var logoHeight = 0f
        c.logo?.takeIf { it.widthPx > 0 && it.heightPx > 0 }?.let { logo ->
            val scale = minOf(S.LOGO_MAX_WIDTH / logo.widthPx, S.LOGO_MAX_HEIGHT / logo.heightPx)
            logoWidth = logo.widthPx * scale
            logoHeight = logo.heightPx * scale
            flow.add(PdfOp.Logo(S.MARGIN_X, top, logoWidth, logoHeight))
        }
        val textX = S.MARGIN_X + if (logoWidth > 0) logoWidth + 12f else 0f
        val textWidth = S.MARGIN_X + HEADER_LEFT_WIDTH - textX
        var leftY = top
        leftY = lines(flow, PdfTextWrapper.wrap(c.businessName, textWidth, S.BUSINESS, m), textX, leftY, S.BUSINESS)
        leftY += 3f
        c.contactLines.forEach { leftY = lines(flow, PdfTextWrapper.wrap(it, textWidth, S.CONTACT, m), textX, leftY, S.CONTACT) }

        val rightWidth = S.CONTENT_WIDTH - HEADER_LEFT_WIDTH - 12f
        var rightY = top
        rightY = lines(flow, PdfTextWrapper.wrap(c.labels.title, rightWidth, S.TITLE, m), RIGHT, rightY, S.TITLE, PdfAlign.RIGHT)
        rightY += 4f
        rightY = lines(flow, PdfTextWrapper.wrap("${c.labels.numberPrefix} ${c.number}", rightWidth, S.BODY_BOLD, m), RIGHT, rightY, S.BODY_BOLD, PdfAlign.RIGHT)
        rightY = lines(flow, listOf("${c.labels.date} : ${c.dateText}"), RIGHT, rightY, S.BODY, PdfAlign.RIGHT)

        val bottom = maxOf(leftY, rightY, top + logoHeight) + 12f
        flow.add(PdfOp.Line(S.MARGIN_X, bottom, RIGHT, bottom, S.ACCENT, 1.5f))
        flow.advance(bottom - flow.y + S.SECTION_GAP + 4f)
    }

    // --- Sections ----------------------------------------------------------------------------

    private fun customer(flow: PageFlow, c: ReceiptPdfContent, m: PdfTextMeasurer) {
        val nameLines = PdfTextWrapper.wrap(c.customerName, S.CONTENT_WIDTH, S.NAME, m)
        val height = S.LABEL.lineHeight + 2f + nameLines.size * S.NAME.lineHeight + (if (c.customerPhone != null) S.BODY.lineHeight else 0f)
        flow.ensure(height)
        var y = lines(flow, listOf(c.labels.customer), S.MARGIN_X, flow.y, S.LABEL) + 2f
        y = lines(flow, nameLines, S.MARGIN_X, y, S.NAME)
        c.customerPhone?.let { y = lines(flow, listOf(it), S.MARGIN_X, y, S.BODY) }
        flow.advance(y - flow.y + S.SECTION_GAP)
    }

    /** Section de texte longue : l'intitulé reste avec sa première ligne, puis le texte se coupe ligne par ligne. */
    private fun textSection(flow: PageFlow, label: String, text: String, m: PdfTextMeasurer) {
        val wrapped = PdfTextWrapper.wrap(text, S.CONTENT_WIDTH, S.BODY, m)
        flow.ensure(S.LABEL.lineHeight + 2f + S.BODY.lineHeight)
        flow.add(textOp(S.MARGIN_X, flow.y, label, S.LABEL))
        flow.advance(S.LABEL.lineHeight + 2f)
        wrapped.forEach { line ->
            flow.ensure(S.BODY.lineHeight)
            if (line.isNotEmpty()) flow.add(textOp(S.MARGIN_X, flow.y, line, S.BODY))
            flow.advance(S.BODY.lineHeight)
        }
        flow.advance(S.SECTION_GAP - 4f)
    }

    // --- Tableau -----------------------------------------------------------------------------

    private fun table(flow: PageFlow, c: ReceiptPdfContent, table: PdfTable, m: PdfTextMeasurer) {
        val labelWidth = S.CONTENT_WIDTH - QTY_WIDTH - UNIT_WIDTH - TOTAL_WIDTH
        val qtyRight = S.MARGIN_X + labelWidth + QTY_WIDTH - CELL_PAD
        val unitRight = S.MARGIN_X + labelWidth + QTY_WIDTH + UNIT_WIDTH - CELL_PAD
        val totalRight = RIGHT - CELL_PAD
        val headerHeight = S.TABLE_HEADER.lineHeight + 8f

        fun drawHeader() {
            val y = flow.y
            flow.add(PdfOp.Rect(S.MARGIN_X, y, S.CONTENT_WIDTH, headerHeight, S.FILL))
            val baseY = y + 4f
            flow.add(textOp(S.MARGIN_X + CELL_PAD, baseY, c.labels.designation, S.TABLE_HEADER))
            flow.add(textOp(qtyRight, baseY, c.labels.quantity, S.TABLE_HEADER, PdfAlign.RIGHT))
            flow.add(textOp(unitRight, baseY, c.labels.unitPrice, S.TABLE_HEADER, PdfAlign.RIGHT))
            flow.add(textOp(totalRight, baseY, c.labels.total, S.TABLE_HEADER, PdfAlign.RIGHT))
            flow.advance(headerHeight)
        }

        table.rows.forEachIndexed { index, row ->
            val labelLines = PdfTextWrapper.wrap(row.label, labelWidth - 2 * CELL_PAD, S.BODY, m)
            val rowHeight = labelLines.size * S.BODY.lineHeight + 8f
            val firstRowNeeds = if (index == 0) headerHeight else 0f
            val openedPage = flow.ensure(firstRowNeeds + rowHeight)
            if (index == 0 || openedPage) drawHeader()
            val y = flow.y
            val baseY = y + 4f
            lines(flow, labelLines, S.MARGIN_X + CELL_PAD, baseY, S.BODY)
            flow.add(textOp(qtyRight, baseY, row.quantity, S.BODY, PdfAlign.RIGHT))
            flow.add(textOp(unitRight, baseY, row.unitPrice, S.BODY, PdfAlign.RIGHT))
            flow.add(textOp(totalRight, baseY, row.lineTotal, S.BODY, PdfAlign.RIGHT))
            flow.advance(rowHeight)
            flow.add(PdfOp.Line(S.MARGIN_X, flow.y, RIGHT, flow.y, S.RULE, 0.5f))
        }
        flow.advance(8f)

        if (table.summary.isNotEmpty()) {
            val height = table.summary.size * S.BODY.lineHeight + 14f
            flow.ensure(height)
            table.summary.forEach { row ->
                flow.add(textOp(RIGHT - 200f, flow.y, row.label, S.BODY))
                flow.add(textOp(totalRight, flow.y, row.value, S.BODY, PdfAlign.RIGHT))
                flow.advance(S.BODY.lineHeight)
            }
            flow.advance(14f)
        }
    }

    // --- Total et paiement -------------------------------------------------------------------

    private fun total(flow: PageFlow, c: ReceiptPdfContent, m: PdfTextMeasurer) {
        val boxHeight = S.LABEL.lineHeight + S.TOTAL_AMOUNT.lineHeight + 22f
        val detailsHeight = c.details.size * S.BODY.lineHeight + 8f
        flow.ensure(boxHeight + 10f + detailsHeight)
        val top = flow.y
        flow.add(PdfOp.Rect(S.MARGIN_X, top, S.CONTENT_WIDTH, boxHeight, S.FILL))
        flow.add(PdfOp.Rect(S.MARGIN_X, top, 4f, boxHeight, S.ACCENT))
        flow.add(textOp(S.MARGIN_X + 16f, top + 10f, c.labels.totalPaid, S.LABEL))
        flow.add(textOp(S.MARGIN_X + 16f, top + 10f + S.LABEL.lineHeight + 2f, c.totalText, S.TOTAL_AMOUNT))
        flow.advance(boxHeight + 10f)
        c.details.forEach { row ->
            val valueLines = PdfTextWrapper.wrap(row.value, S.CONTENT_WIDTH - 160f, S.BODY_BOLD, m)
            flow.add(textOp(S.MARGIN_X, flow.y, row.label, S.BODY))
            val end = lines(flow, valueLines, S.MARGIN_X + 150f, flow.y, S.BODY_BOLD)
            flow.advance(maxOf(end - flow.y, S.BODY.lineHeight))
        }
        flow.advance(S.SECTION_GAP)
    }

    // --- Signature ---------------------------------------------------------------------------

    private fun signature(flow: PageFlow, c: ReceiptPdfContent, m: PdfTextMeasurer) {
        flow.ensure(SIGNATURE_HEIGHT)
        val top = flow.y
        val half = S.CONTENT_WIDTH / 2f
        if (c.issuedByName != null || c.issuedByTitle != null) {
            var y = lines(flow, listOf(c.labels.issuedBy), S.MARGIN_X, top, S.LABEL) + 2f
            c.issuedByName?.let { y = lines(flow, PdfTextWrapper.wrap(it, half - 12f, S.BODY_BOLD, m), S.MARGIN_X, y, S.BODY_BOLD) }
            c.issuedByTitle?.let { lines(flow, PdfTextWrapper.wrap(it, half - 12f, S.BODY, m), S.MARGIN_X, y, S.BODY) }
        }
        val signX = S.MARGIN_X + half + 12f
        flow.add(textOp(signX, top, c.labels.signature, S.LABEL))
        flow.add(PdfOp.Line(signX, top + SIGNATURE_HEIGHT - 8f, RIGHT, top + SIGNATURE_HEIGHT - 8f, S.RULE, 0.8f))
        flow.advance(SIGNATURE_HEIGHT)
    }

    // --- Pied de page ------------------------------------------------------------------------

    private fun footers(flow: PageFlow, c: ReceiptPdfContent): List<PdfPagePlan> {
        val pages = flow.toPlans()
        pages.forEachIndexed { index, ops ->
            val baseline = S.FOOTER_BASELINE
            ops += PdfOp.Line(S.MARGIN_X, baseline - 14f, RIGHT, baseline - 14f, S.RULE, 0.5f)
            ops += PdfOp.Text(S.MARGIN_X, baseline, "${c.labels.numberPrefix} ${c.number}", S.FOOTER)
            ops += PdfOp.Text(RIGHT, baseline, "${c.labels.page} ${index + 1} / ${pages.size}", S.FOOTER, PdfAlign.RIGHT)
        }
        return pages.map { PdfPagePlan(it) }
    }

    // --- Utilitaires -------------------------------------------------------------------------

    private fun textOp(x: Float, topY: Float, text: String, style: PdfTextStyle, align: PdfAlign = PdfAlign.LEFT) =
        PdfOp.Text(x, topY + style.sizePt, text, style, align)

    /** Écrit [textLines] à partir de [topY] sans toucher au curseur ; retourne le Y suivant. */
    private fun lines(flow: PageFlow, textLines: List<String>, x: Float, topY: Float, style: PdfTextStyle, align: PdfAlign = PdfAlign.LEFT): Float {
        var y = topY
        textLines.forEach { line ->
            if (line.isNotEmpty()) flow.add(textOp(x, y, line, style, align))
            y += style.lineHeight
        }
        return y
    }
}
