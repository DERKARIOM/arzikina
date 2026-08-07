package com.arzikina.ne.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.arzikina.ne.data.local.dao.UserDao
import com.arzikina.ne.data.local.database.NewUserDefaultDataSeeder
import com.arzikina.ne.data.local.entity.UserEntity
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.AuthError
import com.arzikina.ne.domain.model.AuthResult
import com.arzikina.ne.domain.model.SecurityQuestion
import com.arzikina.ne.domain.model.User
import com.arzikina.ne.domain.repository.AuthRepository
import com.arzikina.ne.util.AuthValidator
import com.arzikina.ne.util.PasswordHasher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implémentation 100% locale de [AuthRepository] : Room pour le stockage,
 * [PasswordHasher] pour ne jamais persister de mot de passe en clair.
 *
 * Chaque méthode revalide le FORMAT via [AuthValidator] même si la
 * présentation est censée l'avoir déjà fait (défense en profondeur — cette
 * classe ne doit jamais faire confiance aveuglément à son appelant), puis
 * s'appuie sur les index UNIQUE de `users` (voir [UserEntity]) comme garde-fou
 * ultime contre une course entre deux vérifications et l'insertion réelle.
 */
class AuthRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val newUserDefaultDataSeeder: NewUserDefaultDataSeeder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AuthRepository {

    override suspend fun register(
        fullName: String,
        username: String,
        email: String,
        phoneNumber: String?,
        rawPassword: String,
        profilePhotoUri: String?,
        securityQuestion: SecurityQuestion,
        securityAnswer: String
    ): AuthResult<User> = withContext(ioDispatcher) {
        validateRegistrationFormat(fullName, username, email, rawPassword, securityAnswer)
            ?.let { return@withContext it }

        if (userDao.findByUsername(username.trim()) != null) {
            return@withContext AuthResult.Failure(AuthError.UsernameAlreadyExists)
        }
        if (userDao.findByEmail(email.trim()) != null) {
            return@withContext AuthResult.Failure(AuthError.EmailAlreadyExists)
        }

        val entity = UserEntity(
            fullName = fullName.trim(),
            username = username.trim(),
            email = email.trim(),
            phoneNumber = phoneNumber?.trim()?.ifBlank { null },
            passwordHash = PasswordHasher.hash(rawPassword),
            profilePhotoUri = profilePhotoUri,
            securityQuestion = securityQuestion,
            securityAnswerHash = PasswordHasher.hash(AuthValidator.normalizeSecurityAnswer(securityAnswer)),
            createdAt = System.currentTimeMillis()
        )
        try {
            val id = userDao.insert(entity)
            // Comptes/catégories par défaut (voir NewUserDefaultDataSeeder) : ce
            // peuplement se faisait auparavant à la création de la base, avant
            // l'authentification — il n'a de sens que maintenant qu'un userId existe.
            newUserDefaultDataSeeder.seed(id)
            AuthResult.Success(entity.copy(id = id).toDomain())
        } catch (e: SQLiteConstraintException) {
            // Course rarissime entre la vérification ci-dessus et l'insertion :
            // l'index UNIQUE a tranché à notre place, on ne sait juste plus lequel
            // des deux champs est en cause sans une nouvelle lecture.
            val error = if (userDao.findByUsername(username.trim()) != null) {
                AuthError.UsernameAlreadyExists
            } else {
                AuthError.EmailAlreadyExists
            }
            AuthResult.Failure(error)
        }
    }

    override suspend fun login(identifier: String, rawPassword: String): AuthResult<User> =
        withContext(ioDispatcher) {
            val entity = userDao.findByIdentifier(identifier.trim())
                ?: return@withContext AuthResult.Failure(AuthError.InvalidCredentials)
            if (!PasswordHasher.verify(rawPassword, entity.passwordHash)) {
                return@withContext AuthResult.Failure(AuthError.InvalidCredentials)
            }
            AuthResult.Success(entity.toDomain())
        }

    override suspend fun isUsernameAvailable(username: String): Boolean =
        withContext(ioDispatcher) { userDao.findByUsername(username.trim()) == null }

    override suspend fun isEmailAvailable(email: String): Boolean =
        withContext(ioDispatcher) { userDao.findByEmail(email.trim()) == null }

    override suspend fun getUser(userId: Long): User? =
        withContext(ioDispatcher) { userDao.findById(userId)?.toDomain() }

    override fun observeUser(userId: Long): Flow<User?> =
        userDao.observeById(userId).map { it?.toDomain() }

    override suspend fun updateProfile(
        userId: Long,
        fullName: String,
        email: String,
        phoneNumber: String?,
        profilePhotoUri: String?
    ): AuthResult<User> = withContext(ioDispatcher) {
        if (fullName.isBlank()) {
            return@withContext AuthResult.Failure(
                AuthError.ValidationFailed(AuthError.ValidationReason.REQUIRED_FIELD_MISSING)
            )
        }
        if (!AuthValidator.isValidEmail(email)) {
            return@withContext AuthResult.Failure(
                AuthError.ValidationFailed(AuthError.ValidationReason.INVALID_EMAIL_FORMAT)
            )
        }
        if (userDao.findByEmailExcluding(email.trim(), userId) != null) {
            return@withContext AuthResult.Failure(AuthError.EmailAlreadyExists)
        }

        userDao.updateProfile(
            userId = userId,
            fullName = fullName.trim(),
            email = email.trim(),
            phoneNumber = phoneNumber?.trim()?.ifBlank { null },
            profilePhotoUri = profilePhotoUri
        )
        val updated = userDao.findById(userId)
            ?: return@withContext AuthResult.Failure(AuthError.UserNotFound)
        AuthResult.Success(updated.toDomain())
    }

