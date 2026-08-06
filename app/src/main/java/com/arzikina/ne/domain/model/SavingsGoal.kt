package com.arzikina.ne.domain.model

/**
 * Objectif d'épargne : montant visé, progression actuelle, échéance
 * optionnelle. Autonome — pas de lien avec [Transaction] ou [Account] : la
 * progression est un champ stocké, mis à jour explicitement (contribution
 * rapide ou correction manuelle dans le formulaire), pas calculée à partir
 * de l'historique des transactions (voir [SavingsGoalRepository.addContribution]).
 *
 * @param deadline échéance en epoch millis (date seule), `null` si aucune.
 * @param id 0L tant que l'objectif n'a pas encore été enregistré en base.
 */
data class SavingsGoal(
    val id: Long = 0L,
    val name: String,
    val targetAmount: Long,
    val currentAmount: Long,
    val currencyCode: String,
    val deadline: Long?,
    val createdAt: Long
)
