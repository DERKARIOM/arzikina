package com.arzikina.ne.domain.model

/**
 * Numéro complet et CVV DÉCHIFFRÉS d'une carte de crédit (voir [AccountType.CREDIT_CARD]),
 * obtenus uniquement à la demande explicite de l'utilisateur (voir
 * [com.arzikina.ne.domain.repository.AccountRepository.revealCardSecrets]) — jamais conservés
 * au-delà de la session d'affichage courante (voir `AccountDetailViewModel`, qui les efface après
 * un délai ou dès que l'écran n'est plus visible).
 */
data class CardSecrets(val fullNumber: String, val cvv: String) {
    /** `toString()` redacté (même raisonnement que `AccountFormState`, voir sa doc) : ce sont ici
     * des données DÉCHIFFRÉES en clair — un `Log.d`/rapport de plantage accidentel ne doit jamais
     * pouvoir les capturer via le `toString()` par défaut d'une data class. */
    override fun toString(): String = "CardSecrets(fullNumber=${"*".repeat(fullNumber.length)}, " +
        "cvv=${"*".repeat(cvv.length)})"
}
