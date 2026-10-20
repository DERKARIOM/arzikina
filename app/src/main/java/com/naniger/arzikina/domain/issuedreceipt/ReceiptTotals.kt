package com.naniger.arzikina.domain.issuedreceipt

/** Résultat du calcul d'un reçu détaillé (voir [ReceiptCalculator]), tout en unité mineure. */
data class ReceiptTotals(
    val subtotalMinor: Long,
    val discountMinor: Long,
    val taxableMinor: Long,
    val taxMinor: Long,
    val totalMinor: Long
)
