package com.naniger.arzikina.domain.issuedreceipt.pdf

/** Police standard du PDF (aucune police externe : le document reste autonome). */
enum class PdfFont { REGULAR, BOLD }

/** Style de texte : taille en points (1 pt = 1/72 pouce), couleur ARGB. */
data class PdfTextStyle(val sizePt: Float, val font: PdfFont, val color: Int) {
    val lineHeight: Float get() = sizePt * ReceiptPdfSpec.LINE_SPACING
}

enum class PdfAlign { LEFT, RIGHT }

/**
 * Mesure du texte, fournie par la plateforme (Android : `Paint`). Le moteur de mise en page n'en
 * dépend que par cette interface, ce qui le rend testable sans Android.
 */
fun interface PdfTextMeasurer {
    fun width(text: String, style: PdfTextStyle): Float
}

/** Instruction de dessin, en points depuis le coin haut-gauche de la page. */
sealed interface PdfOp {
    /** [x] est le bord gauche (ou droit si [align] = RIGHT) ; [baselineY] la ligne de base du texte. */
    data class Text(val x: Float, val baselineY: Float, val text: String, val style: PdfTextStyle, val align: PdfAlign = PdfAlign.LEFT) : PdfOp

    data class Rect(val x: Float, val y: Float, val width: Float, val height: Float, val fillColor: Int) : PdfOp

    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Int, val widthPt: Float) : PdfOp

    /** Emplacement du logo (ratio d'origine déjà respecté par le moteur). */
    data class Logo(val x: Float, val y: Float, val width: Float, val height: Float) : PdfOp

    /** Emplacement de la signature manuscrite (ratio d'origine déjà respecté par le moteur). */
    data class Signature(val x: Float, val y: Float, val width: Float, val height: Float) : PdfOp
}

/** Contenu dessiné d'une page. */
data class PdfPagePlan(val ops: List<PdfOp>)
