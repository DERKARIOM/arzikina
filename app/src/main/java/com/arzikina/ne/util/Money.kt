package com.arzikina.ne.util

import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.domain.model.SupportedCurrency
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

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

    /**
     * Format BRUT et ré-analysable (voir [parseToMinorUnits]) : toujours 2
     * décimales, jamais de séparateur de milliers. Réservé aux champs de
     * saisie éditables (Montant, Limite de budget, Solde initial...) — PAS à
     * l'affichage (voir [format]/[formatAmount]), qui a des règles différentes
     * et incompatibles avec une ré-analyse (séparateur de milliers, décimales
     * masquées si nulles).
     */
    fun formatMajorUnits(minorUnits: Long): String {
        val major = BigDecimal(minorUnits).divide(BigDecimal(MINOR_UNITS_PER_MAJOR))
        return major.setScale(2, RoundingMode.HALF_UP).toPlainString()
    }

    /** Conversion numérique (non formatée) en unité majeure, pour les axes de graphiques. */
    fun toMajorDouble(minorUnits: Long): Double = minorUnits.toDouble() / MINOR_UNITS_PER_MAJOR

    /** Formate un [CurrencyAmount] pour l'AFFICHAGE, avec le symbole de sa devise
     * et un séparateur de milliers (ex. "10 000 F CFA", ou "10 000,50 F CFA" si le
     * montant a réellement des centimes — voir [formatAmount]). */
    fun format(amount: CurrencyAmount): String {
        val symbol = SupportedCurrency.entries.firstOrNull { it.code == amount.currencyCode }?.symbol
            ?: amount.currencyCode
        return "${formatAmount(amount.amountMinor)} $symbol"
    }

    /**
     * Formate un montant pour l'AFFICHAGE, sans devise : séparateur de milliers
     * (espace, locale française), et décimales affichées SEULEMENT si
     * réellement non nulles ("10 000" plutôt que "10 000,00", mais bien
     * "10 000,50" si le montant a de vrais centimes) — pour ne perdre aucune
     * information sur les devises qui en utilisent réellement (EUR, USD), tout
     * en restant lisible pour le Franc CFA, qui n'en a pas dans l'usage
     * courant. Utilisé par [format] ; exposé aussi pour les rares affichages
     * sans devise résolue (voir TransactionItemBinder).
     */
    fun formatAmount(minorUnits: Long): String {
        val hasCents = minorUnits % MINOR_UNITS_PER_MAJOR != 0L
        val numberFormat = NumberFormat.getNumberInstance(Locale.FRENCH).apply {
            minimumFractionDigits = if (hasCents) 2 else 0
            maximumFractionDigits = if (hasCents) 2 else 0
        }
        val major = BigDecimal(minorUnits).divide(BigDecimal(MINOR_UNITS_PER_MAJOR))
        return numberFormat.format(major)
    }
}
