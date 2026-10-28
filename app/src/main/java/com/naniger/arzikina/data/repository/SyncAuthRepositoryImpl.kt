package com.naniger.arzikina.data.repository

import android.content.Context
import android.provider.Settings
import com.naniger.arzikina.data.remote.api.HttpFailureException
import com.naniger.arzikina.data.remote.api.SyncAuthApi
import com.naniger.arzikina.data.remote.dto.GoogleLoginRequestDto
import com.naniger.arzikina.data.remote.dto.LinkGoogleAccountRequestDto
import com.naniger.arzikina.data.remote.dto.LoginRequestDto
import com.naniger.arzikina.data.remote.dto.LoginResponseDto
import com.naniger.arzikina.data.remote.dto.RegisterRequestDto
import com.naniger.arzikina.data.remote.dto.SetPasswordRequestDto
import com.naniger.arzikina.data.remote.dto.SetUsageModeRequestDto
import com.naniger.arzikina.di.ApplicationScope
import com.naniger.arzikina.di.IoDispatcher
import com.naniger.arzikina.domain.model.GoogleSyncSession
import com.naniger.arzikina.domain.model.PasswordChangeError
import com.naniger.arzikina.domain.model.PasswordChangeResult
import com.naniger.arzikina.domain.model.PasswordProof
import com.naniger.arzikina.domain.model.SyncAuthError
import com.naniger.arzikina.domain.model.SyncAuthResult
import com.naniger.arzikina.domain.model.SyncSession
import com.naniger.arzikina.domain.model.UsageMode
import com.naniger.arzikina.domain.model.UsageModeSelection
import com.naniger.arzikina.domain.repository.PushRegistrationRepository
import com.naniger.arzikina.domain.repository.SyncAuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * Implémentation [SyncAuthRepository] (domaine). Le token brut ne transite JAMAIS en dehors de la
 * couche data (voir [SyncAuthStore], qui délègue lui-même à
 * [com.naniger.arzikina.data.security.TokenCipher] pour le chiffrement) : ni loggé, ni exposé via
 * [SyncAuthRepository] au domaine/présentation.
 *
 * Se limite STRICTEMENT à la connexion au serveur (compte + session) — voir la KDoc de
 * [SyncAuthRepository] : ne déclenche PAS lui-même de synchronisation (pull/push) ni de résolution
 * de compte LOCAL. C'est [com.naniger.arzikina.data.repository.UnifiedAuthRepositoryImpl] (couche
 * au-dessus) qui orchestre l'ensemble pour le login unifié — au moment où [login]/[register]
 * s'exécutent ici, le compte local correspondant n'existe pas forcément encore, donc tenter une
 * synchronisation à CET endroit serait prématuré (elle n'aurait pas encore de session locale
 * valide à laquelle rattacher les données reçues, voir `SessionManager`).
 *
 * Pas de rafraîchissement automatique du token à ce stade : un token expiré rend
 * [getActiveSession] silencieusement `null`, l'appelant devra alors demander une nouvelle
 * connexion — `api/auth/refresh.php` n'existe pas encore côté serveur (voir la KDoc de
 * `TOKEN_EXPIRY_SECONDS` dans `secrets.example.php`).
 */
