package com.arzikina.ne.util

import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.domain.model.SupportedCurrency
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Conversion entre la saisie utilisateur (unité majeure, ex. "1500.50") et le
 * stockage interne en unité mineure ([Long], voir [com.arzikina.ne.domain.model.Account]).
 *
 * Simplification actuelle : un facteur fixe de 100 est utilisé pour toutes
 * les devises, y compris celles sans sous-unité usuelle (ex. XOF). Ce sera
 * affiné avec la gestion complète des devises à l'étape "Paramètres" —
 * l'unité de stockage ([Long]) n'aura pas besoin de changer, seul ce
 * convertisseur évoluera.
 */
object Money {
    private const val MINOR_UNITS_PER_MAJOR = 100

    /** Retourne `null` si [input] n'est pas un nombre positif valide. */
    fun parseToMinorUnits(input: String): Long? {
        val normalized = input.trim().replace(',', '.')
        if (normalized.isEmpty()) return null
        val value = normalized.toBigDecimalOrNull() ?: return null
        if (value.signum() < 0) return null
        return value.multiply(BigDecimal(MINOR_UNITS_PER_MAJOR))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }

    fun formatMajorUnits(minorUnits: Long): String {
        val major = BigDecimal(minorUnits).divide(BigDecimal(MINOR_UNITS_PER_MAJOR))
        return major.setScale(2, RoundingMode.HALF_UP).toPlainString()
    }

    /** Conversion numérique (non formatée) en unité majeure, pour les axes de graphiques. */
    fun toMajorDouble(minorUnits: Long): Double = minorUnits.toDouble() / MINOR_UNITS_PER_MAJOR

    /** Formate un [CurrencyAmount] avec le symbole de sa devise (ex. "1 500,00 F CFA"). */
    fun format(amount: CurrencyAmount): String {
        val symbol = SupportedCurrency.entries.firstOrNull { it.code == amount.currencyCode }?.symbol
            ?: amount.currencyCode
        return "${formatMajorUnits(amount.amountMinor)} $symbol"
    }
}
