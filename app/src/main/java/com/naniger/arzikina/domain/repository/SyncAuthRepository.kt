package com.naniger.arzikina.domain.repository

import com.naniger.arzikina.domain.model.SyncAuthResult
import com.naniger.arzikina.domain.model.SyncSession
import kotlinx.coroutines.flow.Flow

/**
 * Connexion au serveur de synchronisation Arzikina (voir `server/api/auth/login.php` et
 * `docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md`, sections 6.1 et 12).
 *
 * DÉLIBÉRÉMENT distinct de [AuthRepository] : celui-ci gère les comptes LOCAUX (Room + PBKDF2,
 * plusieurs profils possibles sur le même appareil) ; [SyncAuthRepository] gère la connexion à UN
 * compte serveur pour activer la synchronisation multi-appareils de ce profil. Un utilisateur
 * peut très bien n'utiliser QUE l'authentification locale (mode 100% hors-ligne, comportement
 * actuel de l'app, inchangé) sans jamais appeler cette interface — la synchronisation reste une
 * fonctionnalité additive, jamais un prérequis.
 *
 * Le mot de passe réel n'est envoyé qu'à [login] (voir la KDoc de `login.php`) ; toute
 * synchronisation suivante utilisera le token obtenu ici, jamais le mot de passe à nouveau — ce
 * token reste interne à la couche data (voir [SyncSession] pour ce que le domaine peut en savoir).
 */
interface SyncAuthRepository {

    suspend fun login(identifier: String, rawPassword: String, deviceLabel: String? = null): SyncAuthResult<SyncSession>

    /**
     * Crée un compte sur le serveur de synchronisation puis ouvre directement une session (voir
     * `server/api/auth/register.php`, qui émet un token immédiatement) — utilisé par
     * [com.naniger.arzikina.data.repository.UnifiedAuthRepositoryImpl], jamais appelé isolément par la
     * présentation (voir sa KDoc pour l'orchestration complète : compte serveur + compte local +
     * rattachement).
     */
    suspend fun register(
        fullName: String,
        username: String,
        email: String,
        rawPassword: String,
        phoneNumber: String? = null,
        securityQuestion: String? = null,
        securityAnswer: String? = null,
        deviceLabel: String? = null
    ): SyncAuthResult<SyncSession>

    /** Supprime la session serveur stockée sur cet appareil. N'affecte ni le compte local
     *  ([AuthRepository]) ni les données déjà synchronisées côté serveur. */
    suspend fun logout()

    /** Lecture ponctuelle. `null` si aucune connexion au serveur n'a jamais été faite, ou si le
     *  token stocké est expiré (pas de rafraîchissement automatique à ce stade — voir
     *  `api/auth/refresh.php`, à écrire dans une étape ultérieure). */
    suspend fun getActiveSession(): SyncSession?

    /** Pour un futur indicateur de statut de synchronisation / écran Réglages. */
    fun observeActiveSession(): Flow<SyncSession?>
}
