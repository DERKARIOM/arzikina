package com.arzikina.ne.data.repository

import com.arzikina.ne.domain.repository.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Fausse implémentation de [SessionManager] pour les tests instrumentés Room (voir
 * `LoanRepositoryImplTest`/`AccountRepositoryImplDeletionTest`) : un seul utilisateur fixe, jamais
 * déconnecté — les repositories testés filtrent systématiquement par `userId` (voir la doc de
 * `AccountDao`), il faut donc une session active pour que `requireCurrentUserId()` ne lève pas.
 */
class FakeSessionManager(private val userId: Long = DEFAULT_USER_ID) : SessionManager {

    private val currentUserId = MutableStateFlow<Long?>(userId)

    override fun observeCurrentUserId(): StateFlow<Long?> = currentUserId

    override suspend fun getCurrentUserIdOnce(): Long? = currentUserId.value

    override suspend fun startSession(userId: Long) {
        currentUserId.value = userId
    }

    override suspend fun clearSession() {
        currentUserId.value = null
    }

    companion object {
        const val DEFAULT_USER_ID = 1L
    }
}
