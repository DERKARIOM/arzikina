package com.arzikina.ne.domain.model

/**
 * Règle de plafond de dépenses pour une catégorie. Ce n'est pas une
 * transaction : la progression ("dépensé jusqu'ici") est calculée à
 * l'affichage à partir des [Transaction] de la période, pas stockée ici —
 * voir [BudgetRepository].
 *
 * Deux modes de période coexistent (voir [util.BudgetProgress]) :
 * - **Récurrent (legacy)** : [startDate]/[endDate] valent `null`, [period]
 *   (semaine/mois civil courant, recalculé en continu) fait foi. C'est le
 *   seul mode qui existait avant la fonctionnalité "période fixe" — conservé
 *   tel quel pour ne jamais casser les budgets déjà créés.
 * - **Période fixe** : [startDate]/[endDate] non nuls (bornes inclusives,
 *   millisecondes epoch, début/fin de journée locale). [period] est alors
 *   ignoré par le calcul de progression et par l'affichage (il garde une
 *   valeur par défaut purement technique, imposée par la colonne SQL non
 *   nullable — voir [com.arzikina.ne.data.local.entity.BudgetEntity]).
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
    val createdAt: Long,
    val startDate: Long? = null,
    val endDate: Long? = null
)
