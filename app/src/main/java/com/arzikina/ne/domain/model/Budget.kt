package com.arzikina.ne.domain.model

/**
 * Règle de plafond de dépenses pour une catégorie, renouvelée à chaque
 * [period] (ex. "300 000 F CFA par mois pour Nourriture"). Ce n'est pas une
 * transaction : la progression ("dépensé jusqu'ici") est calculée à
 * l'affichage à partir des [Transaction] de la période en cours, pas stockée
 * ici — voir [BudgetRepository].
 *
 * @param currencyCode devise du plafond ; seules les transactions de la
 * catégorie effectuées dans cette devise entrent dans le calcul de
 * progression (pas de conversion de change dans l'application, voir
 * [CurrencyAmount]).
 * @param id 0L tant que le budget n'a pas encore été enregistré en base.
 */
data class Budget(
    val id: Long = 0L,
    val categoryId: Long,
    val period: BudgetPeriod,
    val limitAmount: Long,
    val currencyCode: String,
    val createdAt: Long
)
