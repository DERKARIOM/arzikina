package com.naniger.arzikina.presentation.utilities.issuedreceipts

import com.naniger.arzikina.domain.model.PaymentMethod

/** Ligne de détail saisie : textes bruts ; [id] stable pour retrouver la ligne dans l'écran. */
data class LineInput(
    val id: Long,
    val label: String = "",
    val quantityInput: String = "1",
    val unitPriceInput: String = ""
)

/** Type de remise saisi. */
enum class DiscountMode { NONE, PERCENT, FIXED }

/**
 * Saisie brute du formulaire de reçu (textes tels que tapés). Sans dépendance Android : la
 * conversion vers le modèle de domaine ([IssuedReceiptDraftFactory]) est testée unitairement.
 *
 * [numberInput] vide = numéro généré automatiquement. [logoFileName] est le logo effectivement
 * affiché (celui du profil, ou aucun) : il est figé dans le reçu.
 *
 * Reçu détaillé ([detailed]) : le montant n'est plus saisi, il est CALCULÉ depuis [lines], la
 * remise ([discountMode]/[discountInput]) et la taxe ([taxInput], en %).
 */
data class IssuedReceiptFormInput(
    val businessName: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val taxId: String = "",
    val logoFileName: String? = null,
    val issuedByName: String = "",
    val issuedByTitle: String = "",
    val numberInput: String = "",
    val issuedAt: Long = 0L,
    val customerName: String = "",
    val customerPhone: String = "",
    val description: String = "",
    val amountInput: String = "",
    val currencyCode: String = "XOF",
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val reference: String = "",
    val notes: String = "",
    val detailed: Boolean = false,
    val lines: List<LineInput> = emptyList(),
    val discountMode: DiscountMode = DiscountMode.NONE,
    val discountInput: String = "",
    val taxInput: String = ""
)
