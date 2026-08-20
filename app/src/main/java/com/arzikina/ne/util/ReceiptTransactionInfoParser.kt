package com.arzikina.ne.util

import com.arzikina.ne.domain.model.TransactionType
import java.time.LocalDate
import java.time.LocalTime

/**
 * Résultat de [ReceiptTransactionInfoParser.parse] — voir sa doc de tête. Chaque champ est
 * INDÉPENDAMMENT `null` si non détecté avec suffisamment de confiance (jamais une valeur inventée,
 * jamais de dépendance entre champs pour décider d'en remplir un autre).
 *
 * @param transactionReference extrait mais non consommé par le formulaire de transaction (qui n'a
 * pas de champ dédié, voir cahier des charges section 12 : "ne pas modifier la structure du
 * formulaire uniquement pour ces données") — conservé ici pour un usage futur (métadonnées du
 * reçu), sans impact sur les étapes suivantes.
 */
data class ReceiptTransactionInfo(
    val amountMinor: Long? = null,
    val feeMinor: Long? = null,
    val dateTimeMillis: Long? = null,
    val description: String? = null,
    val transactionType: TransactionType? = null,
    val transactionReference: String? = null
)

/**
 * Extraction MULTI-CHAMPS d'un reçu PDF pour préremplir le formulaire "Ajouter une transaction"
 * (cahier des charges "Créer une transaction depuis un reçu"). Complète [ReceiptAmountParser]
 * (montant SEUL, utilisé par ailleurs pour "Détecter le montant" dans Détail du reçu) sans le
 * remplacer ni le modifier : deux besoins différents, volontairement séparés pour ne jamais risquer
 * de régression sur ce flux déjà livré — voir sa doc.
 *
 * Même philosophie que [ReceiptAmountParser] : SUGGESTION uniquement (voir [ReceiptTransactionInfo]),
 * jamais une écriture automatique. Fonction PURE (aucune dépendance Android, aucun accès disque) —
 * testable directement, voir `ReceiptTransactionInfoParserTest`, calibré sur un reçu réel (MyNITA,
 * transfert d'argent au Niger) fourni pendant la conception de cette fonctionnalité.
 *
 * Approche "libellé : valeur" (plutôt que la recherche par mots-clés génériques de
 * [ReceiptAmountParser]) : les reçus visés ici (MyNITA, Orange Money, Airtel Money...) suivent tous
 * un gabarit "Champ : valeur" assez régulier — un jeu de libellés connus, tolérants aux variations
 * d'accents/apostrophes (souvent perdues lors de l'extraction de texte d'un PDF, voir
 * `ReceiptTextExtractor`), donne de bien meilleurs résultats ici qu'une recherche de mots-clés
 * libres. [LABEL_BOUNDARY] borne chaque valeur capturée au prochain libellé connu (ou à la fin de la
 * ligne) — nécessaire car certains reçus placent PLUSIEURS "libellé : valeur" sur une même ligne
 * (ex. "Montant : 10 000 CFA Ville : NIAMEY").
 */
object ReceiptTransactionInfoParser {

    fun parse(text: String): ReceiptTransactionInfo {
        val transactionType = parseTransactionType(text)
        return ReceiptTransactionInfo(
            amountMinor = ReceiptAmountParser.parseAmount(text),
            feeMinor = parseFee(text),
            dateTimeMillis = parseDateTimeMillis(text),
            description = buildDescription(text, transactionType),
            transactionType = transactionType,
            transactionReference = parseTransactionReference(text)
        )
    }

    /**
     * Recherche spécifiquement un "Frais :" ISOLÉ — le lookbehind négatif exclut toute occurrence
     * précédée de "de " (ex. "Type de Frais : fraisApars", qui désigne la CATÉGORIE des frais,
     * jamais leur montant, voir l'exemple MyNITA dans les tests). Réutilise
     * [ReceiptAmountParser.normalizeAmountToken] plutôt que de dupliquer la conversion.
     */
    private fun parseFee(text: String): Long? {
        val raw = FEE_REGEX.find(text)?.groupValues?.get(1) ?: return null
        return ReceiptAmountParser.normalizeAmountToken(raw)?.takeIf { it > 0 }
    }

