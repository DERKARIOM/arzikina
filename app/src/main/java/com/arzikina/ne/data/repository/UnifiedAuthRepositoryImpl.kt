package com.arzikina.ne.data.repository

import com.arzikina.ne.data.local.dao.UserDao
import com.arzikina.ne.data.local.dao.UserServerLinkDao
import com.arzikina.ne.data.local.database.NewUserDefaultDataSeeder
import com.arzikina.ne.data.local.entity.UserEntity
import com.arzikina.ne.data.local.entity.UserServerLinkEntity
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.SecurityQuestion
import com.arzikina.ne.domain.model.SyncAuthError
import com.arzikina.ne.domain.model.SyncAuthResult
import com.arzikina.ne.domain.model.SyncSession
import com.arzikina.ne.domain.model.UnifiedAuthError
import com.arzikina.ne.domain.model.UnifiedAuthResult
import com.arzikina.ne.domain.repository.SyncAuthRepository
import com.arzikina.ne.domain.repository.UnifiedAuthRepository
import com.arzikina.ne.util.AuthValidator
import com.arzikina.ne.util.PasswordHasher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * Implémentation [UnifiedAuthRepository] — voir sa KDoc pour le contrat général. Dépend
 * directement de [UserDao]/[UserServerLinkDao] (couche data), jamais de [AuthRepository] : ce
 * dernier reste l'authentification 100 % locale historique (toujours utilisée telle quelle par
 * `presentation/profile`, `ForgotPasswordViewModel`...), dupliquer ici sa logique de validation
 * plutôt que la réutiliser évite un couplage entre deux flux dont les erreurs typées
 * ([AuthError]/[UnifiedAuthError]) et les invariants (compte serveur vs. compte 100 % local)
 * diffèrent réellement.
 *
 * MISE À JOUR (audit "création de compte") : [register] couvre désormais le formulaire complet
 * (nom d'utilisateur choisi, téléphone, question de sécurité) — `RegisterViewModel` l'appelle à la
 * place de [AuthRepository.register], qui reste néanmoins la référence pour les écrans qui n'ont
 * pas encore migré vers l'authentification serveur unifiée. Seuls [login]/la migration silencieuse/
 * le repli hors ligne ci-dessous restent dépourvus d'une vraie question de sécurité (voir
 * [resolveOrCreateLocalUser]) : ils n'ont jamais accès à la réponse en clair d'un compte créé avant
 * eux.
 *
 * RATTACHEMENT ANTI-DOUBLON (prolongement direct de l'étape A de ce chantier, voir
 * `SyncEngineImpl.applyCategoryServerState`/`applyAccountServerState`) : [resolveOrCreateLocalUser]
 * ne sème JAMAIS les données par défaut ([NewUserDefaultDataSeeder]) pour un appareil qui se
 * connecte à un compte serveur DÉJÀ EXISTANT (`seedDefaultsIfNewLocalUser = false` dans [login]) —
 * seul un compte serveur RÉELLEMENT NOUVEAU ([register]) déclenche ce semis. C'est exactement le
 * même raisonnement que le bug corrigé à l'étape A : semer des catégories/comptes système sur un
 * appareil qui rejoint un compte déjà synchronisé ailleurs recréerait la même famille de doublons
 * que celle déjà nettoyée à l'étape B.
 */
