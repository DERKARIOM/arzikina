package com.naniger.arzikina.domain.repository

import com.naniger.arzikina.domain.model.AccountDeletionResult
import com.naniger.arzikina.domain.model.AccountLinkResult
import com.naniger.arzikina.domain.model.PasswordChangeResult
import com.naniger.arzikina.domain.model.PasswordProof
import com.naniger.arzikina.domain.model.SecurityQuestion
import com.naniger.arzikina.domain.model.UnifiedAuthResult
import com.naniger.arzikina.domain.model.UsageMode
import com.naniger.arzikina.domain.model.UsageModeSelectionResult

/**
 * Point d'entrée UNIQUE d'authentification pour l'app mobile — cahier des charges "audit auth +
 * sync + doublons", étape D ("Nouvelle logique d'authentification") : l'utilisateur se connecte
 * DÉSORMAIS avec les identifiants de son compte de synchronisation (les mêmes que sur le web, voir
 * `arzikina-web-sync/src/lib/api/client.ts`, qui appelle le même `api/auth/login.php`), plus jamais
 * une connexion locale séparée d'une connexion serveur séparée.
 *
 * Orchestre, dans cet ordre :
 * 1. Authentification SERVEUR ([SyncAuthRepository]) — source de vérité de l'identité.
 * 2. Résolution du compte LOCAL correspondant sur CET appareil ([AuthRepository]/`UserDao` +
 *    `UserServerLinkDao`) : réutilise un lien déjà connu, rattache un compte local préexistant du
 *    même e-mail, ou en crée un nouveau — jamais un `UserEntity` en double pour la même personne.
 * 3. Semis des données par défaut UNIQUEMENT pour un compte serveur réellement NOUVEAU (jamais pour
 *    un appareil qui rejoint un compte serveur déjà existant — voir la KDoc de
 *    [com.naniger.arzikina.data.repository.UnifiedAuthRepositoryImpl] pour le raisonnement anti-doublon,
 *    prolongement direct de l'étape A de ce même chantier).
 *
 * NE déclenche PAS lui-même la synchronisation (pull/push) : voir la KDoc de
 * [com.naniger.arzikina.data.repository.UnifiedAuthRepositoryImpl] — reste du ressort de l'appelant
 * (ViewModel), qui orchestre aussi `SessionManager.startSession` et l'affichage progressif
 * "Connexion... / Synchronisation... / Terminée" (cahier des charges, section 13).
 */
interface UnifiedAuthRepository {

    /**
     * Connexion avec les identifiants du compte de synchronisation. Si le serveur rejette ces
     * identifiants MAIS qu'un compte LOCAL préexistant (créé avant ce chantier, jamais synchronisé)
     * correspond exactement (même e-mail, même mot de passe vérifié localement), un compte serveur
     * est créé AUTOMATIQUEMENT en silence pour ce profil avant de continuer — migration transparente
     * décidée explicitement pour les utilisateurs existants, voir la KDoc de l'implémentation.
     */
    suspend fun login(email: String, rawPassword: String): UnifiedAuthResult

    /**
     * Inscription volontaire d'un compte entièrement nouveau (serveur + local), avec semis des
     * données par défaut — voir `presentation/auth/RegisterFragment`, écran complet (pas le
     * parcours simplifié "Gmail + mot de passe" d'origine de cette interface, voir l'historique de
     * [com.naniger.arzikina.data.repository.UnifiedAuthRepositoryImpl]).
     *
     * [username]/[securityQuestion]/[securityAnswer] sont RÉELLEMENT envoyés au serveur (voir
     * `server/api/auth/register.php`) ET stockés localement (hachage PBKDF2, voir
     * [com.naniger.arzikina.util.PasswordHasher]) — contrairement au chemin [login] (connexion, ou
     * migration silencieuse d'un compte local préexistant), qui ne dispose jamais de ces valeurs
     * réelles et continue d'utiliser un repli documenté (voir l'implémentation).
     */
    suspend fun register(
        fullName: String,
        username: String,
        email: String,
        phoneNumber: String?,
        rawPassword: String,
        profilePhotoUri: String?,
        securityQuestion: SecurityQuestion,
        securityAnswer: String,
        /**
         * Mode d'utilisation choisi à l'inscription (voir [UsageMode]) : envoyé au serveur, enregistré sur
         * l'utilisateur local et déterminant les comptes créés par défaut, le tout dans une seule transaction
         * locale. Obligatoire : aucune inscription ne se fait sans ce choix.
         */
        usageMode: UsageMode,
        /**
         * Devise principale choisie à l'inscription (code ISO 4217 du catalogue), ou `null` si aucun choix explicite :
         * [com.naniger.arzikina.domain.currency.CurrencyCatalog.DEFAULT_CODE] est alors utilisée pour les comptes par
         * défaut et AUCUNE préférence n'est écrite. Enregistrée dans les préférences de l'utilisateur (donc synchronisée).
         */
        currencyCode: String? = null
    ): UnifiedAuthResult

    /**
     * Finalise le choix du mode d'utilisation d'un utilisateur déjà authentifié (compte Google sans mode) : enregistre
     * [mode] sur le serveur (le choix déjà existant fait foi, voir `set_usage_mode.php`), le conserve sur
     * l'utilisateur local [localUserId], puis crée les comptes par défaut du mode UNIQUEMENT si le serveur n'en
     * détient aucun — le tout idempotent (nouvel appui, nouvelle tentative : aucun doublon). La session de l'appli
     * n'est pas touchée : c'est à l'appelant de la démarrer ensuite.
     */
    suspend fun completeUsageModeSelection(
        localUserId: Long,
        mode: UsageMode,
        currencyCode: String? = null
    ): UsageModeSelectionResult

