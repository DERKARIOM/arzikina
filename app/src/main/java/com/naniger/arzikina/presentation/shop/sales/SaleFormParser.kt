package com.naniger.arzikina.presentation.shop.sales

import com.naniger.arzikina.domain.model.ReceiptDiscount
import com.naniger.arzikina.util.Money
import java.math.BigDecimal

/** Type de remise choisi dans le panier. Ordre = ordre des puces à l'écran. */
enum class DiscountMode { NONE, PERCENT, FIXED }

/** Résultat de la lecture du champ de remise. */
sealed interface DiscountParse {
    data class Valid(val discount: ReceiptDiscount) : DiscountParse
    data object Invalid : DiscountParse
}

/**
 * Lecture PURE (sans Android) des champs de remise du panier, testée sur JVM.
 *
 * Un champ vide est valide (aucune remise). Le pourcentage accepte au plus 2 décimales, de 0 à 100 %,
 * et se convertit en points de base (7,5 % → 750) ; le montant fixe est lu comme un montant monétaire.
 * La cohérence avec le sous-total (remise plafonnée) est du ressort de `SaleCalculator`.
 */
object SaleFormParser {

    private const val MAX_PERCENT_DECIMALS = 2
    private val ONE_HUNDRED = BigDecimal(100)

    fun parseDiscount(mode: DiscountMode, input: String): DiscountParse {
        if (mode == DiscountMode.NONE || input.isBlank()) return DiscountParse.Valid(ReceiptDiscount.None)
        return when (mode) {
            DiscountMode.PERCENT -> parseBasisPoints(input)
                ?.let { DiscountParse.Valid(ReceiptDiscount.Percent(it)) }
                ?: DiscountParse.Invalid
            DiscountMode.FIXED -> Money.parseToMinorUnits(input)
                ?.takeIf { it >= 0L }
                ?.let { DiscountParse.Valid(ReceiptDiscount.Fixed(it)) }
                ?: DiscountParse.Invalid
            DiscountMode.NONE -> DiscountParse.Valid(ReceiptDiscount.None)
        }
    }

    /** « 7,5 » → 750 ; `null` si illisible, négatif, au-dessus de 100 ou à plus de 2 décimales. */
    fun parseBasisPoints(input: String): Int? {
        val cleaned = input.filterNot { it.isWhitespace() || Character.isSpaceChar(it) }
            .removeSuffix("%")
            .replace(',', '.')
        if (cleaned.isEmpty()) return null
        val value = cleaned.toBigDecimalOrNull() ?: return null
        if (value.signum() < 0 || value > ONE_HUNDRED) return null
        if (value.stripTrailingZeros().scale() > MAX_PERCENT_DECIMALS) return null
        return value.movePointRight(2).setScale(0).intValueExact()
    }
}
