package com.arzikina.ne.util

import java.text.Normalizer

/**
 * Heuristique de détection d'un montant dans le texte brut d'un reçu (voir
 * `data/receipts/ReceiptTextExtractor`) — cahier des charges "Gestion des reçus", extraction du
 * montant.
 *
 * IMPORTANT : ceci reste une SUGGESTION, jamais une certitude. Les reçus Mobile Money (Orange
 * Money, Airtel Money, Moov Money, Wave...) n'ont aucun gabarit commun — cette heuristique peut se
 * tromper (montant de frais confondu avec le montant principal, solde du compte capté à la place
 * d'un transfert, ligne non reconnue...). Le résultat de [parseAmount] ne doit JAMAIS être enregistré
 * automatiquement dans `Receipt.amountMinor` : il est destiné à être présenté à l'utilisateur pour
 * confirmation explicite (voir `ReceiptDetailViewModel`, Étape 3 à venir) — même principe déjà
 * appliqué à la provenance d'un reçu ("ne jamais inventer une donnée").
 *
 * Fonction PURE (aucune dépendance Android, aucun accès disque) — testable directement, voir
 * `ReceiptAmountParserTest`.
 */
object ReceiptAmountParser {

    /**
     * Cherche d'abord les mots-clés à HAUTE confiance (formulations qui désignent sans ambiguïté le
     * montant principal de la transaction), puis seulement si rien n'est trouvé, les mots-clés
     * génériques (moins fiables : "Total"/"Montant" seuls peuvent apparaître sur un reçu pour tout
     * autre chose, ex. un total de frais). Volontairement SANS accents (voir [normalizeForMatching],
     * appliqué aussi bien ici qu'au texte du reçu avant comparaison).
     */
    private val highConfidenceKeywords = listOf(
        "montant total", "montant transfere", "montant envoye", "montant recu",
        "total paye", "montant a payer", "montant de la transaction", "montant transaction"
    )
    private val mediumConfidenceKeywords = listOf("montant", "total", "somme")

    /** Une ligne contenant l'un de ces mots est ENTIÈREMENT ignorée, même si elle contient par
     * ailleurs un mot-clé ci-dessus : ce sont presque toujours des montants secondaires (frais,
     * commission) ou hors-sujet (solde du compte APRÈS la transaction, jamais le montant transféré
     * lui-même). */
    private val excludeKeywords = listOf("frais", "commission", "solde", "balance")

    /** Un nombre éventuellement groupé par milliers (espace, point ou virgule) — capture volontairement
     * large, la validité réelle est vérifiée ensuite par [normalizeAmountToken]. */
    private val numberTokenRegex = Regex("""\d[\d\s.,]*\d|\d""")

    /**
     * @return un montant en unité mineure (voir [Money], même convention ×100), ou `null` si aucune
     * ligne suffisamment fiable n'a été trouvée.
     */
    fun parseAmount(text: String): Long? {
        val lines = text.lines()
        return findAmount(lines, highConfidenceKeywords) ?: findAmount(lines, mediumConfidenceKeywords)
    }

    private fun findAmount(lines: List<String>, keywords: List<String>): Long? {
        for (index in lines.indices) {
            val normalizedLine = normalizeForMatching(lines[index])
            if (excludeKeywords.any { normalizedLine.contains(it) }) continue
            if (keywords.none { normalizedLine.contains(it) }) continue

            // Le montant suit généralement le mot-clé sur la MÊME ligne (ex. "Montant : 15 000
            // FCFA") — mais certains reçus l'affichent seul sur la ligne SUIVANTE (ex. "Montant\n15
            // 000 FCFA"), d'où ce repli si la ligne du mot-clé ne contient elle-même aucun chiffre.
            val candidateLine = lines[index].takeIf { it.any(Char::isDigit) }
                ?: lines.getOrNull(index + 1)?.takeIf { line ->
                    line.any(Char::isDigit) && excludeKeywords.none { normalizeForMatching(line).contains(it) }
                }
                ?: continue

            val match = numberTokenRegex.find(candidateLine) ?: continue
            val minorUnits = normalizeAmountToken(match.value) ?: continue
            if (minorUnits > 0) return minorUnits
        }
        return null
    }

    /**
     * Convertit un jeton numérique brut (ex. "15 000", "15.000", "1 500,50") en unité mineure.
     *
     * Ambiguïté classique CFA vs devises à décimales : le dernier séparateur rencontré est traité
     * comme une virgule DÉCIMALE seulement s'il est suivi d'EXACTEMENT 2 chiffres (ex. "1500,50" ou
     * "1500.50") — sinon (ex. "15.000", "10 000 000") tous les séparateurs sont traités comme des
     * regroupements de milliers. Cette règle simple couvre la quasi-totalité des formats rencontrés
     * en pratique (aucun reçu Mobile Money local n'utilise de centimes réels).
     */
    private fun normalizeAmountToken(raw: String): Long? {
        val cleaned = raw.filter { it.isDigit() || it == '.' || it == ',' }
        val lastSeparatorIndex = cleaned.indexOfLast { it == '.' || it == ',' }
        val isDecimal = lastSeparatorIndex != -1 && cleaned.length - lastSeparatorIndex - 1 == 2

        return if (isDecimal) {
            val integerPart = cleaned.substring(0, lastSeparatorIndex).filter(Char::isDigit)
            val fractionalPart = cleaned.substring(lastSeparatorIndex + 1)
            val integerValue = integerPart.toLongOrNull() ?: return null
            val fractionalValue = fractionalPart.toLongOrNull() ?: return null
            integerValue * 100 + fractionalValue
        } else {
            val integerValue = cleaned.filter(Char::isDigit).toLongOrNull() ?: return null
            integerValue * 100
        }
    }

    /** Minuscules ET sans accents (ex. "à" -> "a") : les reçus n'utilisent pas toujours une
     * accentuation correcte/cohérente, comparer des chaînes déjà "aplaties" des deux côtés évite de
     * rater un mot-clé pour cette seule raison. */
    private fun normalizeForMatching(text: String): String {
        val withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return withoutAccents.lowercase()
    }
}
