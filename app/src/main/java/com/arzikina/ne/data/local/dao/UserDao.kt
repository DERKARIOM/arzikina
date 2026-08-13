package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.arzikina.ne.data.local.entity.UserEntity
import com.arzikina.ne.domain.model.SecurityQuestion
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    /** Laisse SQLite lever une contrainte d'unicité (username/email) plutôt que de la
     *  revérifier ici : `AuthRepositoryImpl` fait déjà une vérification préalable pour
     *  un message d'erreur ciblé, ceci n'est qu'un filet de sécurité contre une course. */
    @Insert
    suspend fun insert(user: UserEntity): Long

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun findById(userId: Long): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId")
    fun observeById(userId: Long): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun findByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun findByEmail(email: String): UserEntity?

    /** [identifier] : e-mail OU nom d'utilisateur (voir écran Connexion). */
    @Query("SELECT * FROM users WHERE username = :identifier OR email = :identifier")
    suspend fun findByIdentifier(identifier: String): UserEntity?

    /** Pour la validation d'unicité lors d'une modification de profil (l'utilisateur ne doit pas se bloquer lui-même). */
    @Query("SELECT * FROM users WHERE email = :email AND id != :excludingUserId")
    suspend fun findByEmailExcluding(email: String, excludingUserId: Long): UserEntity?

    /** Même raisonnement que [findByEmailExcluding], pour le nom d'utilisateur — utilisé par
     * `BackupRepositoryImpl.importBackup` avant de restaurer un profil, pour vérifier que le nom
     * d'utilisateur du fichier n'est pas déjà pris par un AUTRE compte du même appareil. */
    @Query("SELECT * FROM users WHERE username = :username AND id != :excludingUserId")
    suspend fun findByUsernameExcluding(username: String, excludingUserId: Long): UserEntity?

    @Query(
        """
        UPDATE users
        SET fullName = :fullName, email = :email, phoneNumber = :phoneNumber, profilePhotoUri = :profilePhotoUri
        WHERE id = :userId
        """
    )
    suspend fun updateProfile(
        userId: Long,
        fullName: String,
        email: String,
        phoneNumber: String?,
        profilePhotoUri: String?
    )

    @Query("UPDATE users SET passwordHash = :passwordHash WHERE id = :userId")
    suspend fun updatePasswordHash(userId: Long, passwordHash: String)

    @Query("UPDATE users SET securityQuestion = :securityQuestion, securityAnswerHash = :securityAnswerHash WHERE id = :userId")
    suspend fun updateSecurityQuestion(userId: Long, securityQuestion: SecurityQuestion, securityAnswerHash: String)

    /**
     * Restaure EN PLACE le profil de l'utilisateur [userId] à partir d'un fichier de sauvegarde
     * (voir `data/backup/UserDto`) — jamais un `INSERT`, la ligne cible existe forcément déjà
     * (l'utilisateur est connecté au moment de l'import). Regroupe volontairement TOUS les champs
     * en une seule requête plutôt que d'enchaîner [updateProfile]/[updatePasswordHash]/
     * [updateSecurityQuestion] : ce sont ici des données qui arrivent ENSEMBLE depuis un seul
     * fichier, pas des modifications indépendantes déclenchées par des écrans différents.
     * `username`/`email` : l'appelant doit avoir déjà vérifié leur disponibilité via
     * [findByUsernameExcluding]/[findByEmailExcluding] — cette requête laisse quand même SQLite
     * lever sa contrainte d'unicité en filet de sécurité (même principe que [insert]).
     */
    @Query(
        """
        UPDATE users
        SET fullName = :fullName, username = :username, email = :email, phoneNumber = :phoneNumber,
            passwordHash = :passwordHash, securityQuestion = :securityQuestion, securityAnswerHash = :securityAnswerHash
        WHERE id = :userId
        """
    )
    suspend fun restoreProfileFromBackup(
        userId: Long,
        fullName: String,
        username: String,
        email: String,
        phoneNumber: String?,
        passwordHash: String,
        securityQuestion: SecurityQuestion,
        securityAnswerHash: String
    )
}
