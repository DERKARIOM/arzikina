package com.naniger.arzikina.domain.model

/**
 * Résultat de [com.naniger.arzikina.domain.repository.UnifiedAuthRepository.linkGoogleAccount] —
 * cahier des charges, Cas C : lie un compte Google au compte Arzikina déjà connecté. Ne touche
 * jamais aux données locales (voir sa KDoc) : contrairement à [UnifiedAuthResult], [Success] ne
 * porte aucun identifiant local, il n'y en a pas de nouveau à résoudre.
 */
sealed class AccountLinkResult {
    data object Success : AccountLinkResult()
    data class Failure(val error: AccountLinkError) : AccountLinkResult()
}
