package com.naniger.arzikina.domain.model

/**
 * Version allégée d'un [IssuedReceipt] pour la LISTE d'historique : ne contient ni le détail de
 * tarification ni l'instantané de l'émetteur, donc aucun JSON à décoder pour afficher des centaines
 * de lignes.
 */
data class IssuedReceiptSummary(
    val id: Long,
    val number: String,
    val customerName: String,
    val issuedAt: Long,
    val amountMinor: Long,
    val currencyCode: String,
    val hasPdf: Boolean
)

/** Filtre de l'historique : [query] cherche dans le numéro, le client et la description ; [fromMillis]/[toMillis] bornent la date d'émission (inclusives). */
data class IssuedReceiptFilter(
    val query: String = "",
    val fromMillis: Long? = null,
    val toMillis: Long? = null
)
