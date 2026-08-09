package com.arzikina.ne.domain.model

/**
 * Compte financier de l'utilisateur (Espèces, Banque, Mobile Money, Épargne,
 * Carte de crédit, ou tout autre compte personnalisé).
 *
 * [initialBalance] est exprimé dans l'unité mineure de la devise (ex. les
 * centimes pour l'EUR) et non en [Double], pour éliminer tout risque
 * d'erreur d'arrondi propre aux nombres à virgule flottante. Cette règle
 * s'appliquera à tous les montants de l'application (transactions, budgets,
 * objectifs d'épargne...).
 *
 * @param id 0L tant que le compte n'a pas encore été enregistré en base.
 * @param type type fonctionnel du compte (voir [AccountType]) — distinct de
 * [icon], qui reste un choix purement visuel. `CASH` par défaut pour rester
 * compatible avec le code écrit avant l'introduction de ce champ.
 * @param cardLastFourDigits 4 derniers chiffres du numéro de carte, pour un
 * affichage du type "•••• •••• •••• 1234" ; `null` sauf pour
 * [AccountType.CREDIT_CARD]. Le numéro complet n'est volontairement jamais
 * conservé (voir [AccountType.CREDIT_CARD]).
 * @param cardExpiryMonth, @param cardExpiryYear date d'expiration de la
 * carte (mois 1-12, année sur 4 chiffres) ; `null` sauf pour
 * [AccountType.CREDIT_CARD].
 */
data class Account(
    val id: Long = 0L,
    val name: String,
    val icon: AccountIcon,
    val colorArgb: Long,
    val currencyCode: String,
    val initialBalance: Long,
    val createdAt: Long,
    val type: AccountType = AccountType.CASH,
    val cardLastFourDigits: String? = null,
    val cardExpiryMonth: Int? = null,
    val cardExpiryYear: Int? = null
)
