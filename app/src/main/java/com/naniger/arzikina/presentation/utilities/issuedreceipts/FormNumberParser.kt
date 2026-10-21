package com.naniger.arzikina.presentation.utilities.issuedreceipts

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Lecture des nombres décimaux saisis (quantités, pourcentages) : virgule ou point, espaces
 * ignorés, arrondi à [scale] décimales. Logique pure, testée.
 */
object FormNumberParser {

    private val decimal = Regex("^(\\d+(\\.\\d*)?|\\.\\d+)$")
    private const val MAX_CHARS = 15

    /** « 2,5 » avec `scale = 3` → 2500 ; `null` si ce n'est pas un nombre positif ou nul lisible. */
    fun parseScaled(input: String, scale: Int): Long? {
        val text = input.filterNot { it.isWhitespace() || Character.isSpaceChar(it) }.replace(',', '.')
        if (text.isEmpty() || text.length > MAX_CHARS || !decimal.matches(text)) return null
        return BigDecimal(text).movePointRight(scale).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    /** Quantité en millièmes (« 2,5 » → 2500). */
    fun quantityMilli(input: String): Long? = parseScaled(input, 3)

    /** Pourcentage en points de base (« 7,5 » → 750). */
    fun basisPoints(input: String): Int? = parseScaled(input, 2)?.takeIf { it <= Int.MAX_VALUE }?.toInt()

    /** Inverse de [parseScaled] pour pré-remplir un champ : 2500 (scale 3) → « 2,5 ». */
    fun format(value: Long, scale: Int): String =
        BigDecimal(value).movePointLeft(scale).stripTrailingZeros().toPlainString().replace('.', ',')
}
