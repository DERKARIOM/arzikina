package com.naniger.arzikina.domain.repository

import com.naniger.arzikina.domain.model.GoogleSyncSession
import com.naniger.arzikina.domain.model.PasswordChangeResult
import com.naniger.arzikina.domain.model.PasswordProof
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

    /** Supprime la session serveur stockée sur cet appareil (immédiatement, même hors ligne), puis
     *  demande au serveur de la révoquer et de couper les notifications push de cet appareil (au
     *  mieux, sans bloquer). N'affecte ni le compte local ([AuthRepository]) ni les données déjà
     *  synchronisées côté serveur. */
    suspend fun logout()

    /**
     * Supprime DÉFINITIVEMENT le compte serveur (et toutes ses données, voir
     * `server/api/auth/delete_account.php`) — [rawPassword] RE-DEMANDÉ (même principe que [login])
     * : un token seul ne suffit jamais à détruire le compte. Contrairement à [logout], une
     * [SyncAuthResult.Failure] ici ne touche à RIEN (ni session serveur, ni session locale) — voir
     * [com.naniger.arzikina.domain.repository.UnifiedAuthRepository.deleteAccount], seul appelant
     * prévu, pour le raisonnement complet (jamais effacer les données locales si le serveur n'a pas
     * confirmé la suppression). En cas de [SyncAuthResult.Success], la session serveur locale
     * (token) est déjà effacée au retour de cet appel — le Bearer utilisé n'a de toute façon plus
     * aucune ligne `auth_tokens` valide côté serveur (voir la KDoc de `delete_account.php`).
     */
    suspend fun deleteAccount(rawPassword: String): SyncAuthResult<Unit>

    /** Lecture ponctuelle. `null` si aucune connexion au serveur n'a jamais été faite, ou si le
     *  token stocké est expiré (pas de rafraîchissement automatique à ce stade — voir
     *  `api/auth/refresh.php`, à écrire dans une étape ultérieure). */
    suspend fun getActiveSession(): SyncSession?

    /**
     * Authentification Google (« Continuer avec Google ») — voir `server/api/auth/google_login.php`.
     * [idToken] : jeton d'identité Google obtenu côté présentation (Credential Manager), jamais
     * vérifié ici : la vérification réelle (signature, audience, e-mail vérifié) est faite PAR LE
     * SERVEUR, jamais fait confiance côté client (cahier des charges : "Ne considère jamais qu'un
     * simple e-mail ou un nom transmis par le client suffit à authentifier un utilisateur").
     *
     * [SyncAuthResult.Failure] avec [com.naniger.arzikina.domain.model.SyncAuthError.AccountExistsLinkRequired] :
     * un compte Arzikina existe déjà avec cet e-mail, créé par mot de passe, pas encore lié à ce
     * compte Google (cahier des charges, Cas C) — voir [linkGoogleAccount].
     */
    suspend fun loginWithGoogle(idToken: String, deviceLabel: String? = null): SyncAuthResult<GoogleSyncSession>

    /**
     * Lie le compte Google (jeton [idToken]) au compte Arzikina de la session active — voir
     * `server/api/auth/link_google_account.php` et le cahier des charges, Cas C. Échoue avec
     * [com.naniger.arzikina.domain.model.SyncAuthError.Unknown] si aucune session de synchronisation
     * n'est active (l'utilisateur doit être connecté classiquement au moment de l'appel, voir
     * [com.naniger.arzikina.domain.repository.UnifiedAuthRepository.linkGoogleAccount] pour la
     * vérification explicite faite AVANT cet appel). Ne touche à aucune donnée locale.
     */
    suspend fun linkGoogleAccount(idToken: String): SyncAuthResult<Unit>

    /**
     * Définit ou change le mot de passe du compte SERVEUR connecté (voir `server/api/auth/set_password.php`) :
     * [proof] est vérifiée par le serveur (mot de passe actuel, ou jeton Google lié au compte). Échoue avec
     * [com.naniger.arzikina.domain.model.PasswordChangeError.NoActiveSession] sans session de synchronisation.
     * Les autres sessions du compte sont révoquées par le serveur ; ne touche à aucune donnée locale (le
     * hash local est mis à jour par [UnifiedAuthRepository.changePassword], seul appelant).
     */
    suspend fun setPassword(newRawPassword: String, proof: PasswordProof): PasswordChangeResult

    /** Pour un futur indicateur de statut de synchronisation / écran Réglages. */
    fun observeActiveSession(): Flow<SyncSession?>
}
