package com.naniger.arzikina.domain.currency

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * Service UNIQUE de formatage des montants affichés (« 10 000 FCFA », « 1 234,50 € »).
 *
 * Règles :
 * - [minorUnits] est le montant STOCKÉ (unités mineures, facteur fixe [MINOR_UNITS_PER_MAJOR]) ;
 *   le formatage ne le modifie jamais et ne convertit jamais de devise.
 * - Séparateur de milliers : espace, séparateur décimal : virgule, quelle que soit la langue de
 *   l'interface ([AMOUNT_LOCALE]) — un montant ne change pas d'apparence avec la langue.
 * - Les décimales ne sont affichées que si elles sont réellement non nulles : aucune information
 *   n'est perdue, y compris pour XOF/XAF/GNF si un montant a des centimes hérités.
 * - Le symbole est placé en suffixe, séparé par une espace.
 */
object CurrencyFormatter {

    /** Doit rester identique à `Money.MINOR_UNITS_PER_MAJOR` (qui y renverra à l'étape suivante). */
    const val MINOR_UNITS_PER_MAJOR = 100

    val AMOUNT_LOCALE: Locale = Locale.FRENCH

    /** Montant seul, sans devise (« 10 000 », « 10 000,50 »). */
    fun formatNumber(minorUnits: Long): String {
        val hasCents = minorUnits % MINOR_UNITS_PER_MAJOR != 0L
        val format = NumberFormat.getNumberInstance(AMOUNT_LOCALE).apply {
            minimumFractionDigits = if (hasCents) 2 else 0
            maximumFractionDigits = if (hasCents) 2 else 0
        }
        val major = BigDecimal(minorUnits).divide(BigDecimal(MINOR_UNITS_PER_MAJOR))
        return format.format(major).normalizeSpaces()
    }

    /** Montant avec le symbole de [currencyCode] (« 10 000 FCFA »). */
    fun format(minorUnits: Long, currencyCode: String): String =
        "${formatNumber(minorUnits)} ${symbolOf(currencyCode)}"

    /** Symbole d'affichage, ou code ISO si la devise est inconnue. */
    fun symbolOf(currencyCode: String): String = CatalogLookup.symbol(currencyCode)

    /** Les formateurs de locale utilisent des espaces insécables (U+202F/U+00A0) : on les ramène à une espace normale. */
    private fun String.normalizeSpaces(): String = replace(' ', ' ').replace(' ', ' ')

    private object CatalogLookup {
        fun symbol(code: String): String = CurrencyCatalog.infoOrFallback(code).symbol
    }
}
