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
}
