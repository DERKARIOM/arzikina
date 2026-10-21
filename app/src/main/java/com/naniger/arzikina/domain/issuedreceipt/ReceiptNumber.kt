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

    private val adjusted = Regex("^$PREFIX-\\d{4}-\\d{3,9}-\\d{1,4}$")

    /**
     * `true` si [number] a la forme d'un numéro GÉNÉRÉ puis suffixé par [disambiguated] (« Recu-2026-005-2 »),
     * c'est-à-dire renuméroté par la synchronisation après un doublon. Heuristique sur la forme seule (aucune
     * colonne en plus) : un numéro personnalisé suffixé est traité comme un numéro libre.
     */
    fun isAdjusted(number: String): Boolean = adjusted.matches(number)

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

    /**
     * Variante libre de [number] quand il est déjà pris (doublon issu de deux appareils hors ligne) :
     * « Recu-2026-004 » → « Recu-2026-004-2 », puis « -3 », etc. Le numéro d'origine est tronqué si besoin
     * pour que le résultat reste valide (≤ [MAX_LENGTH]) ; [taken] est comparé sans tenir compte de la casse
     * pour rester sûr même si le serveur compare ainsi.
     */
    fun disambiguated(number: String, taken: Collection<String>): String {
        val used = taken.map { it.lowercase() }.toHashSet()
        var n = 2
        while (true) {
            val suffix = "-$n"
            val candidate = number.take(MAX_LENGTH - suffix.length) + suffix
            if (candidate.lowercase() !in used) return candidate
            n++
        }
    }

    /**
     * Prétention d'un reçu à un numéro, pour départager un doublon (deux appareils hors ligne ont
     * attribué le même numéro). [syncId] vide = pas encore synchronisé.
     */
    data class Claim(val deleted: Boolean, val createdAt: Long, val syncId: String)

    /**
     * `true` si [a] doit perdre le numéro face à [b] (et être renuméroté). Règle déterministe, donc
     * identique sur tous les appareils : un reçu supprimé perd toujours face à un reçu actif ; sinon
     * le plus récemment créé perd ; à égalité de date, le plus grand `syncId`.
     */
    fun loses(a: Claim, b: Claim): Boolean = when {
        a.deleted != b.deleted -> a.deleted
        a.createdAt != b.createdAt -> a.createdAt > b.createdAt
        else -> a.syncId > b.syncId
    }

    /** Nom du fichier PDF d'un reçu : « Recu-2026-001.pdf » (le numéro validé ne contient que `[A-Za-z0-9._-]`, jamais modifié). */
    fun pdfFileName(number: String): String =
        number.map { if (it.isLetterOrDigit() && it.code < 128 || it in "._-") it else '-' }
            .joinToString("") + ".pdf"
}
