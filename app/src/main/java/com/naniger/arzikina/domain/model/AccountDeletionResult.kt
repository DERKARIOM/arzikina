package com.naniger.arzikina.domain.model

/**
 * Résultat de la suppression définitive du compte (voir
 * [com.naniger.arzikina.domain.repository.UnifiedAuthRepository.deleteAccount]). [Success] seul,
 * sans donnée : aucune information à transporter après un succès (session serveur ET locale déjà
 * effacées, toutes les données locales de l'utilisateur déjà purgées — voir l'implémentation) —
 * l'appelant n'a plus qu'à naviguer vers l'écran de connexion.
 */
sealed class AccountDeletionResult {
    data object Success : AccountDeletionResult()
    data class Failure(val error: AccountDeletionError) : AccountDeletionResult()
}