    /**
     * Cherche une date `JJ/MM/AAAA` ET une heure `HH:MM(:SS)` n'importe où dans le texte — jamais
     * l'une sans l'autre : [ReceiptTransactionInfo.dateTimeMillis] est un seul instant complet ou
     * rien (voir `Transaction.date`), l'heure actuelle sert de repli si seule la date est détectée
     * (voir `TransactionFormViewModel`, Étape 5 à venir — pas ici, une fonction pure ne doit pas
     * dépendre de l'heure d'exécution). Fuseau LOCAL de l'appareil, même convention que le reste de
     * l'application (voir [DatePeriods]). Format JJ/MM/AAAA supposé (contexte ouest-africain,
     * jamais MM/JJ) — pas de détection intelligente de l'ordre.
     */
    private fun parseDateTimeMillis(text: String): Long? {
        val dateMatch = DATE_REGEX.find(text) ?: return null
        val day = dateMatch.groupValues[1].toIntOrNull() ?: return null
        val month = dateMatch.groupValues[2].toIntOrNull() ?: return null
        val year = dateMatch.groupValues[3].toIntOrNull() ?: return null
        val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null

        val timeMatch = TIME_REGEX.find(text)
        val time = timeMatch?.let { match ->
            val hour = match.groupValues[1].toIntOrNull()
            val minute = match.groupValues[2].toIntOrNull()
            val second = match.groupValues[3].toIntOrNull() ?: 0
            if (hour == null || minute == null) null else runCatching { LocalTime.of(hour, minute, second) }.getOrNull()
        }

        return DatePeriods.toEpochMillis(date, time ?: LocalTime.MIDNIGHT)
    }

    /** "Débit"/"Crédit" seulement — jamais "Transfert" (impossible à établir fiablement sans
     * connaître les DEUX comptes concernés, voir la doc de tête, "ne jamais inventer"). Recherché
     * n'importe où dans le texte (pas ancré au libellé "Type d'opération", trop instable d'un reçu à
     * l'autre — voir l'exemple MyNITA, où l'extraction du PDF a perdu l'apostrophe : "Type d
     * opération"). */
    private fun parseTransactionType(text: String): TransactionType? = when {
        OPERATION_DEBIT_REGEX.containsMatchIn(text) -> TransactionType.EXPENSE
        OPERATION_CREDIT_REGEX.containsMatchIn(text) -> TransactionType.INCOME
        else -> null
    }

    private fun parseTransactionReference(text: String): String? =
        REFERENCE_REGEX.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

    /**
     * Description SYNTHÉTIQUE ("Transfert vers Ari Aoua"/"Reçu de Abdoul Kader Bachir"), jamais un
     * nom seul — voir cahier des charges section 11 ("Transfert vers Ibrahim"). `null` si ni
     * [transactionType] ni aucun nom (destinataire/expéditeur) n'a pu être détecté : un champ vide
     * reste préférable à une description à moitié devinée.
     */
    private fun buildDescription(text: String, transactionType: TransactionType?): String? {
        val recipient = RECIPIENT_REGEX.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        val sender = SENDER_REGEX.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

        return when {
            transactionType == TransactionType.EXPENSE && recipient != null -> "Transfert vers $recipient"
            transactionType == TransactionType.INCOME && sender != null -> "Reçu de $sender"
            recipient != null -> "Transfert vers $recipient"
            sender != null -> "Reçu de $sender"
            else -> null
        }
    }

    /** Voir la doc de tête : liste des libellés connus, utilisée pour borner chaque valeur
     * capturée ci-dessous à la fin de son propre "libellé : valeur", jamais au suivant. */
    private const val LABEL_BOUNDARY =
        """(?=Montant\s*:|Ville\s*:|(?:Type\s+de\s+)?Frais\s*:|Type\s+d['’ ]?\s*op[ée]ration\s*:|Code\s*:|Exp[ée]diteur\s*:|Destinataire\s*:|B[ée]n[ée]ficiaire\s*:|R[ée]f[ée]rence\s*:|Date\s*:|Heure\s*:|$)"""

    // `MULTILINE` indispensable : sans elle, `$` dans LABEL_BOUNDARY ne correspondrait qu'à la toute
    // fin du texte entier, jamais à la fin de chaque ligne — une valeur en fin de ligne (ex. "Code :
    // MYNITA1ABA77D905DF3", seule sur sa ligne) ne serait alors jamais capturée.
    private val LABEL_VALUE_OPTIONS = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)

    private val FEE_REGEX = Regex("""(?<!de\s)Frais\s*:\s*(.+?)$LABEL_BOUNDARY""", LABEL_VALUE_OPTIONS)
    private val REFERENCE_REGEX = Regex("""(?:Code|R[ée]f[ée]rence|Transaction)\s*:\s*(.+?)$LABEL_BOUNDARY""", LABEL_VALUE_OPTIONS)
    private val RECIPIENT_REGEX = Regex("""Destinataire\s*:\s*(.+?)$LABEL_BOUNDARY""", LABEL_VALUE_OPTIONS)
    private val SENDER_REGEX = Regex("""Exp[ée]diteur\s*:\s*(.+?)$LABEL_BOUNDARY""", LABEL_VALUE_OPTIONS)

    private val DATE_REGEX = Regex("""(\d{1,2})/(\d{1,2})/(\d{4})""")
    private val TIME_REGEX = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""")
    private val OPERATION_DEBIT_REGEX = Regex("""d[ée]bit""", RegexOption.IGNORE_CASE)
    private val OPERATION_CREDIT_REGEX = Regex("""cr[ée]dit""", RegexOption.IGNORE_CASE)
}
