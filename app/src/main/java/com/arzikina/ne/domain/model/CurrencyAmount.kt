package com.arzikina.ne.domain.model

/**
 * Montant (unité mineure, voir [Account.initialBalance]) associé à sa devise.
 *
 * Utilisé partout où plusieurs montants doivent être agrégés sans supposer
 * qu'ils partagent la même devise (ex. solde total du tableau de bord) :
 * il n'y a aujourd'hui aucune conversion de change dans l'application, donc
 * additionner des montants de devises différentes en un seul [Long]
 * produirait un chiffre erroné. Les appelants doivent regrouper par
 * [currencyCode] avant de sommer (voir `presentation/dashboard/DashboardViewModel`).
 */
data class CurrencyAmount(
    val currencyCode: String,
    val amountMinor: Long
)
