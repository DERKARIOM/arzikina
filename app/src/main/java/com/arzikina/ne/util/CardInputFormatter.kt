package com.arzikina.ne.util

/**
 * Formatage/validation de la saisie d'une carte de crédit (voir
 * `domain/model/AccountType.CREDIT_CARD`). Utilitaire pur (aucune dépendance
 * Android), même principe que [Money] : testable isolément, réutilisable par
 * n'importe quel écran.
 *
 * Ce fichier ne fait que VALIDER/FORMATER la saisie, jamais que la persister :
 * l'enregistrement (4 derniers chiffres en clair sur `Account`, numéro complet
 * + CVV chiffrés dans `card_secrets`) est entièrement délégué à l'appelant
 * (voir `AccountFormViewModel.save` et `data/security/CardCipher`).
 */
object CardInputFormatter {
    private const val MAX_CARD_NUMBER_DIGITS = 19
    private const val MIN_CARD_NUMBER_DIGITS = 12
    private const val MAX_CVV_DIGITS = 4
    private const val MIN_CVV_DIGITS = 3
    private const val EXPIRY_DIGITS = 4

    /** Ne garde que les chiffres de [input], tronqués à [maxLength]. */
    fun digitsOnly(input: String, maxLength: Int): String =
        input.filter { it.isDigit() }.take(maxLength)

    fun cardNumberDigits(input: String): String = digitsOnly(input, MAX_CARD_NUMBER_DIGITS)

    fun cvvDigits(input: String): String = digitsOnly(input, MAX_CVV_DIGITS)

    /** Regroupe [digits] par paquets de 4 pour l'affichage (ex. "1234567890123456" ->
     * "1234 5678 9012 3456"), utilisé uniquement lors de la révélation temporaire du numéro
     * complet (voir `AccountDetailViewModel.cardSecrets`). */
    fun groupDigits(digits: String): String = digits.chunked(4).joinToString(" ")

    /** Insère automatiquement "/" après les 2 premiers chiffres (ex. "1228" -> "12/28"),
     * pour une saisie "MM/AA" au clavier numérique sans que l'utilisateur tape lui-même le "/". */
    fun formatExpiry(rawInput: String): String {
        val digits = digitsOnly(rawInput, EXPIRY_DIGITS)
        return if (digits.length <= 2) digits else "${digits.substring(0, 2)}/${digits.substring(2)}"
    }

    /**
     * Algorithme de Luhn : détecte une erreur de frappe dans un numéro de
     * carte (chiffre inversé, oublié...). Ne garantit PAS que la carte existe
     * réellement — aucune vérification réseau n'est possible ni nécessaire
     * pour Arzikina, qui ne traite aucun paiement.
     */
    fun isValidLuhn(digits: String): Boolean {
        if (digits.isEmpty()) return false
        var sum = 0
        var doubleNext = false
        for (i in digits.length - 1 downTo 0) {
            var value = digits[i] - '0'
            if (doubleNext) {
                value *= 2
                if (value > 9) value -= 9
            }
            sum += value
            doubleNext = !doubleNext
        }
        return sum % 10 == 0
    }

    /** [digits] : uniquement des chiffres (voir [cardNumberDigits]), longueur générique
     * (12-19) plutôt que spécifique à un réseau (Visa/Mastercard/Amex...) — Arzikina ne
     * distingue pas les réseaux de carte. */
    fun isValidCardNumber(digits: String): Boolean =
        digits.length in MIN_CARD_NUMBER_DIGITS..MAX_CARD_NUMBER_DIGITS && isValidLuhn(digits)

    fun isValidCvv(digits: String): Boolean = digits.length in MIN_CVV_DIGITS..MAX_CVV_DIGITS

    /** [expiryDigits] : "MMAA" (4 chiffres, voir [formatExpiry] dépouillé de son "/").
     * Valide si le mois existe (1-12) ET que le mois/année ne sont pas déjà passés par
     * rapport à [referenceYear]/[referenceMonth] (année sur 4 chiffres, mois 1-12). */
    fun isValidExpiry(expiryDigits: String, referenceYear: Int, referenceMonth: Int): Boolean {
        if (expiryDigits.length != EXPIRY_DIGITS) return false
        val month = expiryDigits.substring(0, 2).toIntOrNull() ?: return false
        val twoDigitYear = expiryDigits.substring(2).toIntOrNull() ?: return false
        if (month !in 1..12) return false
        val fullYear = 2000 + twoDigitYear
        return fullYear > referenceYear || (fullYear == referenceYear && month >= referenceMonth)
    }
}
