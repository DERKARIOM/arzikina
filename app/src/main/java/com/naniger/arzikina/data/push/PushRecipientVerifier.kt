package com.naniger.arzikina.data.push

import com.naniger.arzikina.data.local.dao.UserServerLinkDao
import com.naniger.arzikina.data.repository.SyncAuthStore
import com.naniger.arzikina.domain.repository.SessionManager
import javax.inject.Inject

/**
 * Seconde barrière contre les fuites entre comptes, côté appareil (la première est côté serveur :
 * appareils révoqués à la déconnexion, voir `server/api/auth/logout.php`).
 *
 * Indispensable quand la déconnexion n'a pas pu prévenir le serveur (hors ligne) : le serveur
 * croit encore l'appareil rattaché à l'ancien compte. Une notification n'est donc affichée que si
 * les TROIS conditions suivantes sont réunies :
 * 1. un utilisateur est connecté localement ;
 * 2. une session serveur valide est ouverte pour le compte destinataire ;
 * 3. l'utilisateur local est bien rattaché à ce compte serveur (`user_server_links`).
 */
class PushRecipientVerifier @Inject constructor(
    private val sessionManager: SessionManager,
    private val syncAuthStore: SyncAuthStore,
    private val userServerLinkDao: UserServerLinkDao
) {
    suspend fun isCurrentRecipient(recipientServerUserId: String): Boolean {
        val localUserId = sessionManager.getCurrentUserIdOnce() ?: return false
        val serverSession = syncAuthStore.getActiveSession() ?: return false
        if (!serverSession.serverUserId.equals(recipientServerUserId, ignoreCase = true)) return false
        val link = userServerLinkDao.getByLocalUserId(localUserId) ?: return false
        return link.serverUserId.equals(recipientServerUserId, ignoreCase = true)
    }
}