    /**
     * Déconnexion complète de l'utilisateur (Profil, écran de verrouillage biométrique) : session
     * serveur fermée (voir [SyncAuthRepository.logout]) PUIS session locale effacée. Aucune donnée
     * de l'utilisateur n'est supprimée.
     *
     * Fermer aussi la session serveur évite qu'un autre profil connecté ensuite sur le même
     * téléphone (notamment par le repli hors ligne, qui n'ouvre pas de session serveur) hérite du
     * token de synchronisation et des notifications push du compte précédent.
     */
    suspend fun logout()

    /**
     * Suppression DÉFINITIVE et IMMÉDIATE du compte (Profil, « Supprimer mon compte ») —
     * [currentRawPassword] RE-DEMANDÉ, par analogie avec [AuthRepository.changePassword] : un
     * accès physique temporaire à un appareil déjà connecté ne doit jamais suffire à détruire le
     * compte.
     *
     * Ordre strict, voir l'implémentation pour le détail complet :
     * 1. Mot de passe vérifié LOCALEMENT d'abord (échec immédiat, aucun appel réseau si incorrect).
     * 2. Si une session de synchronisation est active, suppression SERVEUR d'abord
     *    ([SyncAuthRepository.deleteAccount]) — si elle échoue (hors ligne, erreur serveur), TOUT
     *    s'arrête ici : aucune donnée locale n'est touchée, pour ne jamais détruire la seule copie
     *    locale en laissant une copie serveur intacte sous le même mot de passe.
     * 3. Toutes les données LOCALES de l'utilisateur (comptes, transactions, catégories... et son
     *    propre compte) sont effacées (voir [com.naniger.arzikina.data.local.database.AccountDataWiper]).
     * 4. Session locale effacée ([SessionManager.clearSession]).
     *
     * Si aucune session de synchronisation n'est active (utilisateur 100% local, jamais connecté
     * au serveur), l'étape 2 est simplement sautée : rien à supprimer côté serveur.
     */
    suspend fun deleteAccount(currentRawPassword: String): AccountDeletionResult

    /**
     * Authentification via Google (« Continuer avec Google », pages Connexion ET Inscription —
     * les deux appellent la MÊME méthode, voir [com.naniger.arzikina.presentation.auth.GoogleSignInLauncher]) :
     * voir `server/api/auth/google_login.php`. [idToken] est le jeton d'identité Google obtenu
     * côté présentation (Credential Manager), jamais vérifié ici — la vérification réelle
     * (signature, audience, e-mail vérifié) est faite PAR LE SERVEUR (cahier des charges : "Ne
     * considère jamais qu'un simple e-mail ou un nom transmis par le client suffit à authentifier
     * un utilisateur").
     *
     * Résout/crée le compte LOCAL exactement comme [login]/[register] (même table
     * `UserServerLinkEntity`, voir [com.naniger.arzikina.data.repository.UnifiedAuthRepositoryImpl]) :
     * un compte Google et un compte classique du même utilisateur convergent vers le MÊME
     * `UserEntity` local dès qu'ils partagent le même compte serveur.
     *
     * [UnifiedAuthError.AccountExistsLinkRequired] : le serveur a trouvé un compte existant avec
     * cet e-mail, créé par mot de passe, pas encore lié à ce compte Google — cahier des charges,
     * Cas C : aucune fusion automatique, l'utilisateur doit se connecter classiquement puis lier
     * son compte Google (voir [linkGoogleAccount]).
     */
    suspend fun loginWithGoogle(idToken: String): UnifiedAuthResult

    /**
     * Lie le compte Google (jeton [idToken]) au compte Arzikina actuellement connecté — voir
     * `server/api/auth/link_google_account.php` et le cahier des charges, Cas C. Nécessite une
     * session de synchronisation active (l'utilisateur doit être connecté classiquement au moment
     * de l'appel, voir [AccountLinkError.NoActiveSession][com.naniger.arzikina.domain.model.AccountLinkError.NoActiveSession]) ;
     * ne touche à aucune donnée locale, uniquement à l'association côté serveur.
     */
    suspend fun linkGoogleAccount(idToken: String): AccountLinkResult

    /**
     * Définit ou change le mot de passe du compte connecté (Profil, « Changer le mot de passe »).
     *
     * - Compte SYNCHRONISÉ (session serveur active) : changé d'abord sur le SERVEUR ([SyncAuthRepository.setPassword],
     *   qui vérifie [proof]), puis le hash local est mis à jour avec le même mot de passe — sans quoi la
     *   connexion sur un autre appareil et « Supprimer mon compte » (qui revérifie le mot de passe côté
     *   serveur) échoueraient. Le serveur révoque les autres sessions du compte.
     * - Compte lié au serveur mais SANS session active (expirée, repli hors ligne) : refusé
     *   ([com.naniger.arzikina.domain.model.PasswordChangeError.NoActiveSession]) — un changement purement local
     *   ferait diverger les deux mots de passe.
     * - Compte 100 % local (jamais lié au serveur) : seule la preuve [PasswordProof.CurrentPassword] est possible,
     *   vérifiée localement ; seul le hash local change.
     *
     * Le nouveau mot de passe est validé ici (minimum de [com.naniger.arzikina.util.AuthValidator], maximum de
     * [com.naniger.arzikina.domain.model.PasswordLimits]) avant tout appel.
     */
    suspend fun changePassword(proof: PasswordProof, newRawPassword: String): PasswordChangeResult
}
