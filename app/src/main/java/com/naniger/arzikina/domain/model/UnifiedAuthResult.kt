package com.naniger.arzikina.domain.model

/**
 * Résultat d'un login/inscription unifiés (voir
 * [com.naniger.arzikina.domain.repository.UnifiedAuthRepository]). Contrairement à [AuthResult]/
 * [SyncAuthResult] (génériques sur le type de succès), celui-ci est spécifique : le seul "succès"
 * possible ici est un utilisateur LOCAL résolu (créé ou déjà existant, éventuellement rattaché à un
 * compte serveur) prêt pour [com.naniger.arzikina.domain.repository.SessionManager.startSession].
 */
sealed class UnifiedAuthResult {
    /**
     * [localUserId] : à passer tel quel à `SessionManager.startSession`.
     * [usedLocalFallback] : `true` si la connexion a réussi HORS-LIGNE (voir
     * [UnifiedAuthError.NetworkUnavailableNoLocalFallback]) — l'appelant doit alors SAUTER l'étape
     * de synchronisation automatique (aucune session serveur valide) et adapter le message affiché
     * ("Connecté hors-ligne" plutôt que "Synchronisation terminée").
     */
    data class Success(val localUserId: Long, val usedLocalFallback: Boolean) : UnifiedAuthResult()

    data class Failure(val error: UnifiedAuthError) : UnifiedAuthResult()
}
