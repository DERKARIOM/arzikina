package com.arzikina.ne.domain.model

import com.arzikina.ne.util.Constants

/**
 * Préférences globales de l'utilisateur, indépendantes de toute donnée métier (comptes,
 * transactions...) — voir [com.arzikina.ne.domain.repository.UserPreferencesRepository].
 *
 * Stockage HYBRIDE depuis l'étape 22 (chantier de synchronisation multi-appareils, voir
 * `com.arzikina.ne.data.repository.UserPreferencesRepositoryImpl` pour le détail complet) :
 * [themeMode]/[currencyCode] vivent désormais dans Room (synchronisables, une ligne par
 * utilisateur), [biometricLockEnabled] reste dans DataStore Preferences, PAR APPAREIL.
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
