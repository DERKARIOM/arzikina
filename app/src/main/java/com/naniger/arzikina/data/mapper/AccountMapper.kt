package com.naniger.arzikina.data.mapper

import com.naniger.arzikina.data.local.entity.AccountEntity
import com.naniger.arzikina.data.remote.dto.AccountSyncPayload
import com.naniger.arzikina.domain.model.Account

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
    createdAt = createdAt,
    type = type,
    cardLastFourDigits = cardLastFourDigits,
    cardExpiryMonth = cardExpiryMonth,
    cardExpiryYear = cardExpiryYear,
    isExcludedFromStatistics = isExcludedFromStatistics,
    mobileMoneyPackageName = mobileMoneyPackageName,
    displayOrder = displayOrder,
    savingsTargetAmount = savingsTargetAmount,
    savingsDescription = savingsDescription
)

/** [userId] : fourni par le repository (voir [com.naniger.arzikina.domain.repository.SessionManager]), jamais par l'appelant. */
fun Account.toEntity(userId: Long): AccountEntity = AccountEntity(
    id = id,
    userId = userId,
    name = name,
    icon = icon,
    colorArgb = colorArgb,
    currencyCode = currencyCode,
    initialBalanceMinor = initialBalance,
    createdAt = createdAt,
    type = type,
    cardLastFourDigits = cardLastFourDigits,
    cardExpiryMonth = cardExpiryMonth,
    cardExpiryYear = cardExpiryYear,
    isExcludedFromStatistics = isExcludedFromStatistics,
    mobileMoneyPackageName = mobileMoneyPackageName,
    displayOrder = displayOrder,
    savingsTargetAmount = savingsTargetAmount,
    savingsDescription = savingsDescription
)

/**
 * Payload `accounts` de `push.php` pour [this] — SEUL constructeur de [AccountSyncPayload] (utilisé
 * par `AccountRepositoryImpl`, `SyncEngineImpl` et `LegacySavingsGoalMigrator`) : un champ ajouté au
 * compte n'a ainsi qu'un seul endroit à compléter pour être synchronisé partout.
 * [syncId] doit déjà être généré (voir l'appelant).
 */
fun AccountEntity.toSyncPayload(baseVersion: Int?): AccountSyncPayload = AccountSyncPayload(
    id = requireNotNull(syncId) { "syncId doit être généré avant l'enfilage." },
    baseVersion = baseVersion,
    name = name,
    icon = icon.name,
    colorArgb = colorArgb,
    currencyCode = currencyCode,
    initialBalanceMinor = initialBalanceMinor,
    type = type.name,
    cardLastFourDigits = cardLastFourDigits,
    cardExpiryMonth = cardExpiryMonth,
    cardExpiryYear = cardExpiryYear,
    isExcludedFromStatistics = isExcludedFromStatistics,
    mobileMoneyPackageName = mobileMoneyPackageName,
    displayOrder = displayOrder,
    savingsTargetAmount = savingsTargetAmount,
    savingsDescription = savingsDescription,
    createdAt = createdAt,
    updatedAt = updatedAt
)
