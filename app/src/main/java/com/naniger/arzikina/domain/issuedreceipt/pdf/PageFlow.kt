package com.naniger.arzikina.domain.issuedreceipt.pdf

/**
 * Curseur vertical qui remplit les pages une à une : [ensure] ouvre une nouvelle page quand le
 * bloc suivant ne tient pas. Utilisé uniquement par [ReceiptPdfLayoutEngine].
 */
internal class PageFlow {
    private val pages = mutableListOf(mutableListOf<PdfOp>())
    var y: Float = ReceiptPdfSpec.MARGIN_TOP
        private set

    val remaining: Float get() = ReceiptPdfSpec.CONTENT_BOTTOM - y
    val pageCount: Int get() = pages.size

    fun add(op: PdfOp) {
        pages.last() += op
    }

    fun advance(delta: Float) {
        y += delta
    }

    /** Ouvre une page si [height] ne tient pas ; retourne `true` dans ce cas. Ne fait rien sur une page vierge. */
    fun ensure(height: Float): Boolean {
        if (height <= remaining || y == ReceiptPdfSpec.MARGIN_TOP) return false
        newPage()
        return true
    }

    fun newPage() {
        pages += mutableListOf<PdfOp>()
        y = ReceiptPdfSpec.MARGIN_TOP
    }

    fun toPlans(): List<MutableList<PdfOp>> = pages
}
