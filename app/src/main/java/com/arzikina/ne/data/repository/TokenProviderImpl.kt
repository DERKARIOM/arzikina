package com.arzikina.ne.data.repository

import javax.inject.Inject

/**
 * Implémentation [TokenProvider] — voir aussi [SyncAuthRepositoryImpl], qui implémente
 * [com.arzikina.ne.domain.repository.SyncAuthRepository] et partage le même stockage sous-jacent
 * via [SyncAuthStore] (voir sa KDoc pour le pourquoi de cette séparation en deux classes plutôt
 * qu'une seule implémentant les deux interfaces).
 */
class TokenProviderImpl @Inject constructor(
    private val store: SyncAuthStore
) : TokenProvider {
    override suspend fun getValidRawToken(): String? = store.getValidRawToken()
}
