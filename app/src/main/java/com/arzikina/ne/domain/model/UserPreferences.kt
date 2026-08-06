package com.arzikina.ne.domain.model

import com.arzikina.ne.util.Constants

/**
 * Préférences globales de l'utilisateur, indépendantes de toute donnée
 * métier (comptes, transactions...) — stockées via DataStore Preferences
 * plutôt que Room, car ce sont de simples valeurs scalaires sans relation ni
 * besoin de requêtes (voir [com.arzikina.ne.domain.repository.UserPreferencesRepository]).
 *
 * [currencyCode] est la devise "principale" utilisée pour les agrégats qui
 * doivent réduire plusieurs devises à une seule (ex. statistiques) — elle ne
 * remplace pas [com.arzikina.ne.domain.model.Account.currencyCode], qui reste
 * propre à chaque compte.
 */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val currencyCode: String = Constants.DEFAULT_CURRENCY_CODE
)
