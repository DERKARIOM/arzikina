package com.naniger.arzikina.data.issuedreceipt.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import com.naniger.arzikina.domain.issuedreceipt.pdf.PdfPagePlan
import com.naniger.arzikina.domain.issuedreceipt.pdf.ReceiptPdfSpec
import com.naniger.arzikina.util.pdf.PdfOpsPainter
import com.naniger.arzikina.util.pdf.PdfPaints
import java.io.OutputStream

/**
 * Dessine les pages calculées par `ReceiptPdfLayoutEngine` dans un VRAI document PDF
 * ([PdfDocument] : texte vectoriel sélectionnable, jamais une capture d'écran). Les coordonnées du
 * plan sont déjà en points PDF, aucune conversion. Le dessin lui-même est celui de l'aperçu
 * ([PdfOpsPainter]).
 */
internal class PdfCanvasRenderer(paints: PdfPaints) {

    private val painter = PdfOpsPainter(paints)

    fun render(pages: List<PdfPagePlan>, logo: Bitmap?, signature: Bitmap?, out: OutputStream) {
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, plan ->
                val info = PdfDocument.PageInfo.Builder(ReceiptPdfSpec.PAGE_WIDTH.toInt(), ReceiptPdfSpec.PAGE_HEIGHT.toInt(), index + 1).create()
                val page = document.startPage(info)
                try {
                    painter.draw(page.canvas, plan.ops, logo, signature)
                } finally {
                    document.finishPage(page)
                }
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
    }
}
