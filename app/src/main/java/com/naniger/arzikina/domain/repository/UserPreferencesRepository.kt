package com.naniger.arzikina.domain.repository

import com.naniger.arzikina.domain.model.ThemeMode
import com.naniger.arzikina.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Accès aux préférences globales de l'utilisateur (thème, devise
 * principale...). Le domaine ne connaît pas DataStore : l'implémentation
 * ([com.naniger.arzikina.data.repository.UserPreferencesRepositoryImpl]) est seule
 * responsable du mécanisme de persistance, comme les autres repositories
 * sont seuls responsables de Room.
 */
interface UserPreferencesRepository {
    fun observePreferences(): Flow<UserPreferences>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setCurrencyCode(currencyCode: String)

    /** Devise principale actuelle (lecture ponctuelle) : valeur par défaut des NOUVEAUX éléments (compte, budget…). */
    suspend fun getCurrencyCode(): String = observePreferences().first().currencyCode

    /**
     * Enregistre la devise CHOISIE EXPLICITEMENT par l'utilisateur [userId], même s'il n'est pas (encore) l'utilisateur
     * de la session courante : utilisé à l'inscription, où l'utilisateur local est créé avant que la session démarre.
     * Persiste dans Room et met la synchronisation en file ; aucune conversion de montant n'est effectuée.
     */
    suspend fun setCurrencyCodeForUser(userId: Long, currencyCode: String)

    /** Voir [UserPreferences.biometricLockEnabled] : réglage par appareil, pas par compte. */
    suspend fun setBiometricLockEnabled(enabled: Boolean)
}
