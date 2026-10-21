package com.naniger.arzikina.domain.issuedreceipt.pdf

/**
 * Spécification de mise en page du reçu PDF, PARTAGÉE avec la console Web (mêmes marges, tailles et
 * couleurs, pour un rendu cohérent entre Android et Web). Unités : points (1/72 pouce), A4 portrait.
 *
 * Le vert Arzikina n'est utilisé que pour les filets et pastilles : le reçu reste lisible, et
 * professionnel, imprimé en noir et blanc.
 */
object ReceiptPdfSpec {
    const val PAGE_WIDTH = 595f
    const val PAGE_HEIGHT = 842f
    const val MARGIN_X = 44f
    const val MARGIN_TOP = 44f

    /** Bas de la zone de contenu : réserve la place du pied de page. */
    const val CONTENT_BOTTOM = PAGE_HEIGHT - 64f
    const val FOOTER_BASELINE = PAGE_HEIGHT - 34f
    const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN_X

    const val LINE_SPACING = 1.3f
    const val SECTION_GAP = 16f

    const val LOGO_MAX_WIDTH = 120f
    const val LOGO_MAX_HEIGHT = 64f

    /**
     * Cadre de la signature manuscrite, dans la moitié droite du bloc « Signature » : l'image tient
     * sous le libellé (décalage [SIGNATURE_TOP_OFFSET]), au-dessus du filet, et est centrée. Sa hauteur
     * maximale est [SIGNATURE_MAX_HEIGHT] ; sa largeur, celle de la moitié droite.
     */
    const val SIGNATURE_TOP_OFFSET = 14f
    const val SIGNATURE_BOTTOM_GAP = 10f
    const val SIGNATURE_MAX_HEIGHT = 60f

    val ACCENT: Int = 0xFF42B998.toInt()
    val TEXT: Int = 0xFF111827.toInt()
    val MUTED: Int = 0xFF6B7280.toInt()
    val RULE: Int = 0xFFD1D5DB.toInt()
    val FILL: Int = 0xFFF3F4F6.toInt()

    val TITLE = PdfTextStyle(17f, PdfFont.BOLD, TEXT)
    val BUSINESS = PdfTextStyle(15f, PdfFont.BOLD, TEXT)
    val BODY = PdfTextStyle(10f, PdfFont.REGULAR, TEXT)
    val BODY_BOLD = PdfTextStyle(10f, PdfFont.BOLD, TEXT)
    val NAME = PdfTextStyle(12f, PdfFont.BOLD, TEXT)
    val CONTACT = PdfTextStyle(9f, PdfFont.REGULAR, MUTED)
    val LABEL = PdfTextStyle(8f, PdfFont.BOLD, MUTED)
    val TABLE_HEADER = PdfTextStyle(9f, PdfFont.BOLD, TEXT)
    val TOTAL_AMOUNT = PdfTextStyle(20f, PdfFont.BOLD, TEXT)
    val FOOTER = PdfTextStyle(8f, PdfFont.REGULAR, MUTED)
}
