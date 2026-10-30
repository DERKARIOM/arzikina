package com.naniger.arzikina.presentation.auth.currency

import com.naniger.arzikina.domain.currency.CurrencyCatalog

/**
 * État PUR (sans Android) de l'étape « Choisissez votre devise », partagé par l'inscription classique et l'écran
 * Google pour ne dupliquer aucune règle.
 *
 * - [code] : devise sélectionnée, toujours un code du catalogue.
 * - [query] : texte de la barre de recherche.
 * - [touched] : l'utilisateur a-t-il choisi lui-même ? Tant que non, la présélection suit la région de l'appareil
 *   ([withRegionDefault]) ; dès qu'il choisit, elle n'est plus jamais écrasée.
 */
data class CurrencySelection(
    val code: String = CurrencyCatalog.DEFAULT_CODE,
    val query: String = "",
    val touched: Boolean = false
) {
    /** Présélection d'après le pays de l'appareil (jamais la langue) ; sans effet après un choix explicite. */
    fun withRegionDefault(countryCode: String?): CurrencySelection =
        if (touched) this else copy(code = CurrencyCatalog.defaultFor(countryCode))

    /** Choix explicite de l'utilisateur ; un code hors catalogue est ramené à la valeur par défaut. */
    fun select(newCode: String): CurrencySelection = copy(code = CurrencyCatalog.sanitize(newCode), touched = true)

    fun search(newQuery: String): CurrencySelection = copy(query = newQuery)
}
