package com.naniger.arzikina.presentation.utilities.issuedreceipts

import com.naniger.arzikina.domain.issuedreceipt.IssuedReceiptError
import com.naniger.arzikina.domain.issuedreceipt.IssuedReceiptValidator
import com.naniger.arzikina.domain.issuedreceipt.ReceiptCalculator
import com.naniger.arzikina.domain.issuedreceipt.ReceiptTotals
import com.naniger.arzikina.domain.model.IssuedReceipt
import com.naniger.arzikina.domain.model.IssuerProfile
import com.naniger.arzikina.domain.model.IssuerSnapshot
import com.naniger.arzikina.domain.model.ReceiptDiscount
import com.naniger.arzikina.domain.model.ReceiptLine
import com.naniger.arzikina.domain.model.ReceiptPricing
import com.naniger.arzikina.util.Money

/** Résultat de la validation d'un formulaire. */
sealed interface IssuedReceiptDraft {
    data class Valid(val receipt: IssuedReceipt, val profile: IssuerProfile) : IssuedReceiptDraft
    data class Invalid(val errors: Set<IssuedReceiptError>) : IssuedReceiptDraft
}

/**
 * Convertit la saisie ([IssuedReceiptFormInput]) en modèle de domaine. Logique pure, testée. Deux
 * usages : [toReceipt] SANS validation (aperçu en temps réel, qui doit afficher un reçu même
 * incomplet) et [validate] (enregistrement, qui s'appuie sur [IssuedReceiptValidator], la seule
 * source des règles).
 */
object IssuedReceiptDraftFactory {

    /** Reçu tel que saisi, même incomplet ; un montant illisible vaut 0. Jamais d'exception. */
    fun toReceipt(input: IssuedReceiptFormInput): IssuedReceipt {
        val pricing = pricingOf(input)
        val total = pricing?.let { ReceiptCalculator.totalsOrNull(it)?.totalMinor }
        val amount = if (input.detailed) total ?: 0L else Money.parseToMinorUnits(input.amountInput) ?: 0L
        return build(input, pricing, amount)
    }

    /**
     * Détail de tarification saisi, ou `null` si le reçu n'est pas détaillé. Une valeur illisible
     * devient une valeur INVALIDE (quantité 0, prix/remise/taxe négatifs) que le validateur du
     * domaine signale : jamais une valeur par défaut silencieuse.
     */
    fun pricingOf(input: IssuedReceiptFormInput): ReceiptPricing? {
        if (!input.detailed) return null
        val lines = input.lines.map { line ->
            ReceiptLine(
                label = line.label.trim(),
                quantityMilli = FormNumberParser.quantityMilli(line.quantityInput) ?: 0L,
                unitPriceMinor = Money.parseToMinorUnits(line.unitPriceInput) ?: INVALID
            )
        }
        val discount = when (input.discountMode) {
            DiscountMode.NONE -> ReceiptDiscount.None
            DiscountMode.PERCENT -> ReceiptDiscount.Percent(
                if (input.discountInput.isBlank()) 0 else FormNumberParser.basisPoints(input.discountInput) ?: INVALID.toInt()
            )
            DiscountMode.FIXED -> ReceiptDiscount.Fixed(
                if (input.discountInput.isBlank()) 0L else Money.parseToMinorUnits(input.discountInput) ?: INVALID
            )
        }
        val tax = if (input.taxInput.isBlank()) 0 else FormNumberParser.basisPoints(input.taxInput) ?: INVALID.toInt()
        return ReceiptPricing(lines, discount, tax)
    }

    /** Totaux calculés d'un reçu détaillé (affichés en direct), `null` si non détaillé ou saisie invalide. */
    fun totalsOf(input: IssuedReceiptFormInput): ReceiptTotals? = pricingOf(input)?.let { ReceiptCalculator.totalsOrNull(it) }

    private fun build(input: IssuedReceiptFormInput, pricing: ReceiptPricing?, amount: Long): IssuedReceipt = IssuedReceipt(
        number = input.numberInput.trim(),
        issuedAt = input.issuedAt,
        customerName = input.customerName.trim(),
        customerPhone = input.customerPhone.cleaned(),
        description = input.description.trim(),
        amountMinor = amount,
        pricing = pricing,
        currencyCode = input.currencyCode,
        paymentMethod = input.paymentMethod,
        transactionReference = input.reference.cleaned(),
        notes = input.notes.cleaned(),
        issuedByName = input.issuedByName.cleaned(),
        issuedByTitle = input.issuedByTitle.cleaned(),
        issuer = IssuerSnapshot(
            businessName = input.businessName.trim(),
            address = input.address.trim(),
            phone = input.phone.trim(),
            email = input.email.cleaned(),
            taxId = input.taxId.cleaned(),
            logoFileName = input.logoFileName
        )
    )

