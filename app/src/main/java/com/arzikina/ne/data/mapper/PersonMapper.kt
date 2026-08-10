package com.arzikina.ne.data.mapper

import com.arzikina.ne.data.local.entity.PersonEntity
import com.arzikina.ne.domain.model.Person

fun PersonEntity.toDomain(): Person = Person(
    id = id,
    name = name,
    phone = phone,
    createdAt = createdAt
)

/** [userId] : fourni par le repository (voir [com.arzikina.ne.domain.repository.SessionManager]), jamais par l'appelant. */
fun Person.toEntity(userId: Long): PersonEntity = PersonEntity(
    id = id,
    userId = userId,
    name = name,
    phone = phone,
    createdAt = createdAt
)
