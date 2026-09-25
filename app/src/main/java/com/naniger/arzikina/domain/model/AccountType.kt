package com.naniger.arzikina.domain.model

/**
 * Type FONCTIONNEL d'un compte — distinct de [AccountIcon], qui reste un
 * choix purement visuel (voir sa doc). Avant l'introduction de la Carte de
 * crédit, l'icône jouait aussi ce rôle par convention ; ce n'est plus
 * possible dès qu'un type a besoin de champs spécifiques (numéro, expiration…)
 * qu'une icône ne peut pas porter.
 *
 * Conçu pour grandir sans refonte : chaque nouveau type se contente d'ajouter
 * une entrée ici et, si besoin, ses propres colonnes nullables sur
 * [com.naniger.arzikina.data.local.entity.AccountEntity] (voir [CREDIT_CARD]) —
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
    CREDIT_CARD,

    /**
     * Objectif d'épargne : un compte à part entière (solde = solde initial + transactions,
     * transferts, statistiques… exactement comme les autres types), qui porte en plus un montant
     * cible — voir [Account.savingsTargetAmount]/[Account.savingsDescription]. Remplace l'ancien
     * utilitaire « Objectifs d'épargne » (table `savings_goals` séparée, montant épargné saisi à la
     * main sans transaction) : voir `data/local/database/LegacySavingsGoalMigrator`.
     *
     * Transformer un compte existant en objectif (ou l'inverse) se fait en changeant simplement
     * son type depuis « Modifier le compte » : même ligne `accounts` (même id/syncId), mêmes
     * transactions, seul le montant cible est ajouté/retiré.
     */
    SAVINGS_GOAL
}
