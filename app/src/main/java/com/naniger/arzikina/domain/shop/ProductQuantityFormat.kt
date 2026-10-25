package com.naniger.arzikina.domain.shop

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * Formatage et lecture d'une quantité en milli-unités (voir [StockRules.QUANTITY_UNIT_MILLI]).
 *
 * Affichage : « 12 » pour une quantité entière, « 1,5 » pour 1 500 milli-unités ; jusqu'à 3 décimales,
 * séparateur de milliers espace, même locale que les montants (voir `Money.AMOUNT_LOCALE`) : un nombre
 * ne change pas d'apparence avec la langue de l'interface.
 */
object ProductQuantityFormat {

    private val LOCALE: Locale = Locale.FRENCH

    fun format(quantityMilli: Long): String {
        val value = BigDecimal(quantityMilli).movePointLeft(3)
        val format = NumberFormat.getNumberInstance(LOCALE).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 3
        }
        return format.format(value)
    }

    /** Valeur brute ré-analysable (champ de saisie) : « 12 » ou « 1,5 », sans séparateur de milliers. */
    fun formatForInput(quantityMilli: Long): String =
        BigDecimal(quantityMilli).movePointLeft(3).stripTrailingZeros().toPlainString().replace('.', ',')

    /**
     * Quantité saisie → milli-unités, ou `null` si invalide (vide, négative, lettres, plus de 3
     * décimales). Accepte la virgule ou le point et ignore les espaces (séparateurs de milliers).
     */
    fun parse(input: String): Long? {
        val cleaned = input.filterNot { it.isWhitespace() || Character.isSpaceChar(it) }.replace(',', '.')
        if (cleaned.isEmpty()) return null
        val value = cleaned.toBigDecimalOrNull() ?: return null
        if (value.signum() < 0 || value.stripTrailingZeros().scale() > 3) return null
        return runCatching { value.movePointRight(3).setScale(0).longValueExact() }.getOrNull()
    }
}