    /** Saisie reconstruite depuis un reçu enregistré ([nextLineId] : prochain id libre pour les lignes). */
    data class EditableInput(val input: IssuedReceiptFormInput, val nextLineId: Long)

    /** Inverse de [toReceipt] : pré-remplit le formulaire pour MODIFIER un reçu. [firstLineId] : id de la première ligne. */
    fun inputFrom(receipt: IssuedReceipt, firstLineId: Long = 1L): EditableInput {
        val pricing = receipt.pricing
        val lines = pricing?.lines.orEmpty().mapIndexed { i, l ->
            LineInput(
                id = firstLineId + i,
                label = l.label,
                quantityInput = FormNumberParser.format(l.quantityMilli, 3),
                unitPriceInput = Money.formatForInput(l.unitPriceMinor)
            )
        }
        val (mode, discountText) = when (val d = pricing?.discount) {
            is ReceiptDiscount.Percent -> DiscountMode.PERCENT to FormNumberParser.format(d.basisPoints.toLong(), 2)
            is ReceiptDiscount.Fixed -> DiscountMode.FIXED to Money.formatForInput(d.amountMinor)
            else -> DiscountMode.NONE to ""
        }
        val input = IssuedReceiptFormInput(
            businessName = receipt.issuer.businessName,
            address = receipt.issuer.address,
            phone = receipt.issuer.phone,
            email = receipt.issuer.email.orEmpty(),
            taxId = receipt.issuer.taxId.orEmpty(),
            logoFileName = receipt.issuer.logoFileName,
            issuedByName = receipt.issuedByName.orEmpty(),
            issuedByTitle = receipt.issuedByTitle.orEmpty(),
            numberInput = receipt.number,
            issuedAt = receipt.issuedAt,
            customerName = receipt.customerName,
            customerPhone = receipt.customerPhone.orEmpty(),
            description = receipt.description,
            amountInput = if (pricing == null) Money.formatForInput(receipt.amountMinor) else "",
            currencyCode = receipt.currencyCode,
            paymentMethod = receipt.paymentMethod,
            reference = receipt.transactionReference.orEmpty(),
            notes = receipt.notes.orEmpty(),
            detailed = pricing != null,
            lines = lines,
            discountMode = mode,
            discountInput = discountText,
            taxInput = if (pricing != null && pricing.taxBasisPoints > 0) FormNumberParser.format(pricing.taxBasisPoints.toLong(), 2) else ""
        )
        return EditableInput(input, firstLineId + lines.size)
    }

    /** Profil à mémoriser pour les prochains reçus. */
    fun toProfile(input: IssuedReceiptFormInput): IssuerProfile = IssuerProfile(
        businessName = input.businessName.trim(),
        address = input.address.trim(),
        phone = input.phone.trim(),
        email = input.email.cleaned(),
        taxId = input.taxId.cleaned(),
        logoFileName = input.logoFileName,
        defaultIssuedByName = input.issuedByName.cleaned(),
        defaultIssuedByTitle = input.issuedByTitle.cleaned()
    )

    fun validate(input: IssuedReceiptFormInput): IssuedReceiptDraft {
        val errors = linkedSetOf<IssuedReceiptError>()
        // Un montant non vide mais illisible est signalé comme montant invalide, pas comme « 0 ».
        if (!input.detailed && input.amountInput.isNotBlank() && Money.parseToMinorUnits(input.amountInput) == null) {
            errors += IssuedReceiptError.AMOUNT_NOT_POSITIVE
        }
        val receipt = toReceipt(input)
        errors += IssuedReceiptValidator.validate(receipt)
        return if (errors.isEmpty()) IssuedReceiptDraft.Valid(receipt, toProfile(input)) else IssuedReceiptDraft.Invalid(errors)
    }

    private const val INVALID = -1L

    private fun String.cleaned(): String? = trim().takeIf { it.isNotEmpty() }
}
