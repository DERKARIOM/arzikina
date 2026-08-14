package com.arzikina.ne.domain.repository

import com.arzikina.ne.domain.model.AuthResult
import com.arzikina.ne.domain.model.SecurityQuestion
import com.arzikina.ne.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Authentification et gestion du compte utilisateur.
 *
 * Contrat volontairement indépendant de toute technologie (Room, DataStore,
 * futur backend REST...) : seule [com.arzikina.ne.data.repository.AuthRepositoryImpl]
 * sait qu'il s'agit aujourd'hui d'une authentification 100% locale (Room +
 * hachage PBKDF2, voir [com.arzikina.ne.util.PasswordHasher]). Remplacer
 * cette implémentation par une authentification en ligne (Firebase, backend
 * REST...) ne changera pas cette interface — donc pas le reste de
 * l'application, qui ne dépend que d'elle.
 *
 * Deux points d'extension prévus dès la conception (voir leurs KDoc respectives) :
 * [SocialAuthRepository] pour une future connexion Google/Apple/Facebook (NON implémenté
 * aujourd'hui), et [BiometricAuthenticator] pour un verrou biométrique optionnel en complément
 * d'une session déjà active (implémenté, voir [com.arzikina.ne.data.security.BiometricAuthenticatorImpl]).
 *
 * Les méthodes prennent des mots de passe en clair (`rawPassword`) : c'est
 * la SEULE couche autorisée à les manipuler ainsi, le temps de les hacher
 * (ou de vérifier un hachage) avant qu'ils ne quittent la mémoire. Ni le
 * domaine ni la présentation ne stockent jamais un mot de passe en clair
 * au-delà du champ de saisie.
 */
interface AuthRepository {

    /**
     * Crée un nouveau compte. Revalide les règles de format (voir
     * [com.arzikina.ne.util.AuthValidator]) par sécurité même si la
     * présentation les a déjà vérifiées, et vérifie l'unicité du nom
     * d'utilisateur et de l'e-mail (voir [AuthError][com.arzikina.ne.domain.model.AuthError.UsernameAlreadyExists]
     * / [EmailAlreadyExists][com.arzikina.ne.domain.model.AuthError.EmailAlreadyExists]).
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
    ): AuthResult<User>

    /** [identifier] : e-mail OU nom d'utilisateur, au choix de l'utilisateur. */
    suspend fun login(identifier: String, rawPassword: String): AuthResult<User>

    /** Vérification en direct pendant la saisie (écran Inscription), avant soumission. */
    suspend fun isUsernameAvailable(username: String): Boolean

    /** Vérification en direct pendant la saisie (écran Inscription), avant soumission. */
    suspend fun isEmailAvailable(email: String): Boolean

    suspend fun getUser(userId: Long): User?

    /** Pour l'écran Profil : se met à jour automatiquement si l'utilisateur modifie ses informations. */
    fun observeUser(userId: Long): Flow<User?>

    suspend fun updateProfile(
        userId: Long,
        fullName: String,
        email: String,
        phoneNumber: String?,
        profilePhotoUri: String?
    ): AuthResult<User>

    /** Exige [currentRawPassword] : un changement de mot de passe volontaire reste soumis à vérification. */
    suspend fun changePassword(
        userId: Long,
        currentRawPassword: String,
        newRawPassword: String
    ): AuthResult<Unit>

    /**
     * Question de sécurité de ce compte, pour l'afficher à l'étape 2 de
     * l'écran "Mot de passe oublié" (voir [resetPasswordWithSecurityAnswer]).
     * Retourne `null` si [identifier] ne correspond à aucun compte — ce qui
     * révèle l'inexistence du compte, compromis assumé : sans backend pour
     * envoyer un e-mail "si ce compte existe...", il n'y a pas d'étape
     * suivante à proposer dans ce cas de toute façon.
     */
    suspend fun getSecurityQuestion(identifier: String): SecurityQuestion?

    /**
     * Réinitialisation locale (écran "Mot de passe oublié") : remplace
     * [AuthError.CurrentPasswordIncorrect][com.arzikina.ne.domain.model.AuthError.CurrentPasswordIncorrect]
     * (l'ancien mot de passe est par définition oublié) par une vérification
     * de [securityAnswer] contre la réponse enregistrée à l'inscription (voir
     * [SecurityQuestion]). Vérification et réinitialisation sont volontairement
     * un seul appel atomique plutôt que deux (un "vérifier" suivi d'un
     * "réinitialiser" séparé) : cela éviterait de devoir faire transiter un
     * état "identité vérifiée" entre deux écrans sans infrastructure de jeton
     * côté serveur pour le sécuriser.
     */
    suspend fun resetPasswordWithSecurityAnswer(
        identifier: String,
        securityAnswer: String,
        newRawPassword: String
    ): AuthResult<Unit>

    /**
     * Modifie la question de sécurité (écran Profil). Exige [currentRawPassword],
     * comme [changePassword] et pour la même raison : sans cette vérification,
     * un accès physique temporaire à un appareil déjà connecté suffirait à
     * détourner le mécanisme de récupération du compte, puis à réinitialiser
     * le mot de passe plus tard sans jamais le connaître.
     */
    suspend fun updateSecurityQuestion(
        userId: Long,
        currentRawPassword: String,
        securityQuestion: SecurityQuestion,
        securityAnswer: String
    ): AuthResult<Unit>
}
