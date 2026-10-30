package com.naniger.arzikina.domain.currency

/**
 * Description d'une devise ISO 4217, indépendante de toute langue et de tout framework.
 *
 * @property code code ISO 4217 en majuscules (« XOF »).
 * @property symbol symbole d'affichage (« FCFA », « € »…) ; le code ISO si la devise n'a pas de symbole usuel.
 * @property fractionDigits nombre de décimales USUELLES de la devise selon ISO 4217 (0 pour XOF, 2 pour EUR, 3 pour TND…).
 *   Il ne sert qu'à l'affichage : le STOCKAGE reste en unités mineures à facteur fixe (voir `Money.MINOR_UNITS_PER_MAJOR`),
 *   ce qui évite toute migration de données.
 * @property region pays/zone principal(e), pour la recherche (« Niger, Mali… ») ; optionnel.
 * @property isFeatured devise mise en avant en tête de liste (contexte ouest-africain + devises majeures).
 */
data class CurrencyInfo(
    val code: String,
    val symbol: String,
    val fractionDigits: Int,
    val region: String = "",
    val isFeatured: Boolean = false
)