class SyncAuthRepositoryImpl @Inject constructor(
    private val authApi: SyncAuthApi,
    @ApplicationContext private val context: Context,
    private val store: SyncAuthStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val pushRegistrationRepository: PushRegistrationRepository,
    @ApplicationScope private val applicationScope: CoroutineScope
) : SyncAuthRepository {

    override suspend fun login(
        identifier: String,
        rawPassword: String,
        deviceLabel: String?
    ): SyncAuthResult<SyncSession> = withContext(ioDispatcher) {
        runCatchingAuthCall {
            authApi.login(
                LoginRequestDto(
                    identifier = identifier,
                    password = rawPassword,
                    deviceId = androidDeviceId(),
                    deviceLabel = deviceLabel
                )
            )
        }
    }

    override suspend fun register(
        fullName: String,
        username: String,
        email: String,
        rawPassword: String,
        phoneNumber: String?,
        securityQuestion: String?,
        securityAnswer: String?,
        deviceLabel: String?,
        usageMode: UsageMode?
    ): SyncAuthResult<SyncSession> = withContext(ioDispatcher) {
        runCatchingAuthCall {
            authApi.register(
                RegisterRequestDto(
                    fullName = fullName,
                    username = username,
                    email = email,
                    phoneNumber = phoneNumber,
                    password = rawPassword,
                    securityQuestion = securityQuestion,
                    securityAnswer = securityAnswer,
                    deviceId = androidDeviceId(),
                    deviceLabel = deviceLabel,
                    usageMode = usageMode?.name
                )
            )
        }
    }

    /**
     * Factorise [login]/[register] (réponse de MÊME FORME, voir [LoginResponseDto]) : sauvegarde
     * de la session puis mise en correspondance des erreurs HTTP/réseau vers [SyncAuthError] — même
     * mapping pour les deux (401 → identifiants invalides pour [login], simplement improbable mais
     * inoffensif pour [register] ; 409 distingue [SyncAuthError.UsernameTaken]/[EmailTaken],
     * pertinent uniquement pour [register] mais sans risque de survenir pour [login]).
     */
    private suspend fun runCatchingAuthCall(call: suspend () -> LoginResponseDto): SyncAuthResult<SyncSession> =
        try {
            val response = call()
            store.saveSession(
                userId = response.userId,
                rawToken = response.token,
                expiresAt = response.expiresAt,
                fullName = response.fullName
            )
            // Nouvelle session serveur : (ré)enregistre l'appareil pour les notifications push,
            // rattaché à CETTE session (voir `server/api/devices/register.php`). WorkManager : ne
            // retarde pas la connexion et réessaie seul hors ligne.
            pushRegistrationRepository.requestRegistration()
            SyncAuthResult.Success(
                SyncSession(
                    serverUserId = response.userId,
                    expiresAt = response.expiresAt,
                    fullName = response.fullName,
                    usageMode = UsageMode.fromCode(response.usageMode),
                    hasServerAccounts = response.hasAccounts
                )
            )
        } catch (e: HttpFailureException) {
            when (e.code) {
                401 -> SyncAuthResult.Failure(SyncAuthError.InvalidCredentials)
                409 -> SyncAuthResult.Failure(mapConflictError(e.message))
                else -> SyncAuthResult.Failure(SyncAuthError.ServerError(e.message))
            }
        } catch (e: IOException) {
            // Pas de réseau, timeout, hôte injoignable — voir OkHttpClient (délais dans NetworkModule).
            SyncAuthResult.Failure(SyncAuthError.NetworkUnavailable)
        } catch (e: Exception) {
            SyncAuthResult.Failure(SyncAuthError.Unknown(e))
        }

    /** [HttpFailureException.message] porte le corps HTTP brut (voir `SyncAuthApi.execute`) — sur
     *  un 409, ce corps est le JSON `{"error":"username_taken"|"email_taken",...}` de
     *  `register.php` ; recherche de texte volontairement simple plutôt qu'un vrai décodage JSON
     *  (pas de DTO d'erreur dédié à ce stade, un seul appelant). */
    private fun mapConflictError(rawBody: String?): SyncAuthError = when {
        rawBody?.contains("username_taken") == true -> SyncAuthError.UsernameTaken
        rawBody?.contains("email_taken") == true -> SyncAuthError.EmailTaken
        else -> SyncAuthError.ServerError(rawBody)
    }

    /**
     * Déconnexion du serveur, en deux temps :
     * 1. LOCAL, immédiat et garanti (même hors ligne) : session effacée, enregistrement push en
     *    attente annulé. Plus aucun appel authentifié ne peut partir de cet appareil.
     * 2. SERVEUR, au mieux : `auth/logout.php` révoque la session et coupe les notifications de cet
     *    appareil. Lancé dans la portée de l'application (et non celle de l'écran, détruit par la
     *    navigation qui suit), avec un délai maximal. S'il échoue (hors ligne), la session expirera
     *    seule et les notifications éventuellement reçues sont écartées par l'application (voir
     *    `PushRecipientVerifier`).
     */
    override suspend fun logout() {
        val rawToken = store.getValidRawToken()
        store.clear()
        pushRegistrationRepository.onSignedOut()
        if (rawToken != null) {
            applicationScope.launch(ioDispatcher) {
                withTimeoutOrNull(SERVER_LOGOUT_TIMEOUT_MILLIS) { runCatching { authApi.logout(rawToken) } }
            }
        }
    }

    /**
     * Contrairement à [logout] (local immédiat + serveur "au mieux" en arrière-plan), ceci attend
     * RÉELLEMENT la réponse du serveur avant de toucher à quoi que ce soit en local (voir la KDoc
     * de l'interface) : [UnifiedAuthRepositoryImpl.deleteAccount][com.naniger.arzikina.data.repository.UnifiedAuthRepositoryImpl.deleteAccount]
     * ne doit jamais effacer les données locales tant que la suppression serveur n'est pas
     * confirmée.
     */
    override suspend fun deleteAccount(rawPassword: String): SyncAuthResult<Unit> = withContext(ioDispatcher) {
        val rawToken = store.getValidRawToken()
            ?: return@withContext SyncAuthResult.Failure(SyncAuthError.Unknown(null))
        try {
            authApi.deleteAccount(rawToken, rawPassword)
            // Session serveur de CET appareil : effacée seulement maintenant, la suppression étant
            // confirmée (contrairement à [logout], qui l'efface AVANT même d'appeler le serveur).
            store.clear()
            pushRegistrationRepository.onSignedOut()
            SyncAuthResult.Success(Unit)
        } catch (e: HttpFailureException) {
            when (e.code) {
                401 -> SyncAuthResult.Failure(SyncAuthError.InvalidCredentials)
                else -> SyncAuthResult.Failure(SyncAuthError.ServerError(e.message))
            }
        } catch (e: IOException) {
            SyncAuthResult.Failure(SyncAuthError.NetworkUnavailable)
        } catch (e: Exception) {
            SyncAuthResult.Failure(SyncAuthError.Unknown(e))
        }
    }

    /**
     * Voir la KDoc de l'interface. Le jeton de session vient de [SyncAuthStore.getValidRawToken] (garde
     * explicite : pas de session valide, pas d'appel réseau) ; la traduction des erreurs serveur est isolée
     * dans [PasswordChangeErrorMapper] (testée sans Android).
     */
    override suspend fun setPassword(newRawPassword: String, proof: PasswordProof): PasswordChangeResult =
        withContext(ioDispatcher) {
            val rawToken = store.getValidRawToken()
                ?: return@withContext PasswordChangeResult.Failure(PasswordChangeError.NoActiveSession)
            val request = SetPasswordRequestDto(
                newPassword = newRawPassword,
                currentPassword = (proof as? PasswordProof.CurrentPassword)?.raw,
                idToken = (proof as? PasswordProof.GoogleIdToken)?.token
            )
            try {
                authApi.setPassword(rawToken, request)
                PasswordChangeResult.Success
            } catch (e: HttpFailureException) {
                PasswordChangeResult.Failure(
                    PasswordChangeErrorMapper.map(e.code, e.message, usedGoogleProof = proof is PasswordProof.GoogleIdToken)
                )
            } catch (e: IOException) {
                PasswordChangeResult.Failure(PasswordChangeError.NetworkUnavailable)
            } catch (e: Exception) {
                PasswordChangeResult.Failure(PasswordChangeError.Unknown(e))
            }
        }

    override suspend fun setUsageMode(mode: UsageMode): SyncAuthResult<UsageModeSelection> = withContext(ioDispatcher) {
        val rawToken = store.getValidRawToken()
            ?: return@withContext SyncAuthResult.Failure(SyncAuthError.InvalidCredentials)
        try {
            val response = authApi.setUsageMode(rawToken, SetUsageModeRequestDto(mode.name))
            // Le serveur fait foi : un choix déjà enregistré (autre appareil, nouvelle tentative) est renvoyé tel quel.
            val confirmed = UsageMode.fromCode(response.usageMode)
                ?: return@withContext SyncAuthResult.Failure(SyncAuthError.ServerError(null))
            SyncAuthResult.Success(UsageModeSelection(confirmed, response.hasAccounts))
        } catch (e: HttpFailureException) {
            SyncAuthResult.Failure(if (e.code == 401) SyncAuthError.InvalidCredentials else SyncAuthError.ServerError(e.message))
        } catch (e: IOException) {
            SyncAuthResult.Failure(SyncAuthError.NetworkUnavailable)
        } catch (e: Exception) {
            SyncAuthResult.Failure(SyncAuthError.Unknown(e))
        }
    }

    override suspend fun getActiveSession(): SyncSession? = store.getActiveSession()

    override fun observeActiveSession(): Flow<SyncSession?> = store.observeActiveSession()

    /**
     * Contrairement à [login]/[register] (voir [runCatchingAuthCall]), la réponse serveur porte
     * ici deux informations supplémentaires ([GoogleLoginResponseDto.email]/[isNewAccount]) — pas
     * de forme partagée possible avec [runCatchingAuthCall], d'où cette implémentation dédiée.
     */
    override suspend fun loginWithGoogle(idToken: String, deviceLabel: String?): SyncAuthResult<GoogleSyncSession> =
        withContext(ioDispatcher) {
            try {
                val response = authApi.googleLogin(
                    GoogleLoginRequestDto(idToken = idToken, deviceId = androidDeviceId(), deviceLabel = deviceLabel)
                )
                store.saveSession(
                    userId = response.userId,
                    rawToken = response.token,
                    expiresAt = response.expiresAt,
                    fullName = response.fullName
                )
                pushRegistrationRepository.requestRegistration()
                SyncAuthResult.Success(
                    GoogleSyncSession(
                        session = SyncSession(
                            serverUserId = response.userId,
                            expiresAt = response.expiresAt,
                            fullName = response.fullName,
                            usageMode = UsageMode.fromCode(response.usageMode),
                            hasServerAccounts = response.hasAccounts
                        ),
                        email = response.email,
                        isNewAccount = response.isNewAccount
                    )
                )
            } catch (e: HttpFailureException) {
                SyncAuthResult.Failure(mapGoogleAuthError(e))
            } catch (e: IOException) {
                SyncAuthResult.Failure(SyncAuthError.NetworkUnavailable)
            } catch (e: Exception) {
                SyncAuthResult.Failure(SyncAuthError.Unknown(e))
            }
        }

    /**
     * Cahier des charges, Cas C : nécessite une session de synchronisation ACTIVE (l'utilisateur
     * doit déjà être connecté classiquement) — [com.naniger.arzikina.domain.repository.UnifiedAuthRepository.linkGoogleAccount]
     * vérifie déjà ce prérequis avant d'appeler cette méthode ; [store.getValidRawToken] rejoue la
     * même garde ici, défense en profondeur plutôt que confiance aveugle dans l'appelant.
     */
    override suspend fun linkGoogleAccount(idToken: String): SyncAuthResult<Unit> = withContext(ioDispatcher) {
        val rawToken = store.getValidRawToken()
            ?: return@withContext SyncAuthResult.Failure(SyncAuthError.Unknown(null))
        try {
            authApi.linkGoogleAccount(rawToken, idToken)
            SyncAuthResult.Success(Unit)
        } catch (e: HttpFailureException) {
            SyncAuthResult.Failure(mapGoogleAuthError(e))
        } catch (e: IOException) {
            SyncAuthResult.Failure(SyncAuthError.NetworkUnavailable)
        } catch (e: Exception) {
            SyncAuthResult.Failure(SyncAuthError.Unknown(e))
        }
    }

    /** [HttpFailureException.message] porte le corps HTTP brut (voir [mapConflictError]) —
     *  `google_login.php`/`link_google_account.php` renvoient un `error` JSON stable (voir
     *  `google_token_verifier.php` et leur doc de tête), recherché ici par simple contenu textuel,
     *  même idiome que [mapConflictError]. */
    private fun mapGoogleAuthError(e: HttpFailureException): SyncAuthError = when {
        e.message?.contains("account_exists_link_required") == true -> SyncAuthError.AccountExistsLinkRequired
        e.message?.contains("google_account_already_linked") == true -> SyncAuthError.GoogleAccountAlreadyLinked
        e.message?.contains("invalid_google_token") == true -> SyncAuthError.InvalidGoogleToken
        e.message?.contains("google_email_not_verified") == true -> SyncAuthError.InvalidGoogleToken
        e.message?.contains("missing_id_token") == true -> SyncAuthError.InvalidGoogleToken
        e.code == 401 -> SyncAuthError.InvalidGoogleToken
        else -> SyncAuthError.ServerError(e.message)
    }

    /** Identifiant d'appareil best-effort pour `auth_tokens.device_id` (voir `login.php`) — utile
     *  à une future gestion des appareils connectés, jamais utilisé comme secret. */
    private fun androidDeviceId(): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
}

private const val SERVER_LOGOUT_TIMEOUT_MILLIS = 15_000L
