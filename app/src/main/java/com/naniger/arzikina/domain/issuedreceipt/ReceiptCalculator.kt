package com.naniger.arzikina.domain.issuedreceipt

import com.naniger.arzikina.domain.model.ReceiptDiscount
import com.naniger.arzikina.domain.model.ReceiptLine
import com.naniger.arzikina.domain.model.ReceiptPricing
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Calcul des totaux d'un reçu détaillé. Logique pure (aucune dépendance Android), testée
 * unitairement. Arithmétique en [BigDecimal] puis arrondi « demi supérieur » à l'unité mineure à
 * chaque étape, jamais de flottant.
 *
 * Ordre : sous-total (somme des lignes) → remise (plafonnée au sous-total) → base taxable → taxe
 * sur la base taxable → total.
 */
object ReceiptCalculator {

    /** Plafond d'un montant : au-delà, [totalsOrNull] renvoie `null` (évite tout débordement de [Long]). */
    const val MAX_AMOUNT_MINOR: Long = 99_999_999_999_999L

    const val QUANTITY_UNIT_MILLI: Long = 1_000L
    const val MAX_TAX_BASIS_POINTS: Int = 10_000
    private const val BASIS_POINTS_DIVISOR = 10_000L

    /** Total d'une ligne : `prix unitaire × quantité`, arrondi à l'unité mineure. `null` si hors plafond. */
    fun lineTotalOrNull(line: ReceiptLine): Long? {
        if (line.quantityMilli <= 0 || line.unitPriceMinor < 0) return null
        val raw = BigDecimal(line.unitPriceMinor)
            .multiply(BigDecimal(line.quantityMilli))
            .divide(BigDecimal(QUANTITY_UNIT_MILLI), 0, RoundingMode.HALF_UP)
        return raw.takeIf { it <= BigDecimal(MAX_AMOUNT_MINOR) }?.toLong()
    }

    /** Totaux complets, ou `null` si une valeur est invalide ou dépasse [MAX_AMOUNT_MINOR]. */
    fun totalsOrNull(pricing: ReceiptPricing): ReceiptTotals? {
        if (pricing.taxBasisPoints !in 0..MAX_TAX_BASIS_POINTS) return null
        var subtotal = 0L
        for (line in pricing.lines) {
            val lineTotal = lineTotalOrNull(line) ?: return null
            subtotal += lineTotal
            if (subtotal > MAX_AMOUNT_MINOR) return null
        }
        val discount = when (val d = pricing.discount) {
            ReceiptDiscount.None -> 0L
            is ReceiptDiscount.Percent -> {
                if (d.basisPoints !in 0..MAX_TAX_BASIS_POINTS) return null
                percentOf(subtotal, d.basisPoints)
            }
            is ReceiptDiscount.Fixed -> {
                if (d.amountMinor < 0) return null
                d.amountMinor.coerceAtMost(subtotal)
            }
        }
        val taxable = subtotal - discount
        val tax = percentOf(taxable, pricing.taxBasisPoints)
        val total = taxable + tax
        if (total > MAX_AMOUNT_MINOR) return null
        return ReceiptTotals(subtotal, discount, taxable, tax, total)
    }

    private fun percentOf(amountMinor: Long, basisPoints: Int): Long =
        BigDecimal(amountMinor)
            .multiply(BigDecimal(basisPoints))
            .divide(BigDecimal(BASIS_POINTS_DIVISOR), 0, RoundingMode.HALF_UP)
            .toLong()
}
