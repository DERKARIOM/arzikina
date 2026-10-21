package com.naniger.arzikina.domain.issuedreceipt.pdf

/**
 * Format des montants du PDF, en logique pure (aucune dépendance à `Locale`/`NumberFormat`, donc
 * identique sur tous les appareils et facile à reproduire côté Web) : « 10 000 XOF », « 1 500,50 EUR ».
 * Convention de l'application : séparateur décimal « , », milliers séparés par une espace
 * insécable ; la devise est affichée par son code ISO (aucun symbole absent de la police).
 */
object ReceiptAmountFormatter {

    private const val NBSP = ' '
    private const val MINOR_PER_MAJOR = 100L

    fun format(amountMinor: Long, currencyCode: String, showDecimals: Boolean): String =
        "${number(amountMinor, showDecimals)}$NBSP$currencyCode"

    fun number(amountMinor: Long, showDecimals: Boolean): String {
        val negative = amountMinor < 0
        val absolute = if (amountMinor == Long.MIN_VALUE) Long.MAX_VALUE else Math.abs(amountMinor)
        val grouped = group((absolute / MINOR_PER_MAJOR).toString())
        val text = if (showDecimals) "$grouped,${(absolute % MINOR_PER_MAJOR).toString().padStart(2, '0')}" else grouped
        return if (negative) "-$text" else text
    }

    /** Quantité en millièmes : 1000 → « 1 », 2500 → « 2,5 », 1250 → « 1,25 ». */
    fun quantity(quantityMilli: Long): String {
        val whole = quantityMilli / 1000
        val fraction = (quantityMilli % 1000).toString().padStart(3, '0').trimEnd('0')
        val groupedWhole = group(whole.toString())
        return if (fraction.isEmpty()) groupedWhole else "$groupedWhole,$fraction"
    }

    /** Points de base : 1800 → « 18 % », 750 → « 7,5 % », 5 → « 0,05 % ». */
    fun percent(basisPoints: Int): String {
        val whole = basisPoints / 100
        val fraction = (basisPoints % 100).toString().padStart(2, '0').trimEnd('0')
        return (if (fraction.isEmpty()) "$whole" else "$whole,$fraction") + "$NBSP%"
    }

    private fun group(digits: String): String {
        val out = StringBuilder()
        digits.forEachIndexed { index, char ->
            if (index > 0 && (digits.length - index) % 3 == 0) out.append(NBSP)
            out.append(char)
        }
        return out.toString()
    }
}
