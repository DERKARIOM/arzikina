package com.arzikina.ne.domain.model

/**
 * Type FONCTIONNEL d'un compte — distinct de [AccountIcon], qui reste un
 * choix purement visuel (voir sa doc). Avant l'introduction de la Carte de
 * crédit, l'icône jouait aussi ce rôle par convention ; ce n'est plus
 * possible dès qu'un type a besoin de champs spécifiques (numéro, expiration…)
 * qu'une icône ne peut pas porter.
 *
 * Conçu pour grandir sans refonte : chaque nouveau type se contente d'ajouter
 * une entrée ici et, si besoin, ses propres colonnes nullables sur
 * [com.arzikina.ne.data.local.entity.AccountEntity] (voir [CREDIT_CARD]) —
 * pas de nouvelle table ni de nouvelle hiérarchie de comptes.
 */
enum class AccountType {
    CASH,
    BANK,
    MOBILE_MONEY,
    SAVINGS,

    /**
     * Seul type à ce jour avec des données propres : voir
     * [Account.cardLastFourDigits], [Account.cardExpiryMonth],
     * [Account.cardExpiryYear]. Le numéro complet et le CVV ne sont
     * volontairement jamais conservés au-delà de leur saisie dans le
     * formulaire (voir `AccountFormViewModel`) — Arzikina ne traite aucun
     * paiement, rien ne justifie de pouvoir les ré-afficher plus tard, et ne
     * pas les stocker élimine tout risque de fuite sans nécessiter de
     * chiffrement (Keystore/Cipher).
     */
    CREDIT_CARD
}
