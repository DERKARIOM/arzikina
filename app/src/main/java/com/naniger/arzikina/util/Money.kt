package com.naniger.arzikina.util

import com.naniger.arzikina.domain.model.CurrencyAmount
import com.naniger.arzikina.domain.model.SupportedCurrency
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Conversion entre la saisie utilisateur (unité majeure, ex. "1500.50") et le
 * stockage interne en unité mineure ([Long], voir [com.naniger.arzikina.domain.model.Account]).
 *
 * Simplification actuelle : un facteur fixe de 100 est utilisé pour toutes
 * les devises, y compris celles sans sous-unité usuelle (ex. XOF). Ce sera
 * affiné avec la gestion complète des devises à l'étape "Paramètres" —
 * l'unité de stockage ([Long]) n'aura pas besoin de changer, seul ce
 * convertisseur évoluera.
 */
object Money {
    /** Facteur unité majeure → mineure (voir la doc de la classe). Public pour les rares
     *  affichages qui partent d'un montant en unités majeures (ex. raccourcis « +1 000 »). */
    const val MINOR_UNITS_PER_MAJOR = 100

    /**
     * Locale de formatage de TOUS les montants, quelle que soit la langue de l'interface (décision
     * produit, chantier i18n) : « 10 000 F CFA » en français comme en anglais. Un montant ne doit
     * jamais changer d'apparence avec la langue, et [parseToMinorUnits] lit la virgule comme
     * séparateur décimal : « 10,000 » y serait compris comme dix. Seule cette constante est à
     * modifier si Arzikina adopte un jour un format de montant par langue.
     */
    val AMOUNT_LOCALE: Locale = Locale.FRENCH


    /**
     * Retourne `null` si [input] n'est pas un nombre positif valide.
     *
     * Tolère, en plus du séparateur décimal (`,` ou `.`), un séparateur de
     * milliers sous forme d'espace — espace normale, insécable (` `) ou
     * fine insécable (` `, utilisée par certaines implémentations de
     * `NumberFormat` pour la locale française). Ces espaces sont uniquement
     * des séparateurs visuels et sont retirés avant l'analyse : "10 000",
     * "10 000" et "10000" sont strictement équivalents ici. Permet de
     * coller directement un montant déjà formaté (voir [formatForInput]) ou
     * copié depuis un affichage (voir [formatAmount]).
     */
    fun parseToMinorUnits(input: String): Long? {
        val withoutThousandsSeparators = input.filterNot { it.isWhitespace() || Character.isSpaceChar(it) }
        val normalized = withoutThousandsSeparators.replace(',', '.')
        if (normalized.isEmpty()) return null
        val value = normalized.toBigDecimalOrNull() ?: return null
        if (value.signum() < 0) return null
        return value.multiply(BigDecimal(MINOR_UNITS_PER_MAJOR))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }

    /**
     * Format BRUT et ré-analysable (voir [parseToMinorUnits]) : toujours 2
     * décimales, jamais de séparateur de milliers. Ancien format de
     * préremplissage des champs de saisie éditables — remplacé par
     * [formatForInput] (voir sa doc), conservé pour compatibilité des appels
     * pas encore migrés (axes de graphiques, exports...).
     */
    fun formatMajorUnits(minorUnits: Long): String {
        val major = BigDecimal(minorUnits).divide(BigDecimal(MINOR_UNITS_PER_MAJOR))
        return major.setScale(2, RoundingMode.HALF_UP).toPlainString()
    }

    /**
     * Formate un montant pour un champ de saisie ÉDITABLE : séparateur de
     * milliers (espace normale, `' '`) et décimales affichées SEULEMENT si
     * réellement non nulles — mêmes règles visuelles que [formatAmount], mais
     * garanties ré-analysables par [parseToMinorUnits] (espace normale
     * uniquement, jamais de symbole de devise). Utilisé pour préremplir un
     * champ en mode édition (ex. "10 000,50" plutôt que l'ancien "10000.50"
     * de [formatMajorUnits]) ; partage son algorithme de regroupement avec le
     * formatage en direct pendant la frappe (voir `MoneyInputFormatter`) —
     * une seule implémentation du regroupement par milliers dans tout le
     * projet.
     */
    fun formatForInput(minorUnits: Long): String {
        val absMinor = kotlin.math.abs(minorUnits)
        val sign = if (minorUnits < 0) "-" else ""
        val majorPart = absMinor / MINOR_UNITS_PER_MAJOR
        val centsPart = absMinor % MINOR_UNITS_PER_MAJOR
        val groupedMajor = groupThousands(majorPart.toString())
        return if (centsPart != 0L) {
            "$sign$groupedMajor,${centsPart.toString().padStart(2, '0')}"
        } else {
            "$sign$groupedMajor"
        }
    }

    /**
     * Insère une espace normale tous les 3 chiffres en partant de la droite.
     * [digits] doit contenir uniquement des chiffres (pas de signe, pas de
     * séparateur décimal) — utilisé par [formatForInput] et, en Étape 2, par
     * le formateur de saisie en direct : c'est l'UNIQUE algorithme de
     * regroupement par milliers du projet.
     */
    internal fun groupThousands(digits: String): String {
        if (digits.length <= 3) return digits
        val startOffset = digits.length % 3
        val builder = StringBuilder()
        if (startOffset != 0) {
            builder.append(digits, 0, startOffset)
            builder.append(' ')
        }
        var i = startOffset
        while (i < digits.length) {
            val end = i + 3
            builder.append(digits, i, end)
            if (end < digits.length) builder.append(' ')
            i = end
        }
        return builder.toString()
    }

    /** Conversion numérique (non formatée) en unité majeure, pour les axes de graphiques. */
    fun toMajorDouble(minorUnits: Long): Double = minorUnits.toDouble() / MINOR_UNITS_PER_MAJOR

    /** Formate un [CurrencyAmount] pour l'AFFICHAGE, avec le symbole de sa devise
     * et un séparateur de milliers (ex. "10 000 F CFA", ou "10 000,50 F CFA" si le
     * montant a réellement des centimes — voir [formatAmount]). */
    fun format(amount: CurrencyAmount): String =
        "${formatAmount(amount.amountMinor)} ${symbolOf(amount.currencyCode)}"

    /** Symbole d'affichage d'une devise (« F CFA », « € »…), ou son code ISO si elle ne fait pas
     * partie de [SupportedCurrency]. Sert aussi de suffixe aux champs de saisie de montant. */
    fun symbolOf(currencyCode: String): String =
        SupportedCurrency.entries.firstOrNull { it.code == currencyCode }?.symbol ?: currencyCode

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
        val numberFormat = NumberFormat.getNumberInstance(AMOUNT_LOCALE).apply {
            minimumFractionDigits = if (hasCents) 2 else 0
            maximumFractionDigits = if (hasCents) 2 else 0
        }
        val major = BigDecimal(minorUnits).divide(BigDecimal(MINOR_UNITS_PER_MAJOR))
        return numberFormat.format(major)
    }
}
