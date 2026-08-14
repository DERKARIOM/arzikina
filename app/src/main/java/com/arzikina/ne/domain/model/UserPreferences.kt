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
 *
 * [biometricLockEnabled] est volontairement un réglage PAR APPAREIL, pas par compte : ce
 * DataStore n'est pas scopé par utilisateur (contrairement à Room, voir `UserEntity`), et ce choix
 * a été fait explicitement plutôt que d'introduire une colonne + migration Room. Conséquence
 * assumée : sur un appareil partagé par plusieurs comptes Arzikina, activer le verrou depuis UN
 * compte l'impose à la connexion de TOUS les comptes utilisés ensuite sur cet appareil (voir
 * `MainActivity.resolveStartDestination`, qui lit cette préférence indépendamment de la session).
 */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val currencyCode: String = Constants.DEFAULT_CURRENCY_CODE,
    val biometricLockEnabled: Boolean = false
)
