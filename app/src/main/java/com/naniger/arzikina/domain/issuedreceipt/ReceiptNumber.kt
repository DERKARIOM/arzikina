package com.naniger.arzikina.domain.issuedreceipt

/**
 * Format et génération des numéros de reçu : « Recu-AAAA-NNN » (compteur par année, 3 chiffres
 * minimum). Logique pure, testée unitairement.
 *
 * Un numéro personnalisé est accepté tant qu'il respecte [isValid] (caractères sûrs pour un nom de
 * fichier : le `/` est refusé pour que « numéro → nom de PDF » reste injectif, deux numéros distincts ne
 * pouvant jamais partager un fichier) ; l'unicité est garantie par le repository (index unique `(userId, number)`).
 */
object ReceiptNumber {

    const val PREFIX = "Recu"
    const val MAX_LENGTH = 30

    private val allowed = Regex("^[A-Za-z0-9][A-Za-z0-9._\\-]{0,${MAX_LENGTH - 1}}$")

    fun isValid(number: String): Boolean = allowed.matches(number)

    /** Préfixe commun des numéros générés pour [year] : « Recu-2026- ». */
    fun yearPrefix(year: Int): String = "$PREFIX-$year-"

    fun format(year: Int, sequence: Int): String =
        "${yearPrefix(year)}${sequence.toString().padStart(3, '0')}"

    /** Séquence d'un numéro généré de l'année [year], ou `null` s'il n'a pas ce format. */
    fun parseSequence(number: String, year: Int): Int? {
        val prefix = yearPrefix(year)
        if (!number.startsWith(prefix)) return null
        val digits = number.removePrefix(prefix)
        if (digits.isEmpty() || digits.length > 9 || !digits.all { it in '0'..'9' }) return null
        return digits.toInt()
    }

    /** Prochain numéro libre de l'année [year] : plus grande séquence existante + 1 (jamais de réutilisation). */
    fun next(year: Int, existingNumbers: Collection<String>): String {
        val highest = existingNumbers.mapNotNull { parseSequence(it, year) }.maxOrNull() ?: 0
        return format(year, highest + 1)
    }

    /** Nom du fichier PDF d'un reçu : « Recu-2026-001.pdf » (le numéro validé ne contient que `[A-Za-z0-9._-]`, jamais modifié). */
    fun pdfFileName(number: String): String =
        number.map { if (it.isLetterOrDigit() && it.code < 128 || it in "._-") it else '-' }
            .joinToString("") + ".pdf"
}
