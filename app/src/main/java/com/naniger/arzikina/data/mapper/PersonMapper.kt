package com.naniger.arzikina.data.mapper

import com.naniger.arzikina.data.local.entity.PersonEntity
import com.naniger.arzikina.domain.model.Person

fun PersonEntity.toDomain(): Person = Person(
    id = id,
    name = name,
    phone = phone,
    createdAt = createdAt
)

/** [userId] : fourni par le repository (voir [com.naniger.arzikina.domain.repository.SessionManager]), jamais par l'appelant. */
fun Person.toEntity(userId: Long): PersonEntity = PersonEntity(
    id = id,
    userId = userId,
    name = name,
    phone = phone,
    createdAt = createdAt
)
