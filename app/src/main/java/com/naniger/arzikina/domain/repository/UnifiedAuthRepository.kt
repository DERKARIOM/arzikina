package com.naniger.arzikina.domain.repository

import com.naniger.arzikina.domain.model.SecurityQuestion
import com.naniger.arzikina.domain.model.UnifiedAuthResult

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
        securityAnswer: String
    ): UnifiedAuthResult
}
