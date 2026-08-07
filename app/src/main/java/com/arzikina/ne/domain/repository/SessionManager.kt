package com.arzikina.ne.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Session locale de l'utilisateur connecté : sait UNIQUEMENT "qui est
 * actuellement connecté sur cet appareil" (un identifiant), rien de plus.
 *
 * Séparé de [AuthRepository] à dessein : l'authentification (vérifier un
 * mot de passe, créer un compte) et la session (mémoriser qui reste
 * connecté entre deux lancements de l'app) sont deux responsabilités
 * distinctes — la seconde survivra même si la première change totalement
 * (ex. bascule vers une authentification en ligne avec jeton de session :
 * seule l'implémentation de [SessionManager] changerait alors, pas ses
 * appelants).
 *
 * Une seule session active à la fois (pas de bascule multi-comptes sans
 * déconnexion) : conforme au besoin exprimé ("plusieurs personnes utilisent
 * l'app sur le même appareil", donc à tour de rôle, pas simultanément).
 */
interface SessionManager {

    /** `null` = aucun utilisateur connecté. À observer au démarrage de l'app (voir MainActivity). */
    fun observeCurrentUserId(): Flow<Long?>

    /** Lecture ponctuelle, pour les endroits qui n'ont pas besoin d'observer les changements. */
    suspend fun getCurrentUserIdOnce(): Long?

    suspend fun startSession(userId: Long)

    /**
     * Déconnexion : supprime UNIQUEMENT la session locale (ce pointeur vers
     * l'utilisateur connecté), jamais les données de l'utilisateur
     * lui-même — conforme à l'exigence explicite du cahier des charges.
     */
    suspend fun clearSession()
}
