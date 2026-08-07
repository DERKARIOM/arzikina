package com.arzikina.ne.data.mapper

import com.arzikina.ne.data.local.entity.UserEntity
import com.arzikina.ne.domain.model.User

/**
 * Conversion [UserEntity] → [User] uniquement (pas de `toEntity()` symétrique,
 * contrairement aux autres mappers du projet) : [UserEntity.passwordHash] n'a
 * pas d'équivalent côté domaine (voir [User]), la construction d'un
 * [UserEntity] reste donc manuelle et localisée dans `AuthRepositoryImpl`,
 * seule classe autorisée à connaître ce champ.
 */
fun UserEntity.toDomain(): User = User(
    id = id,
    fullName = fullName,
    username = username,
    email = email,
    phoneNumber = phoneNumber,
    profilePhotoUri = profilePhotoUri,
    createdAt = createdAt
)
