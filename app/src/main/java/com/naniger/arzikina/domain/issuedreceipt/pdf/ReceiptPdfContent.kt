package com.naniger.arzikina.domain.issuedreceipt.pdf

/** Dimensions d'origine du logo en pixels : seul le RATIO compte pour la mise en page. */
data class PdfLogo(val widthPx: Int, val heightPx: Int)

data class PdfKeyValue(val label: String, val value: String)

data class PdfTableRow(val label: String, val quantity: String, val unitPrice: String, val lineTotal: String)

/** Tableau de détail : [rows], puis [summary] (sous-total, remise, taxe) aligné à droite. */
data class PdfTable(val rows: List<PdfTableRow>, val summary: List<PdfKeyValue>)

/**
 * Contenu du reçu, DÉJÀ mis en forme (textes, montants, dates) : le moteur de mise en page ne fait
 * que le disposer. Construit par [ReceiptPdfContentBuilder].
 */
data class ReceiptPdfContent(
    val labels: ReceiptPdfLabels,
    val businessName: String,
    val contactLines: List<String>,
    val logo: PdfLogo?,
    val number: String,
    val dateText: String,
    val customerName: String,
    val customerPhone: String?,
    val description: String,
    val table: PdfTable?,
    val totalText: String,
    val details: List<PdfKeyValue>,
    val notes: String?,
    val issuedByName: String?,
    val issuedByTitle: String?
)