    override suspend fun changePassword(
        userId: Long,
        currentRawPassword: String,
        newRawPassword: String
    ): AuthResult<Unit> = withContext(ioDispatcher) {
        val entity = userDao.findById(userId)
            ?: return@withContext AuthResult.Failure(AuthError.UserNotFound)
        if (!PasswordHasher.verify(currentRawPassword, entity.passwordHash)) {
            return@withContext AuthResult.Failure(AuthError.CurrentPasswordIncorrect)
        }
        if (!AuthValidator.isPasswordLongEnough(newRawPassword)) {
            return@withContext AuthResult.Failure(
                AuthError.ValidationFailed(AuthError.ValidationReason.PASSWORD_TOO_SHORT)
            )
        }
        userDao.updatePasswordHash(userId, PasswordHasher.hash(newRawPassword))
        AuthResult.Success(Unit)
    }

    override suspend fun getSecurityQuestion(identifier: String): SecurityQuestion? =
        withContext(ioDispatcher) { userDao.findByIdentifier(identifier.trim())?.securityQuestion }

    override suspend fun resetPasswordWithSecurityAnswer(
        identifier: String,
        securityAnswer: String,
        newRawPassword: String
    ): AuthResult<Unit> = withContext(ioDispatcher) {
        val entity = userDao.findByIdentifier(identifier.trim())
            ?: return@withContext AuthResult.Failure(AuthError.UserNotFound)
        val normalizedAnswer = AuthValidator.normalizeSecurityAnswer(securityAnswer)
        if (!PasswordHasher.verify(normalizedAnswer, entity.securityAnswerHash)) {
            return@withContext AuthResult.Failure(AuthError.SecurityAnswerIncorrect)
        }
        if (!AuthValidator.isPasswordLongEnough(newRawPassword)) {
            return@withContext AuthResult.Failure(
                AuthError.ValidationFailed(AuthError.ValidationReason.PASSWORD_TOO_SHORT)
            )
        }
        userDao.updatePasswordHash(entity.id, PasswordHasher.hash(newRawPassword))
        AuthResult.Success(Unit)
    }

    override suspend fun updateSecurityQuestion(
        userId: Long,
        currentRawPassword: String,
        securityQuestion: SecurityQuestion,
        securityAnswer: String
    ): AuthResult<Unit> = withContext(ioDispatcher) {
        val entity = userDao.findById(userId)
            ?: return@withContext AuthResult.Failure(AuthError.UserNotFound)
        if (!PasswordHasher.verify(currentRawPassword, entity.passwordHash)) {
            return@withContext AuthResult.Failure(AuthError.CurrentPasswordIncorrect)
        }
        if (!AuthValidator.isSecurityAnswerLongEnough(securityAnswer)) {
            return@withContext AuthResult.Failure(
                AuthError.ValidationFailed(AuthError.ValidationReason.SECURITY_ANSWER_TOO_SHORT)
            )
        }
        val normalizedAnswer = AuthValidator.normalizeSecurityAnswer(securityAnswer)
        userDao.updateSecurityQuestion(userId, securityQuestion, PasswordHasher.hash(normalizedAnswer))
        AuthResult.Success(Unit)
    }

    private fun validateRegistrationFormat(
        fullName: String,
        username: String,
        email: String,
        rawPassword: String,
        securityAnswer: String
    ): AuthResult.Failure? {
        if (fullName.isBlank() || username.isBlank() || email.isBlank() ||
            rawPassword.isBlank() || securityAnswer.isBlank()
        ) {
            return AuthResult.Failure(AuthError.ValidationFailed(AuthError.ValidationReason.REQUIRED_FIELD_MISSING))
        }
        if (!AuthValidator.isValidUsername(username)) {
            return AuthResult.Failure(AuthError.ValidationFailed(AuthError.ValidationReason.INVALID_USERNAME_FORMAT))
        }
        if (!AuthValidator.isValidEmail(email)) {
            return AuthResult.Failure(AuthError.ValidationFailed(AuthError.ValidationReason.INVALID_EMAIL_FORMAT))
        }
        if (!AuthValidator.isPasswordLongEnough(rawPassword)) {
            return AuthResult.Failure(AuthError.ValidationFailed(AuthError.ValidationReason.PASSWORD_TOO_SHORT))
        }
        if (!AuthValidator.isSecurityAnswerLongEnough(securityAnswer)) {
            return AuthResult.Failure(
                AuthError.ValidationFailed(AuthError.ValidationReason.SECURITY_ANSWER_TOO_SHORT)
            )
        }
        return null
    }
}