class UnifiedAuthRepositoryImpl @Inject constructor(
    private val syncAuthRepository: SyncAuthRepository,
    private val userDao: UserDao,
    private val userServerLinkDao: UserServerLinkDao,
    private val newUserDefaultDataSeeder: NewUserDefaultDataSeeder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : UnifiedAuthRepository {

    override suspend fun login(email: String, rawPassword: String): UnifiedAuthResult = withContext(ioDispatcher) {
        val trimmedEmail = email.trim()
        validateFormat(trimmedEmail, rawPassword)?.let { return@withContext UnifiedAuthResult.Failure(it) }

        when (val result = syncAuthRepository.login(identifier = trimmedEmail, rawPassword = rawPassword)) {
            is SyncAuthResult.Success ->
                onServerAuthSuccess(result.data, trimmedEmail, rawPassword, seedDefaultsIfNewLocalUser = false)

            is SyncAuthResult.Failure -> when (result.error) {
                // Identifiants refusés par le serveur : peut être un vrai mot de passe incorrect,
                // OU (voir la KDoc de [UnifiedAuthRepository.login]) un compte LOCAL préexistant
                // jamais encore synchronisé — dans ce second cas seulement, on l'enregistre sur le
                // serveur en silence avant de continuer.
                SyncAuthError.InvalidCredentials -> attemptSilentServerMigration(trimmedEmail, rawPassword)
                SyncAuthError.NetworkUnavailable -> attemptOfflineFallback(trimmedEmail, rawPassword)
                is SyncAuthError.ServerError -> UnifiedAuthResult.Failure(UnifiedAuthError.ServerError(result.error.message))
                is SyncAuthError.Unknown -> UnifiedAuthResult.Failure(UnifiedAuthError.Unknown(result.error.cause))
                // UsernameTaken/EmailTaken : jamais renvoyées par `login()` côté serveur (409
                // n'existe que sur `register.php`) — filet de sécurité si l'implémentation évolue.
                else -> UnifiedAuthResult.Failure(UnifiedAuthError.ServerError(null))
            }
        }
    }

    override suspend fun register(
        fullName: String,
        username: String,
        email: String,
        phoneNumber: String?,
        rawPassword: String,
        profilePhotoUri: String?,
        securityQuestion: SecurityQuestion,
        securityAnswer: String
    ): UnifiedAuthResult = withContext(ioDispatcher) {
        val trimmedEmail = email.trim()
        val trimmedFullName = fullName.trim()
        val trimmedUsername = username.trim()
        val trimmedSecurityAnswer = AuthValidator.normalizeSecurityAnswer(securityAnswer)
        validateFormat(
            email = trimmedEmail,
            rawPassword = rawPassword,
            fullName = trimmedFullName,
            username = trimmedUsername,
            securityAnswer = trimmedSecurityAnswer
        )?.let { return@withContext UnifiedAuthResult.Failure(it) }

        registerOnServerWithUsernameRetry(
            fullName = trimmedFullName,
            preferredUsername = trimmedUsername,
            email = trimmedEmail,
            rawPassword = rawPassword,
            phoneNumber = phoneNumber,
            securityQuestion = securityQuestion,
            securityAnswer = trimmedSecurityAnswer,
            // Nom CHOISI par l'utilisateur (formulaire complet, contrairement à la migration
            // silencieuse ci-dessous) : jamais de retentative silencieuse sous un autre nom, voir
            // [UnifiedAuthError.UsernameAlreadyExists].
            allowUsernameRetry = false
        ).let { result ->
            when (result) {
                is SyncAuthResult.Success ->
                    onServerAuthSuccess(
                        session = result.data,
                        email = trimmedEmail,
                        rawPassword = rawPassword,
                        seedDefaultsIfNewLocalUser = true,
                        // allowUsernameRetry = false ci-dessus : le nom effectivement créé côté
                        // serveur est garanti identique à trimmedUsername.
                        username = trimmedUsername,
                        phoneNumber = phoneNumber,
                        profilePhotoUri = profilePhotoUri,
                        securityQuestion = securityQuestion,
                        securityAnswer = trimmedSecurityAnswer
                    )

                is SyncAuthResult.Failure -> when (result.error) {
                    SyncAuthError.EmailTaken -> UnifiedAuthResult.Failure(UnifiedAuthError.EmailAlreadyExists)
                    SyncAuthError.UsernameTaken -> UnifiedAuthResult.Failure(UnifiedAuthError.UsernameAlreadyExists)
                    SyncAuthError.NetworkUnavailable -> UnifiedAuthResult.Failure(UnifiedAuthError.NetworkUnavailableNoLocalFallback)
                    is SyncAuthError.ServerError -> UnifiedAuthResult.Failure(UnifiedAuthError.ServerError(result.error.message))
                    is SyncAuthError.Unknown -> UnifiedAuthResult.Failure(UnifiedAuthError.Unknown(result.error.cause))
                    else -> UnifiedAuthResult.Failure(UnifiedAuthError.ServerError(null))
                }
            }
        }
    }

    private suspend fun onServerAuthSuccess(
        session: SyncSession,
        email: String,
        rawPassword: String,
        seedDefaultsIfNewLocalUser: Boolean,
        // Uniquement fournis par [register] (voir sa doc) — `null` pour [login]/[attemptSilentServerMigration]/
        // [attemptOfflineFallback], qui laissent [resolveOrCreateLocalUser] appliquer son repli.
        username: String? = null,
        phoneNumber: String? = null,
        profilePhotoUri: String? = null,
        securityQuestion: SecurityQuestion? = null,
        securityAnswer: String? = null
    ): UnifiedAuthResult {
        val localUserId = resolveOrCreateLocalUser(
            serverUserId = session.serverUserId,
            fullName = session.fullName,
            email = email,
            rawPassword = rawPassword,
            seedDefaultsIfNewLocalUser = seedDefaultsIfNewLocalUser,
            username = username,
            phoneNumber = phoneNumber,
            profilePhotoUri = profilePhotoUri,
            securityQuestion = securityQuestion,
            securityAnswer = securityAnswer
        )
        return UnifiedAuthResult.Success(localUserId, usedLocalFallback = false)
    }

    /**
     * Voir la KDoc de [UnifiedAuthRepository.login]. Ne tente la migration QUE si un compte local
     * de cet e-mail existe ET que [rawPassword] est bien SON mot de passe local (sinon : simple
     * mot de passe incorrect, rien à migrer — voir [UnifiedAuthError.InvalidCredentials]).
     *
     * `email_taken` en retour de l'inscription silencieuse signifie qu'un AUTRE mot de passe est
     * attendu côté serveur pour cet e-mail (compte serveur déjà existant, sous un mot de passe
     * différent du mot de passe local) : dans ce cas précis, remonter [InvalidCredentials] reste le
     * message le plus honnête (identifiants refusés), jamais [EmailAlreadyExists] (réservé à
     * [register], une action volontaire de l'utilisateur).
     */
    private suspend fun attemptSilentServerMigration(email: String, rawPassword: String): UnifiedAuthResult {
        val localUser = userDao.findByEmail(email)
        if (localUser == null || !PasswordHasher.verify(rawPassword, localUser.passwordHash)) {
            return UnifiedAuthResult.Failure(UnifiedAuthError.InvalidCredentials)
        }

        return when (
            val result = registerOnServerWithUsernameRetry(
                fullName = localUser.fullName,
                preferredUsername = localUser.username,
                email = email,
                rawPassword = rawPassword,
                // Nom dérivé lors de l'inscription locale d'origine, jamais choisi consciemment
                // pour un compte serveur : une collision peut être renommée en silence sans induire
                // l'utilisateur en erreur (voir la KDoc de [registerOnServerWithUsernameRetry]).
                allowUsernameRetry = true
            )
        ) {
            is SyncAuthResult.Success ->
                onServerAuthSuccess(result.data, email, rawPassword, seedDefaultsIfNewLocalUser = false)

            is SyncAuthResult.Failure -> when (result.error) {
                SyncAuthError.EmailTaken -> UnifiedAuthResult.Failure(UnifiedAuthError.InvalidCredentials)
                SyncAuthError.NetworkUnavailable -> UnifiedAuthResult.Failure(UnifiedAuthError.NetworkUnavailableNoLocalFallback)
                is SyncAuthError.ServerError -> UnifiedAuthResult.Failure(UnifiedAuthError.ServerError(result.error.message))
                is SyncAuthError.Unknown -> UnifiedAuthResult.Failure(UnifiedAuthError.Unknown(result.error.cause))
                else -> UnifiedAuthResult.Failure(UnifiedAuthError.ServerError(null))
            }
        }
    }

    /**
     * Cahier des charges, section 13 : "permettre de continuer avec les données locales
     * disponibles si cela est compatible avec la logique de sécurité" — possible UNIQUEMENT si un
     * compte local de cet e-mail existe déjà ET que le mot de passe local correspond (vérification
     * de sécurité inchangée, jamais contournée faute de réseau). Aucune synchronisation n'a lieu
     * dans ce cas (voir [UnifiedAuthResult.Success.usedLocalFallback]) : les données déjà présentes
     * localement restent utilisables telles quelles.
     */
    private suspend fun attemptOfflineFallback(email: String, rawPassword: String): UnifiedAuthResult {
        val localUser = userDao.findByEmail(email)
        if (localUser == null || !PasswordHasher.verify(rawPassword, localUser.passwordHash)) {
            return UnifiedAuthResult.Failure(UnifiedAuthError.NetworkUnavailableNoLocalFallback)
        }
        return UnifiedAuthResult.Success(localUserId = localUser.id, usedLocalFallback = true)
    }

    /**
     * Voir [UserServerLinkEntity] pour le raisonnement (table séparée, `UserEntity` non modifiable).
     * TROIS cas, dans cet ordre :
     * 1. Ce compte serveur est déjà lié à un compte local sur CET appareil (reconnexion normale).
     * 2. Un compte local de cet e-mail existe mais n'est pas encore lié (migration silencieuse ou
     *    reconnexion après une réinstallation ayant conservé... — en pratique surtout le cas 3 ci-dessous
     *    redevenu 2 après un premier lien) : on le lie SANS semer de données (elles existent déjà).
     * 3. Aucun compte local de cet e-mail sur cet appareil : nouveau compte local créé — [seedDefaultsIfNewLocalUser]
     *    décide si les données par défaut sont semées (uniquement pour un [register] réel, jamais
     *    pour un appareil qui rejoint un compte serveur préexistant, voir la KDoc de tête).
     */
    private suspend fun resolveOrCreateLocalUser(
        serverUserId: String,
        fullName: String,
        email: String,
        rawPassword: String,
        seedDefaultsIfNewLocalUser: Boolean,
        username: String? = null,
        phoneNumber: String? = null,
        profilePhotoUri: String? = null,
        securityQuestion: SecurityQuestion? = null,
        securityAnswer: String? = null
    ): Long {
        userServerLinkDao.getByServerUserId(serverUserId)?.let { return it.localUserId }

        val existingLocal = userDao.findByEmail(email)
        val localUserId = if (existingLocal != null) {
            existingLocal.id
        } else {
            val entity = UserEntity(
                fullName = fullName.ifBlank { email },
                username = username ?: deriveLocalUsernameCandidate(email),
                email = email,
                phoneNumber = phoneNumber,
                passwordHash = PasswordHasher.hash(rawPassword),
                profilePhotoUri = profilePhotoUri,
                // `securityQuestion`/`securityAnswer` : uniquement fournis par [register] (inscription
                // complète, voir sa doc) — `null` pour [login]/la migration silencieuse/le repli hors
                // ligne, qui ne disposent JAMAIS de la vraie réponse en clair (seul le hash PBKDF2 local,
                // déjà irréversible, existait avant cette connexion) : impossible de la migrer
                // fidèlement vers le serveur ou de la re-hacher ici. Repli documenté dans ce cas
                // seulement : la récupération par question de sécurité reste indisponible tant que
                // l'utilisateur n'en définit pas une réelle depuis Paramètres — connu, pas corrigé
                // dans cette étape (voir l'audit "création de compte").
                securityQuestion = securityQuestion ?: SecurityQuestion.entries.first(),
                securityAnswerHash = securityAnswer?.let { PasswordHasher.hash(it) }
                    ?: PasswordHasher.hash(UUID.randomUUID().toString()),
                createdAt = System.currentTimeMillis()
            )
            val id = userDao.insert(entity)
            if (seedDefaultsIfNewLocalUser) newUserDefaultDataSeeder.seed(id)
            id
        }

        userServerLinkDao.upsert(
            UserServerLinkEntity(localUserId = localUserId, serverUserId = serverUserId, linkedAt = System.currentTimeMillis())
        )
        return localUserId
    }

    /**
     * Retente [SyncAuthRepository.register] avec un nom d'utilisateur dérivé DIFFÉRENT si le
     * serveur répond `username_taken` — UNIQUEMENT quand [allowUsernameRetry] vaut `true`
     * (migration silencieuse, voir [attemptSilentServerMigration]) : le nom auto-dérivé (voir
     * [deriveLocalUsernameCandidate]) n'a par construction aucune signification pour l'utilisateur,
     * une collision ne doit donc jamais lui être exposée comme une erreur à corriger lui-même.
     *
     * Quand [allowUsernameRetry] vaut `false` ([register], nom CHOISI dans le formulaire), un
     * `username_taken` remonte tel quel dès la première tentative — voir
     * [UnifiedAuthError.UsernameAlreadyExists] : substituer un autre nom sans le dire à
     * l'utilisateur serait trompeur.
     */
    private suspend fun registerOnServerWithUsernameRetry(
        fullName: String,
        preferredUsername: String,
        email: String,
        rawPassword: String,
        phoneNumber: String? = null,
        securityQuestion: SecurityQuestion? = null,
        securityAnswer: String? = null,
        allowUsernameRetry: Boolean,
        attempt: Int = 0
    ): SyncAuthResult<SyncSession> {
        val username = if (attempt == 0) preferredUsername else "${preferredUsername.take(24)}_${(1000..9999).random()}"
        val result = syncAuthRepository.register(
            fullName = fullName,
            username = username,
            email = email,
            rawPassword = rawPassword,
            phoneNumber = phoneNumber,
            securityQuestion = securityQuestion?.name,
            securityAnswer = securityAnswer
        )
        val isUsernameConflict = result is SyncAuthResult.Failure && result.error == SyncAuthError.UsernameTaken
        return if (allowUsernameRetry && isUsernameConflict && attempt < MAX_USERNAME_RETRY_ATTEMPTS) {
            registerOnServerWithUsernameRetry(
                fullName, preferredUsername, email, rawPassword, phoneNumber, securityQuestion, securityAnswer,
                allowUsernameRetry, attempt + 1
            )
        } else {
            result
        }
    }

    /** Nom d'utilisateur LOCAL valide (voir `AuthValidator`) dérivé de la partie avant `@` de
     *  l'e-mail — le parcours simplifié ne demande plus ce champ à l'utilisateur. */
    private fun deriveLocalUsernameCandidate(email: String): String {
        val sanitized = email.substringBefore('@')
            .filter { it.isLetterOrDigit() || it == '.' || it == '_' }
            .take(AuthValidator.MAX_USERNAME_LENGTH)
        return if (sanitized.length < AuthValidator.MIN_USERNAME_LENGTH) {
            sanitized.padEnd(AuthValidator.MIN_USERNAME_LENGTH, '0')
        } else {
            sanitized
        }
    }

    /** [username]/[securityAnswer] : uniquement fournis (et donc validés) par [register] — `null`
     *  pour [login], qui ne les demande pas. */
    private fun validateFormat(
        email: String,
        rawPassword: String,
        fullName: String? = null,
        username: String? = null,
        securityAnswer: String? = null
    ): UnifiedAuthError? {
        if (email.isBlank() || rawPassword.isBlank() || fullName?.isBlank() == true || username?.isBlank() == true) {
            return UnifiedAuthError.ValidationFailed(UnifiedAuthError.ValidationFailed.ValidationReason.REQUIRED_FIELD_MISSING)
        }
        if (!AuthValidator.isValidEmail(email)) {
            return UnifiedAuthError.ValidationFailed(UnifiedAuthError.ValidationFailed.ValidationReason.INVALID_EMAIL_FORMAT)
        }
        if (!AuthValidator.isPasswordLongEnough(rawPassword)) {
            return UnifiedAuthError.ValidationFailed(UnifiedAuthError.ValidationFailed.ValidationReason.PASSWORD_TOO_SHORT)
        }
        if (username != null && !AuthValidator.isValidUsername(username)) {
            return UnifiedAuthError.ValidationFailed(UnifiedAuthError.ValidationFailed.ValidationReason.INVALID_USERNAME)
        }
        if (securityAnswer != null && !AuthValidator.isSecurityAnswerLongEnough(securityAnswer)) {
            return UnifiedAuthError.ValidationFailed(UnifiedAuthError.ValidationFailed.ValidationReason.SECURITY_ANSWER_TOO_SHORT)
        }
        return null
    }

    private companion object {
        const val MAX_USERNAME_RETRY_ATTEMPTS = 3
    }
}
