package com.naniger.arzikina.domain.issuedreceipt.pdf

import com.naniger.arzikina.domain.model.PaymentMethod

/**
 * Textes fixes du PDF. Le document est indépendant de la langue de l'interface au moment de
 * l'affichage : la langue est choisie à la génération (français par défaut, anglais pris en charge).
 */
data class ReceiptPdfLabels(
    val title: String,
    val numberPrefix: String,
    val date: String,
    val customer: String,
    val phone: String,
    val email: String,
    val taxId: String,
    val description: String,
    val designation: String,
    val quantity: String,
    val unitPrice: String,
    val total: String,
    val subtotal: String,
    val discount: String,
    val tax: String,
    val totalPaid: String,
    val paymentMethod: String,
    val reference: String,
    val notes: String,
    val issuedBy: String,
    val signature: String,
    val page: String,
    private val methods: Map<PaymentMethod, String>
) {
    fun methodName(method: PaymentMethod): String = methods.getValue(method)

    companion object {
        fun forLanguage(language: String): ReceiptPdfLabels = if (language.lowercase().startsWith("en")) EN else FR

        val FR = ReceiptPdfLabels(
            title = "REÇU DE PAIEMENT", numberPrefix = "N°", date = "Date", customer = "CLIENT",
            phone = "Tél.", email = "E-mail", taxId = "ID fiscal", description = "DESCRIPTION",
            designation = "Désignation", quantity = "Qté", unitPrice = "Prix unitaire", total = "Total",
            subtotal = "Sous-total", discount = "Remise", tax = "Taxe", totalPaid = "TOTAL PAYÉ",
            paymentMethod = "Mode de paiement", reference = "Référence", notes = "OBSERVATIONS",
            issuedBy = "Émis par", signature = "Signature", page = "Page",
            methods = mapOf(
                PaymentMethod.CASH to "Espèces", PaymentMethod.MOBILE_MONEY to "Mobile Money",
                PaymentMethod.BANK_TRANSFER to "Virement bancaire", PaymentMethod.CARD to "Carte",
                PaymentMethod.OTHER to "Autre"
            )
        )

        val EN = ReceiptPdfLabels(
            title = "PAYMENT RECEIPT", numberPrefix = "No.", date = "Date", customer = "CUSTOMER",
            phone = "Phone", email = "E-mail", taxId = "Tax ID", description = "DESCRIPTION",
            designation = "Item", quantity = "Qty", unitPrice = "Unit price", total = "Total",
            subtotal = "Subtotal", discount = "Discount", tax = "Tax", totalPaid = "TOTAL PAID",
            paymentMethod = "Payment method", reference = "Reference", notes = "NOTES",
            issuedBy = "Issued by", signature = "Signature", page = "Page",
            methods = mapOf(
                PaymentMethod.CASH to "Cash", PaymentMethod.MOBILE_MONEY to "Mobile Money",
                PaymentMethod.BANK_TRANSFER to "Bank transfer", PaymentMethod.CARD to "Card",
                PaymentMethod.OTHER to "Other"
            )
        )
    }
}
