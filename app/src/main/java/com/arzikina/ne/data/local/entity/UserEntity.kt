package com.arzikina.ne.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arzikina.ne.domain.model.SecurityQuestion

/**
 * Représentation Room d'un utilisateur. Contrairement aux autres entités,
 * celle-ci contient des données sensibles ([passwordHash], [securityAnswerHash]) :
 * elle ne doit JAMAIS être mappée vers le domaine (voir `data/mapper/UserMapper`,
 * qui n'expose volontairement qu'un `toDomain()`, sans `toEntity()` symétrique —
 * la construction d'un [UserEntity] à l'inscription/au changement de mot de
 * passe reste manuelle et localisée dans `AuthRepositoryImpl`, seule classe
 * autorisée à manipuler ces deux champs).
 *
 * `COLLATE NOCASE` sur [username] et [email] : deux comptes "Jean" et "jean"
 * doivent être considérés comme le même nom d'utilisateur, aussi bien pour
 * l'unicité (index ci-dessous) que pour la recherche à la connexion (voir
 * `UserDao`, dont les comparaisons `=` héritent de cette collation).
 */
@Entity(
    tableName = "users",
    indices = [
        Index(value = ["username"], unique = true),
        Index(value = ["email"], unique = true)
    ]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val fullName: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val username: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val email: String,
    val phoneNumber: String?,
    /** Jamais le mot de passe en clair — voir `util/PasswordHasher`. */
    val passwordHash: String,
    val profilePhotoUri: String?,
    /** Question choisie à l'inscription (voir `écran "Mot de passe oublié"`). */
    val securityQuestion: SecurityQuestion,
    /** Jamais la réponse en clair — même hachage que [passwordHash] (voir `util/PasswordHasher`). */
    val securityAnswerHash: String,
    val createdAt: Long
)
