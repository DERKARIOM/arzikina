package com.arzikina.ne.data.mapper

import com.arzikina.ne.data.local.entity.AccountEntity
import com.arzikina.ne.domain.model.Account

/**
 * Conversions entre la représentation Room ([AccountEntity]) et le modèle du
 * domaine ([Account]). Isole toute connaissance du schéma de base de données
 * hors de la couche domaine.
 */
fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    icon = icon,
    colorArgb = colorArgb,
    currencyCode = currencyCode,
    initialBalance = initialBalanceMinor,
    createdAt = createdAt
)

fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    icon = icon,
    colorArgb = colorArgb,
    currencyCode = currencyCode,
    initialBalanceMinor = initialBalance,
    createdAt = createdAt
)
